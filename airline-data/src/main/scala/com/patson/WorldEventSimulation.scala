package com.patson

import com.patson.data.{AirlineSource, LogSource, WorldEventSource}
import com.patson.model._

import java.util.concurrent.ThreadLocalRandom
import scala.util.control.NonFatal

/**
  * Makes something happen to the world now and again.
  *
  * Runs at the top of every week: loads what is still going on, hands it to
  * ActiveWorldEvents for the rest of the cycle to read, and now and then
  * starts something new and tells everybody.
  *
  * Nothing here may fail a cycle. A week without news is a shame; a week that
  * does not happen is not.
  */
object WorldEventSimulation {

  //how long the past stays in the panel
  private[this] val HISTORY_WEEKS = 52

  def simulate(cycle : Int, airports : List[Airport]) : Unit = {
    if (!GameConfig.worldEventsEnabled) {
      ActiveWorldEvents.clear()
    } else {
      try {
        WorldEventSource.deleteBefore(cycle - HISTORY_WEEKS)

        val active = WorldEventSource.loadActive(cycle)

        //Only one at a time. Two at once and neither is the news any more, and
        //the arithmetic of a boom inside a slump is nobody's idea of fun.
        //
        //Written without an early return on purpose: in Scala a return from
        //inside a closure is thrown, so the catch below would have caught it
        //and reported the new event as a failure - which is exactly what
        //happened the first time this ran.
        val effective =
          if (active.isEmpty && shouldStartSomething()) {
            create(cycle, airports) match {
              case Some(event) =>
                WorldEventSource.save(event)
                announce(event, cycle)
                List(event)
              case None => active
            }
          } else {
            active
          }

        ActiveWorldEvents.set(effective)
      } catch {
        //NonFatal rather than Throwable, for the same reason
        case NonFatal(e) =>
          println("Could not run the world events: " + e.getMessage)
          ActiveWorldEvents.clear()
      }
    }
  }

  /** Roughly one event every worldEventEveryWeeks weeks, at random. */
  private def shouldStartSomething() : Boolean = {
    val every = Math.max(1, GameConfig.worldEventEveryWeeks)
    ThreadLocalRandom.current().nextInt(every) == 0
  }

  private def create(cycle : Int, airports : List[Airport]) : Option[WorldEvent] = {
    val weeks = GameConfig.worldEventMinWeeks +
      ThreadLocalRandom.current().nextInt(Math.max(1, GameConfig.worldEventMaxWeeks - GameConfig.worldEventMinWeeks + 1))
    val endCycle = cycle + weeks

    //Somewhere worth noticing. An event at an airport nobody flies to is not
    //an event, so this only picks from places with real traffic.
    val notableAirports = airports.filter(airport => airport.size >= 4 && airport.iata != "")
    if (notableAirports.isEmpty) {
      return None
    }

    def pick[T](list : Seq[T]) : T = list(ThreadLocalRandom.current().nextInt(list.size))

    val kind = pick(WorldEventKind.values.toList)
    val strength = GameConfig.worldEventMinStrengthPercent +
      ThreadLocalRandom.current().nextInt(Math.max(1, GameConfig.worldEventMaxStrengthPercent - GameConfig.worldEventMinStrengthPercent + 1))

    kind match {
      case WorldEventKind.FUEL_CRISIS =>
        Some(WorldEvent(kind, None, None, "the whole world", strength, cycle, endCycle))

      case WorldEventKind.AIRPORT_TROUBLE =>
        val airport = pick(notableAirports)
        Some(WorldEvent(kind, None, Some(airport.id), airport.displayText, strength, cycle, endCycle))

      case _ => //the two country-wide ones
        val airport = pick(notableAirports)
        val countryName = com.patson.data.CountrySource.loadCountryByCode(airport.countryCode).map(_.name).getOrElse(airport.countryCode)
        Some(WorldEvent(kind, Some(airport.countryCode), None, countryName, strength, cycle, endCycle))
    }
  }

  /**
    * Tell everybody. An event nobody notices is a number in a table, and the
    * whole point of these is that the five of you have something to react to
    * and something to talk about.
    */
  private def announce(event : WorldEvent, cycle : Int) : Unit = {
    val message = event.announcement
    println("World event: " + message)
    try {
      val airlines = AirlineSource.loadAllAirlines(false).filter(!_.isGenerated)
      val logs = airlines.map(airline => Log(airline, message, LogCategory.SELF_NOTE, LogSeverity.WARN, cycle))
      LogSource.insertLogs(logs)
    } catch {
      case NonFatal(e) => println("Could not announce the world event: " + e.getMessage)
    }
  }
}
