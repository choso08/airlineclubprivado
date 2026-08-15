package com.patson

import com.patson.data._
import com.patson.model._
import com.patson.model.airplane.Airplane

import java.util.concurrent.ThreadLocalRandom
import scala.util.control.NonFatal

/**
  * Things going wrong at one airline at a time.
  *
  * World events give everybody something to react to at once. What they cannot
  * do is single anybody out, and a game between five people needs that as
  * well: a week where one of you is in trouble, and the other four can see it,
  * take advantage of it, or help.
  *
  * The chance is worked out from the state of the fleet, so this is not bad
  * luck landing on whoever it feels like. An airline flying new aircraft
  * almost never has an incident; one flying wrecks has them regularly. That is
  * the point - it turns maintenance from a bill into a decision.
  *
  * Only real airlines are affected. The world has hundreds of computer ones,
  * and news about them is not news.
  *
  * Nothing here may fail a cycle. A week without an incident is nothing; a
  * week that does not happen is everything.
  */
object IncidentSimulation {
  private[this] val HISTORY_WEEKS = 52

  def simulate(cycle : Int) : Unit = {
    if (!GameConfig.incidentsEnabled) {
      ActiveIncidents.clear()
    } else {
      try {
        AirlineIncidentSource.deleteBefore(cycle - HISTORY_WEEKS)

        val active = AirlineIncidentSource.loadActive(cycle)
        val busyAirlineIds = active.map(_.airlineId).toSet

        val started = AirlineSource.loadAllAirlines(false)
          .filter(airline => !airline.isGenerated && !busyAirlineIds.contains(airline.id))
          .flatMap(airline => maybeStartOne(airline, cycle))

        ActiveIncidents.set(active ::: started)
      } catch {
        case NonFatal(e) =>
          println("Could not run the incidents: " + e.getMessage)
          ActiveIncidents.clear()
      }
    }
  }

  /**
    * Whether this airline has one this week, and which.
    *
    * An airline that is not flying anything cannot have a strike or a delay,
    * so the fleet is the gate as well as the odds.
    */
  private def maybeStartOne(airline : Airline, cycle : Int) : Option[AirlineIncident] = {
    val fleet = AirplaneSource.loadAirplanesByOwner(airline.id).filter(airplane => !airplane.isSold && airplane.isReady)
    if (fleet.isEmpty) {
      None
    } else {
      val averageCondition = fleet.map(_.condition).sum / fleet.size

      //0.3 times the base chance on a new fleet, twice it on a worn out one
      val wear = 0.3 + 1.7 * (1 - averageCondition / Airplane.MAX_CONDITION)
      val chance = GameConfig.incidentChancePercent / 100.0 * wear

      if (ThreadLocalRandom.current().nextDouble() >= chance) {
        None
      } else {
        val kind = pickKind()
        val weeks = GameConfig.incidentMinWeeks +
          ThreadLocalRandom.current().nextInt(Math.max(1, GameConfig.incidentMaxWeeks - GameConfig.incidentMinWeeks + 1))
        val strength = GameConfig.incidentMinStrengthPercent +
          ThreadLocalRandom.current().nextInt(Math.max(1, GameConfig.incidentMaxStrengthPercent - GameConfig.incidentMinStrengthPercent + 1))

        val incident =
          if (kind == IncidentKind.BREAKDOWN) {
            //a one-off: it is the repair that hurts, not a spell of bad weeks
            AirlineIncident(airline.id, kind, strength, cycle, cycle + 1)
          } else {
            AirlineIncident(airline.id, kind, strength, cycle, cycle + weeks)
          }

        incident.id = AirlineIncidentSource.save(incident)

        if (kind == IncidentKind.BREAKDOWN) {
          breakOneAircraft(airline, fleet, strength, cycle)
        }

        announce(incident, airline, cycle)
        Some(incident)
      }
    }
  }

  private def pickKind() : IncidentKind.Value = {
    //Breakdowns are the common one because they are the mildest: a bill and a
    //tired aircraft. A strike cancels flights for weeks and should be rare.
    val roll = ThreadLocalRandom.current().nextInt(100)
    if (roll < 50) IncidentKind.BREAKDOWN
    else if (roll < 85) IncidentKind.MAINTENANCE
    else IncidentKind.STRIKE
  }

  /**
    * One aircraft, worse for wear and with a bill attached.
    *
    * The oldest one, because that is the one that would break, and because it
    * makes the lesson legible: this is what flying an aircraft into the ground
    * costs.
    */
  private def breakOneAircraft(airline : Airline, fleet : List[Airplane], strength : Int, cycle : Int) : Unit = {
    val victim = fleet.minBy(_.condition)

    //An aircraft at 0 condition is retired by the simulation the same week, so
    //a breakdown that took all of it would not be a breakdown, it would be a
    //write-off - and one bad roll would destroy an aircraft somebody paid for.
    //Five to fifteen points, and never past a state it can be flown out of.
    val floor = 15.0
    val conditionLost = Math.max(0.0, Math.min(strength / 20.0, victim.condition - floor))

    //Five to fifteen per cent of what the aircraft is worth. A heavy check,
    //not a rebuild.
    val repairBill = Math.max(1L, (victim.value.toDouble * strength / 100 / 20).toLong)

    if (conditionLost > 0) {
      AirplaneSource.updateAirplanes(List(victim.copy(condition = victim.condition - conditionLost)))
    }
    AirlineSource.adjustAirlineBalance(airline.id, -repairBill)
    AirlineSource.saveCashFlowItem(AirlineCashFlowItem(airline.id, CashFlowType.BUY_AIRPLANE, -repairBill))

    LogSource.insertLogs(List(Log(airline,
      f"Your ${victim.model.name} broke down - $$${repairBill}%,d to put it right, and ${conditionLost.toInt}%% off its condition",
      LogCategory.SELF_NOTE, LogSeverity.WARN, cycle)))
  }

  /**
    * Tell everybody.
    *
    * An incident nobody hears about is a number in a table. The whole value of
    * these is that the other four can see it happen.
    */
  private def announce(incident : AirlineIncident, airline : Airline, cycle : Int) : Unit = {
    val message = incident.announcement(airline.name)
    println("Incident: " + message)
    try {
      val airlines = AirlineSource.loadAllAirlines(false).filter(!_.isGenerated)
      val severity = if (incident.airlineId == airline.id) LogSeverity.WARN else LogSeverity.INFO
      LogSource.insertLogs(airlines.map(other => Log(other, message, LogCategory.SELF_NOTE, severity, cycle)))
    } catch {
      case NonFatal(e) => println("Could not announce the incident: " + e.getMessage)
    }
  }
}
