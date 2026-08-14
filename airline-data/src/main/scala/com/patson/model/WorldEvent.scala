package com.patson.model

/**
  * Things that happen to the world, for a few weeks at a time.
  *
  * The game has an event system, and it is used for exactly one thing: the
  * Olympics, once every four in-game years. Between those, nothing ever
  * happens that anybody has to react to - the world is the same on week 300 as
  * it was on week 3, and the only news is what the players did to each other.
  *
  * These are the news. Short, visible, and worth talking about: a fuel crisis
  * that squeezes everybody at once, a country suddenly worth flying to, an
  * airport nobody can rely on for a month. They are deliberately blunt - one
  * number, one place, one span of weeks - because a private game between five
  * people needs things to happen, not a simulation of why.
  */
case class WorldEvent(kind : WorldEventKind.Value,
                      scopeCountry : Option[String],
                      scopeAirportId : Option[Int],
                      scopeName : String,
                      strengthPercent : Int,
                      startCycle : Int,
                      endCycle : Int,
                      var id : Int = 0) extends IdObject {

  def isActive(cycle : Int) : Boolean = cycle >= startCycle && cycle < endCycle

  def weeksLeft(cycle : Int) : Int = Math.max(0, endCycle - cycle)

  /** What to tell everybody when it starts. */
  def announcement : String = WorldEventKind.announcement(kind, scopeName, strengthPercent)

  /** The short line the panel shows. */
  def description : String = WorldEventKind.description(kind, scopeName, strengthPercent)
}

object WorldEventKind extends Enumeration {
  type WorldEventKind = Value

  /** Everybody's fuel bill goes up. No favourites, nowhere to hide. */
  val FUEL_CRISIS = Value

  /** One country is suddenly worth flying to. */
  val TOURISM_BOOM = Value

  /** One country stops travelling: recession, unrest, a bad year. */
  val ECONOMIC_SLUMP = Value

  /** One airport cannot be relied on - strike, weather, closed runway. */
  val AIRPORT_TROUBLE = Value

  val label : WorldEventKind => String = {
    case FUEL_CRISIS => "Fuel crisis"
    case TOURISM_BOOM => "Tourism boom"
    case ECONOMIC_SLUMP => "Economic slump"
    case AIRPORT_TROUBLE => "Airport disruption"
  }

  def announcement(kind : WorldEventKind, scope : String, strength : Int) : String = kind match {
    case FUEL_CRISIS => s"Fuel crisis: fuel is costing $strength% more everywhere, and will for a while"
    case TOURISM_BOOM => s"Everybody wants to go to $scope - demand there is up $strength%"
    case ECONOMIC_SLUMP => s"$scope has stopped travelling - demand there is down $strength%"
    case AIRPORT_TROUBLE => s"Trouble at $scope - traffic through it is down $strength%"
  }

  def description(kind : WorldEventKind, scope : String, strength : Int) : String = kind match {
    case FUEL_CRISIS => s"Fuel +$strength% worldwide"
    case TOURISM_BOOM => s"$scope: demand +$strength%"
    case ECONOMIC_SLUMP => s"$scope: demand -$strength%"
    case AIRPORT_TROUBLE => s"$scope: traffic -$strength%"
  }
}

/**
  * The active events, kept in memory so the simulation can ask about them
  * millions of times a cycle without touching the database.
  *
  * Refreshed once per cycle by WorldEventSimulation. Empty until then, which
  * is the right answer for a world where nothing is happening.
  */
object ActiveWorldEvents {
  @volatile private[this] var fuelMultiplier : Double = 1.0
  @volatile private[this] var byCountry : Map[String, Double] = Map.empty
  @volatile private[this] var byAirport : Map[Int, Double] = Map.empty

  def set(events : List[WorldEvent]) : Unit = {
    var fuel = 1.0
    val countries = scala.collection.mutable.HashMap[String, Double]()
    val airports = scala.collection.mutable.HashMap[Int, Double]()

    events.foreach { event =>
      val factor = event.strengthPercent / 100.0
      event.kind match {
        case WorldEventKind.FUEL_CRISIS =>
          fuel = fuel * (1 + factor)
        case WorldEventKind.TOURISM_BOOM =>
          event.scopeCountry.foreach(code => countries.put(code, countries.getOrElse(code, 1.0) * (1 + factor)))
        case WorldEventKind.ECONOMIC_SLUMP =>
          event.scopeCountry.foreach(code => countries.put(code, countries.getOrElse(code, 1.0) * (1 - factor)))
        case WorldEventKind.AIRPORT_TROUBLE =>
          event.scopeAirportId.foreach(id => airports.put(id, airports.getOrElse(id, 1.0) * (1 - factor)))
      }
    }

    fuelMultiplier = fuel
    byCountry = countries.toMap
    byAirport = airports.toMap
  }

  def clear() : Unit = set(List.empty)

  def fuelCostMultiplier : Double = fuelMultiplier

  /** What is happening to travel between these two places, as a multiplier.
    * 1.0 when the world is quiet, which is most of the time. */
  def demandMultiplier(from : Airport, to : Airport) : Double = {
    if (byCountry.isEmpty && byAirport.isEmpty) {
      1.0
    } else {
      var factor = 1.0
      if (byCountry.nonEmpty) {
        //a boom in one country lifts travel at both ends of a route into it
        byCountry.get(from.countryCode).foreach(f => factor *= f)
        if (to.countryCode != from.countryCode) {
          byCountry.get(to.countryCode).foreach(f => factor *= f)
        }
      }
      if (byAirport.nonEmpty) {
        byAirport.get(from.id).foreach(f => factor *= f)
        byAirport.get(to.id).foreach(f => factor *= f)
      }
      factor
    }
  }
}
