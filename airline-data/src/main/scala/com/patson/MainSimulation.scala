

package com.patson

import java.util.concurrent.TimeUnit
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.Actor
import com.patson.data._
import com.patson.model.{Airport, GameConfig, Seasons}
import com.patson.stream.{CycleCompleted, CycleStart, DirectDemandInfo, SimulationEventStream}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirplaneOwnershipInfo, AirportCache}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

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

//  implicit val actorSystem = ActorSystem("rabbit-akka-stream")

//  import actorSystem.dispatcher

//  implicit val materializer = FlowMaterializer()
  
  mainFlow
  
  def mainFlow() = {
    val actor = actorSystem.actorOf(Props[MainSimulationActor])
    actorSystem.scheduler.schedule(Duration.Zero, Duration(CYCLE_DURATION, TimeUnit.SECONDS), actor, Start)

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
          actor ! ForceStart
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
      CycleProfiler.phase("airlines") { AirlineSimulation.airlineSimulation(cycle, flightLinkResult, loungeResult, airplanes) }
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
      case ForceStart =>
        //asked for explicitly, so it happens whether or not anybody is around
        restedTicks = 0
        runWeek()
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

    private def runWeek() : Unit = {
      status = SimulationStatus.IN_PROGRESS
      val endTime = startCycle(currentWeek)

      currentWeek += 1
      CycleSource.setCycle(currentWeek)
      status = SimulationStatus.WAITING_CYCLE_START
      postCycle(currentWeek) //post cycle do some quick updates, no long simulation

      //notify the websockets via EventStream
      println("Publish Cycle Complete message")
      SimulationEventStream.publish(CycleCompleted(currentWeek - 1, endTime))
    }
  }
   
  
  case class Start()

  /** A week asked for explicitly - by the Force week button - which happens
    * whether or not anybody is around to see it. See holiday mode. */
  case object ForceStart

  var status : SimulationStatus.Value = SimulationStatus.WAITING_CYCLE_START
  object SimulationStatus extends Enumeration {
    type DelegateTaskType = Value
    val IN_PROGRESS, WAITING_CYCLE_START = Value
  }

  
}

