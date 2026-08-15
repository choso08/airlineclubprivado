

package com.patson

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.{Actor, ActorInitializationException, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import com.patson.data._
import com.patson.model.{Airport, GameConfig, Seasons}
import com.patson.stream.{CycleCompleted, CycleStart, DirectDemandInfo, SimulationEventStream}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirplaneOwnershipInfo, AirportCache}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

object MainSimulation extends App {
  // Real seconds between cycles. One cycle is one in-game week, so the stock
  // 30 minutes means a game year takes about 26 hours of real time - fine for
  // a public server people check in on daily, very slow for a few friends
  // playing one evening. Override with simulation.cycleDurationSeconds, or
  // the AIRLINE_CYCLE_SECONDS environment variable.
  //
  // Keep this comfortably above how long a cycle takes to compute (the
  // "cycle N spent X secs" line in the log). If cycles take longer than the
  // interval they queue up behind each other and the game drifts.
  val CYCLE_DURATION : Int = {
    val config = com.typesafe.config.ConfigFactory.load()
    if (config.hasPath("simulation.cycleDurationSeconds")) config.getInt("simulation.cycleDurationSeconds") else 30 * 60
  }
  println(s"!!!!!!!!!!!!!!!CYCLE DURATION IS $CYCLE_DURATION seconds")

  // A file whose appearance runs a cycle at once, without waiting out the rest
  // of the interval. The web site cannot simply ask for one - it is a separate
  // process, and reaching into this one would mean opening a door that stays
  // open. A file both halves agree on is the smallest thing that works, and
  // the only thing that can use it is something already running on this
  // machine as this user.
  val FORCE_CYCLE_FILE : String = {
    val config = com.typesafe.config.ConfigFactory.load()
    if (config.hasPath("simulation.forceCycleFile")) config.getString("simulation.forceCycleFile")
    else "/tmp/airline-force-cycle"
  }

  var currentWeek: Int = 0

  //when the last week finished, for working out how long a week is taking
  private var lastWeekEndedAt : Long = 0

//  implicit val actorSystem = ActorSystem("rabbit-akka-stream")

//  import actorSystem.dispatcher

//  implicit val materializer = FlowMaterializer()
  
  mainFlow
  
  // How long the game may stand still before this process decides something is
  // wrong and plays a week itself: two whole weeks that never came, plus a
  // minute of margin so one slow cycle is never mistaken for a stopped game.
  //
  // A def rather than a val, and deliberately. This object extends App, so its
  // fields are initialised in source order at startup - and mainFlow() is
  // called further up this file than a val here would be defined. A val would
  // therefore still be 0 when the supervisor first read it, every wait would
  // look like a stall, and the game would quietly play a week a minute.
  def stallMillis : Long = (cycleSecondsNow().toLong * 2 + 60) * 1000

  /**
    * How long a week should take right now.
    *
    * The night is slower if it has been set to be. A server runs all night for
    * people who are asleep, and at three minutes a week that is a hundred and
    * sixty weeks between going to bed and getting up - three years of a world
    * nobody watched.
    *
    * Read fresh every time rather than once at startup, which is the whole
    * point: the pace has to change at ten in the morning without anybody
    * restarting anything.
    */
  def cycleSecondsNow() : Int = {
    val night = GameConfig.nightCycleSeconds
    if (night <= 0) {
      CYCLE_DURATION
    } else {
      val hour = java.time.LocalTime.now().getHour
      val from = GameConfig.nightFromHour
      val to = GameConfig.nightToHour
      //a window that crosses midnight is still one window
      val quiet = if (from <= to) hour >= from && hour < to else hour >= from || hour < to
      if (quiet) night else CYCLE_DURATION
    }
  }

