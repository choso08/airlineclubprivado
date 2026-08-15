package com.patson.model

/**
  * Something going wrong at one airline, for a few weeks.
  *
  * The world events that were added give everybody something to react to at
  * once - a fuel crisis, a boom, a slump. What they cannot do is single
  * anybody out, and a game between five people needs that too: a week where
  * one of you is in trouble and the other four can see it, take advantage of
  * it, or help.
  *
  * Nothing here is pure bad luck. The chance of an incident is worked out from
  * the state of the fleet, so an airline that keeps its aircraft in condition
  * almost never has one and an airline flying wrecks has them often. That
  * makes maintenance a decision rather than a bill.
  *
  * The effects deliberately reuse the delays and cancellations the game
  * already simulates. Those already cost compensation, already disappoint
  * passengers, and already show on the flight card - so an incident needs no
  * new arithmetic anywhere, only a heavier hand for a few weeks.
  */
case class AirlineIncident(airlineId : Int,
                           kind : IncidentKind.Value,
                           strength : Int, //how many times worse than usual, as a percentage
                           startCycle : Int,
                           endCycle : Int,
                           var id : Int = 0) extends IdObject {

  def isActive(cycle : Int) : Boolean = cycle >= startCycle && cycle < endCycle

  def weeksLeft(cycle : Int) : Int = Math.max(0, endCycle - cycle)

  /** What everybody is told when it starts. */
  def announcement(airlineName : String) : String = {
    val weeks = endCycle - startCycle
    kind match {
      case IncidentKind.STRIKE =>
        s"Cabin crew at $airlineName have walked out - flights cancelled for the next $weeks week(s)"
      case IncidentKind.MAINTENANCE =>
        s"$airlineName is under a maintenance inspection - expect delays for the next $weeks week(s)"
      case IncidentKind.BREAKDOWN =>
        s"An aircraft of $airlineName broke down and is being repaired"
    }
  }
}

object IncidentKind extends Enumeration {
  type IncidentKind = Value
  val STRIKE, MAINTENANCE, BREAKDOWN = Value
}

/**
  * What is going on right now, for the rest of the cycle to read.
  *
  * Set once a week by IncidentSimulation and then read for every flight of
  * every airline, so it is a plain map in memory rather than a query.
  */
object ActiveIncidents {
  @volatile private[this] var byAirline : Map[Int, List[AirlineIncident]] = Map.empty

  def set(incidents : List[AirlineIncident]) : Unit = {
    byAirline = incidents.groupBy(_.airlineId)
  }

  def clear() : Unit = byAirline = Map.empty

  def of(airlineId : Int) : List[AirlineIncident] = byAirline.getOrElse(airlineId, List.empty)

  /** How much more likely a flight of this airline is to be delayed. */
  def delayMultiplier(airlineId : Int) : Double = multiplier(airlineId, IncidentKind.MAINTENANCE)

  /** How much more likely a flight of this airline is to be cancelled. */
  def cancellationMultiplier(airlineId : Int) : Double = multiplier(airlineId, IncidentKind.STRIKE)

  private def multiplier(airlineId : Int, kind : IncidentKind.Value) : Double = {
    of(airlineId).filter(_.kind == kind) match {
      case Nil => 1.0
      case incidents => 1.0 + incidents.map(_.strength).sum / 100.0
    }
  }
}
