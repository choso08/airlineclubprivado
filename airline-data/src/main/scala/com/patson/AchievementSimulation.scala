package com.patson

import com.patson.data.{AchievementSource, AirlineSource, AirplaneSource, LinkSource, LogSource}
import com.patson.model._

/**
  * Checks, once a week, whether anybody has just become the first to do
  * something - and writes it down for good if they have.
  *
  * Only milestones nobody has claimed are even looked at, so once the board
  * fills up this costs a single query a week. Nothing here can fail the cycle:
  * a board that misses a week is a nuisance, a cycle that dies because of one
  * is not.
  */
object AchievementSimulation {

  def simulate(cycle : Int, linkResults : List[LinkConsumptionDetails]) : Unit = {
    try {
      val claimed = AchievementSource.loadClaimedIds()
      val unclaimed = Achievement.all.filterNot(achievement => claimed.contains(achievement.id))
      if (unclaimed.isEmpty) {
        return
      }

      val airlines = AirlineSource.loadAllAirlines(false).filter(_.isGenerated == false)
      if (airlines.isEmpty) {
        return
      }

      val links = LinkSource.loadAllFlightLinks()
      val linksByAirline = links.groupBy(_.airline.id)
      val passengersByAirline = linkResults.groupBy(_.link.airline.id).map {
        case (airlineId, results) => (airlineId, results.map(_.link.soldSeats.total.toLong).sum)
      }

      unclaimed.foreach { achievement =>
        //the airlines that qualify this week, if any; the first one claims it
        val qualifying = airlines.filter { airline =>
          val airlineLinks = linksByAirline.getOrElse(airline.id, List.empty)
          val passengers = passengersByAirline.getOrElse(airline.id, 0L)
          qualifies(achievement, airline, airlineLinks, passengers)
        }

        qualifying.headOption.foreach { winner =>
          if (AchievementSource.claim(achievement.id, winner.id, cycle)) {
            announce(achievement, winner, airlines, cycle)
          }
        }
      }
    } catch {
      case e : Throwable => println("Could not update the achievements board: " + e.getMessage)
    }
  }

  private def qualifies(achievement : Achievement, airline : Airline, links : List[Link], passengers : Long) : Boolean = {
    import Achievement._
    achievement match {
      case FIRST_ROUTE     => links.nonEmpty
      case INTERNATIONAL   => links.exists(link => link.from.countryCode != link.to.countryCode)
      case INTERCONTINENTAL=> links.exists(link => link.from.zone != link.to.zone)
      case THREE_COUNTRIES => served(links, _.countryCode).size >= 3
      case THREE_CONTINENTS=> served(links, _.zone).size >= 3
      case TEN_AIRCRAFT    => fleetSize(airline) >= 10
      case THIRTY_AIRCRAFT => fleetSize(airline) >= 30
      case THOUSAND_PASSENGERS            => passengers >= 1000
      case TEN_THOUSAND_PASSENGERS        => passengers >= 10000
      case HUNDRED_THOUSAND_PASSENGERS    => passengers >= 100000
      case HUNDRED_MILLION => airline.getBalance() >= 100000000L
      case BILLION         => airline.getBalance() >= 1000000000L
      case _               => false
    }
  }

  /** The distinct somethings an airline's routes touch, both ends counted. */
  private def served(links : List[Link], property : Airport => String) : Set[String] = {
    links.flatMap(link => List(property(link.from), property(link.to))).toSet
  }

  private def fleetSize(airline : Airline) : Int = {
    AirplaneSource.loadAirplanesByOwner(airline.id).size
  }

  /**
    * Tell everybody. A milestone nobody hears about is not worth being first
    * at, so this goes in every airline's log, not only the winner's.
    */
  private def announce(achievement : Achievement, winner : Airline, airlines : List[Airline], cycle : Int) : Unit = {
    val message = s"${winner.name} is the first to earn '${achievement.title}' - ${achievement.description}"
    println("Achievement: " + message)
    val logs = airlines.map(airline => Log(airline, message, LogCategory.SELF_NOTE, LogSeverity.INFO, cycle))
    LogSource.insertLogs(logs)
  }
}