  def mainFlow() = {
    val supervisor = actorSystem.actorOf(Props(new SimulationSupervisor))

    // The tick is a scheduled job that sends a message, rather than a schedule
    // addressed to the actor. The difference matters more than it looks: the
    // addressed form cancels itself for good the moment the receiving actor is
    // gone, so one week that killed the actor - a database hiccup while it was
    // starting is enough - stopped every week after it, for ever, with the
    // process still running and nothing in the log to say so. The supervisor
    // outlives its worker, so the tick always has somewhere to land.
    //
    // And it schedules itself one week at a time rather than at a fixed rate,
    // because the length of a week is not fixed: the night can be set to run
    // slower, and that has to take effect at the hour it is set for, without
    // anybody restarting anything.
    def scheduleNextWeek() : Unit = {
      val seconds = cycleSecondsNow()
      actorSystem.scheduler.scheduleOnce(Duration(seconds, TimeUnit.SECONDS)) {
        try {
          supervisor ! Start
        } finally {
          //whatever happened, there must always be a next week
          scheduleNextWeek()
        }
      }
    }

    supervisor ! Start //one straight away, as it always did
    scheduleNextWeek()

    // Checked often enough to feel immediate and cheaply enough not to matter:
    // one stat() every two seconds. The file is deleted before the cycle is
    // asked for, so a cycle that runs long cannot queue up a second one behind
    // itself - the request is consumed whether or not anybody is watching.
    val forceCycleFile = new java.io.File(FORCE_CYCLE_FILE)
    println(s"Force a cycle by creating $FORCE_CYCLE_FILE")
    actorSystem.scheduler.schedule(Duration(5, TimeUnit.SECONDS), Duration(2, TimeUnit.SECONDS)) {
      try {
        if (forceCycleFile.exists()) {
          forceCycleFile.delete()
          println("Cycle forced by request")
          supervisor ! ForceStart
        }
      } catch {
        case e : Throwable => println("Could not check for a forced cycle: " + e.getMessage)
      }
    }

    Await.result(actorSystem.whenTerminated, Duration.Inf)
  }


  def invalidateCaches() = {
    AirlineCache.invalidateAll()
    AirportCache.invalidateAll()
    AirplaneOwnershipCache.invalidateAll()
  }

  def startCycle(cycle : Int) = {
      val cycleStartTime = System.currentTimeMillis()
      println("cycle " + cycle + " starting!")
      if (cycle == 0) { //initialize it
        OilSimulation.simulate(0)
        LoanInterestRateSimulation.simulate(0)
      }

      SimulationEventStream.publish(CycleStart(cycle, cycleStartTime))
      invalidateCaches()

      println("Loading airports")
      val airports: List[Airport] = CycleProfiler.phase("load airports") { AirportSource.loadAllAirports(true) }
      println("Loaded " + airports.size + " airports")

      CycleProfiler.phase("users") { UserSimulation.simulate(cycle) }
      Seasons.setCycle(cycle)

      println("World events")
      CycleProfiler.phase("world events") { WorldEventSimulation.simulate(cycle, airports) }

      println("Incidents")
      CycleProfiler.phase("incidents") { IncidentSimulation.simulate(cycle) }

      println("Event simulation")
      CycleProfiler.phase("events") { EventSimulation.simulate(cycle, airports) }

      val (flightLinkResult, loungeResult, linkRidershipDetails) =
        CycleProfiler.phase("links + passengers") { LinkSimulation.linkSimulation(cycle, airports) }
      println("Airport simulation")
      val (airportChampionInfo, directDemand) =
        CycleProfiler.phase("airports") { AirportSimulation.airportSimulation(cycle, airports, flightLinkResult, linkRidershipDetails) }
      SimulationEventStream.publish(DirectDemandInfo(cycle, directDemand.map{
        case (airport, demand) => (airport.id, demand)
      }))

      println("Airport assets simulation")
      CycleProfiler.phase("airport assets") { AirportAssetSimulation.simulate(cycle, linkRidershipDetails) }

      println("Airplane simulation")
      val airplanes = CycleProfiler.phase("airplanes") { AirplaneSimulation.airplaneSimulation(cycle) }
      println("Airline simulation")
      println("Cargo")
      val cargoEarnings = CycleProfiler.phase("cargo") { CargoSimulation.simulate(cycle, flightLinkResult) }
      CycleProfiler.phase("airlines") { AirlineSimulation.airlineSimulation(cycle, flightLinkResult, loungeResult, airplanes, cargoEarnings) }
      println("Achievements")
      CycleProfiler.phase("achievements") { AchievementSimulation.simulate(cycle, flightLinkResult) }

      println("Country simulation")
      val countryChampionInfo = CycleProfiler.phase("countries") { CountrySimulation.simulate(cycle) }

      println("Alliance simulation")
      CycleProfiler.phase("alliances") { AllianceSimulation.simulate(cycle, flightLinkResult, loungeResult, airportChampionInfo, countryChampionInfo) }
      println("Airplane model simulation")
      CycleProfiler.phase("airplane models") { AirplaneModelSimulation.simulate(cycle) }

      //purge log
      println("Purging logs")
      CycleProfiler.phase("purge logs") { LogSource.deleteLogsBeforeCycle(cycle - com.patson.model.Log.RETENTION_CYCLE) }

      //purge history
      println("Purging link history")
      CycleProfiler.phase("purge history") { ChangeHistorySource.deleteLinkChangeByCriteria(List(("cycle", "<", cycle - 500))) }

      //purge airline modifier
      println("Purging airline modifier")
      AirlineSource.deleteAirlineModifierByExpiry(cycle)

      DetectAirlineViolations.detect()

      val cycleEnd = System.currentTimeMillis()
      
      println("cycle " + cycle + " spent " + (cycleEnd - cycleStartTime) / 1000 + " secs")
      CycleProfiler.report(cycle, cycleEnd - cycleStartTime)
      cycleEnd
  }

  /**
    * Things to be done after cycle ticked. These should be relatively short operations (data reconciliation etc)
    * @param currentCycle
    */
  def postCycle(currentCycle : Int) = {
    println("Oil simulation")
    OilSimulation.simulate(currentCycle) //simulate at the beginning of a new cycle
    println("Loan simulation")
    LoanInterestRateSimulation.simulate(currentCycle) //simulate at the beginning of a new cycle
    //refresh delegates
    println("Delegate simulation")
    DelegateSimulation.simulate(currentCycle)

    println("Post cycle link simulation")
    LinkSimulation.simulatePostCycle(currentCycle)

    println(s"Post cycle done $currentCycle")
  }


  /**
    * Owns the actor that plays the weeks, and outlives it.
    *
    * Everything that can go wrong goes wrong inside the worker: a query that
    * fails, a connection pool that is empty for a second, a bug in one of the
    * simulations. None of that may be allowed to stop the world for good, and
    * until now any of it could - the process stayed up, the web site stayed
    * up, the clock kept counting down, and no week ever happened again.
    *
    * This does nothing risky itself, so it does not die, and it is the only
    * thing the clock talks to.
    */
  class SimulationSupervisor extends Actor {
    override val supervisorStrategy = OneForOneStrategy() {
      case _ : ActorInitializationException =>
        //It could not even start, which in practice means the database was not
        //there. Restarting straight away would spin at full speed; the next
        //tick builds a new one in a few minutes, by which time it may be back.
        println("The simulation could not start. Trying again on the next week.")
        SupervisorStrategy.Stop
      case NonFatal(e) =>
        println("The simulation actor failed, starting it again: " + e)
        e.printStackTrace()
        SupervisorStrategy.Restart
    }

    private[this] var worker : Option[ActorRef] = None
    //when the worker last proved it was alive by dealing with a tick
    private[this] var lastTickHandled = System.currentTimeMillis()

    private def weeks() : ActorRef = worker match {
      case Some(actor) => actor
      case None =>
        val actor = context.actorOf(Props(new MainSimulationActor))
        context.watch(actor)
        worker = Some(actor)
        actor
    }

    override def preStart() : Unit = {
      //Nothing outside this process is guaranteed to be watching - the
      //watchdog has to be installed, and it may not be - so the game also
      //keeps an eye on itself.
      context.system.scheduler.schedule(Duration(1, TimeUnit.MINUTES), Duration(1, TimeUnit.MINUTES)) {
        self ! StallCheck
      }
    }

    def receive = {
      case TickHandled =>
        lastTickHandled = System.currentTimeMillis()

      case Terminated(actor) =>
        if (worker.contains(actor)) {
          worker = None
          println("The simulation actor is gone - a new one will play the next week.")
        }

      case StallCheck =>
        val standingStill = System.currentTimeMillis() - lastTickHandled
        if (status != SimulationStatus.IN_PROGRESS && standingStill > stallMillis) {
          println(s"No week has been played for ${standingStill / 1000} seconds - playing one now.")
          //Give it a full window before trying again, rather than every minute
          lastTickHandled = System.currentTimeMillis()
          weeks() ! Start
        }

      case message =>
        weeks() ! message
    }
  }

  /**
    * The simulation can be seen like this:
    * On week(cycle) n. It starts the long simulation (pax simulation) at the "END of the week"
    * when it finishes computing the pax of the past week. It sets the current week to next week (which indicates a beginning of week n + 1)
    * It then runs some postCycle task (these tasks should be short and can be regarded as things to do at the Beginning of a week)
    *
    */
  class MainSimulationActor extends Actor {
    currentWeek = CycleSource.loadCycle()

    //how many scheduled ticks in a row have been let go while nobody played
    private[this] var restedTicks = 0

    def receive = {
      case Start =>
        if (shouldRest()) {
          restedTicks += 1
          println(s"Holiday mode: nobody has played in ${GameConfig.holidayIdleMinutes} minutes - " +
                  s"resting this week ($restedTicks of ${GameConfig.holidaySlowdown - 1})")
        } else {
          restedTicks = 0
          runWeek()
        }
        //A rested week counts too: what the supervisor is watching for is a
        //worker that has stopped answering, not a world that is standing still
        //on purpose.
        context.parent ! TickHandled
      case ForceStart =>
        //asked for explicitly, so it happens whether or not anybody is around
        restedTicks = 0
        runWeek()
        context.parent ! TickHandled
    }

    /**
      * Whether to let this scheduled week go by without playing it out.
      *
      * The game runs all week for people who play on some evenings. Advancing
      * regardless means friends who miss two days come back to a world that
      * moved months without them. So while nobody is around it plays one week
      * in holidaySlowdown - and the moment anybody logs in, the next tick is a
      * real one again.
      */
    private def shouldRest() : Boolean = {
      if (!GameConfig.holidayMode || GameConfig.holidaySlowdown <= 1) {
        return false
      }
      //rested long enough - let one through, so the world still moves slowly
      if (restedTicks >= GameConfig.holidaySlowdown - 1) {
        return false
      }
      try {
        val since = java.util.Calendar.getInstance()
        since.add(java.util.Calendar.MINUTE, -GameConfig.holidayIdleMinutes)
        UserSource.countUsersActiveSince(since) == 0
      } catch {
        //never let this decision be the reason a week does not happen
        case e : Throwable =>
          println("Could not tell whether anybody is playing, so playing the week: " + e.getMessage)
          false
      }
    }

    /**
      * Play one week.
      *
      * A week that fails must not take the ones after it with it. Failing
      * loudly and coming back next week is the worst that should happen, and
      * the failure is printed in full: the night the database driver changed,
      * every cycle threw and nothing said what, which cost most of a day.
      */
    private def runWeek() : Unit = {
      try {
        status = SimulationStatus.IN_PROGRESS
        val endTime = startCycle(currentWeek)

        currentWeek += 1
        CycleSource.setCycle(currentWeek)
        status = SimulationStatus.WAITING_CYCLE_START
        postCycle(currentWeek) //post cycle do some quick updates, no long simulation

        //Write down when this week finished, so that the web site can start a
        //clock without having to ask this process anything. When the link
        //between the two is down - or the simulation has just restarted and
        //the web site is holding a dead address - nothing answers, and a page
        //opens with no clock and no aircraft moving.
        try {
          val previousEnd = lastWeekEndedAt
          lastWeekEndedAt = endTime
          val gap = if (previousEnd > 0) endTime - previousEnd else CYCLE_DURATION.toLong * 1000
          CycleTimingSource.save(currentWeek - 1, endTime, gap)
        } catch {
          case NonFatal(e) => println("Could not write down when the week finished: " + e.getMessage)
        }

        //notify the websockets via EventStream
        println("Publish Cycle Complete message")
        SimulationEventStream.publish(CycleCompleted(currentWeek - 1, endTime))
      } catch {
        case NonFatal(e) =>
          println("!!!!!!!!!!!!!!! WEEK " + currentWeek + " FAILED: " + e)
          e.printStackTrace()
          //Read the week back from the database rather than trusting what is in
          //memory: the failure may have been after it moved on.
          try {
            currentWeek = CycleSource.loadCycle()
          } catch {
            case NonFatal(_) => //it will be right again the next time it works
          }
      } finally {
        //Never leave it looking busy. Something that is stuck IN_PROGRESS for
        //ever is a game nothing will try to rescue.
        status = SimulationStatus.WAITING_CYCLE_START
      }
    }
  }
   
  
  case class Start()

  /** A week asked for explicitly - by the Force week button - which happens
    * whether or not anybody is around to see it. See holiday mode. */
  case object ForceStart

  /** The worker telling the supervisor it dealt with a tick: proof of life. */
  case object TickHandled

  /** The supervisor asking itself whether the game is still moving. */
  case object StallCheck

  var status : SimulationStatus.Value = SimulationStatus.WAITING_CYCLE_START
  object SimulationStatus extends Enumeration {
    type DelegateTaskType = Value
    val IN_PROGRESS, WAITING_CYCLE_START = Value
  }

  
}

