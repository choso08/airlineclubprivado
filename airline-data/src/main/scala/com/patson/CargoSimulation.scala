package com.patson

import com.patson.data._
import com.patson.model._

import java.util.concurrent.ThreadLocalRandom
import scala.util.control.NonFatal

/**
  * Freight: who is offering it, and who managed to carry it.
  *
  * An aircraft's hold flies empty every week of its life in this game, and the
  * only thing an airline can sell is a seat. Cargo is the missing half of the
  * business - but added as "your routes now also earn a bit for freight" it
  * would be money arriving without a decision, which is the least interesting
  * thing a game can be given.
  *
  * So it arrives as contracts. Somebody offers so many tonnes a week between
  * two airports for so many weeks; taking it means committing hold space on a
  * route you now have to keep flying, and being paid steadily for it whatever
  * the passengers do. The hold is real - the capacity is worked out from the
  * aircraft actually assigned that week - so a contract is only worth taking
  * on a route with the size and the frequency to carry it.
  *
  * Nothing here may fail a cycle.
  */
object CargoSimulation {
  private[this] val HISTORY_WEEKS = 52

  /**
    * Run the week, and say what each airline earned.
    *
    * The earnings go back to AirlineSimulation rather than being paid here, so
    * that they land in the same week's accounts as everything else and the
    * income statement adds up.
    */
  def simulate(cycle : Int, flightLinkResult : List[LinkConsumptionDetails]) : Map[Int, Long] = {
    if (!GameConfig.cargoEnabled) {
      Map.empty
    } else {
      try {
        CargoContractSource.expireOffersBefore(cycle - CargoContract.OFFER_WEEKS)
        CargoContractSource.deleteBefore(cycle - HISTORY_WEEKS)

        val earned = settleRunningContracts(cycle, flightLinkResult)
        offerNewContracts(cycle, flightLinkResult)
        earned
      } catch {
        case NonFatal(e) =>
          println("Could not run the cargo contracts: " + e.getMessage)
          Map.empty
      }
    }
  }

  /**
    * Pay for what was carried, and notice what was not.
    *
    * A week the airline could not carry is not the end of the contract - a bad
    * week happens - but it is not free either: it costs a week's payment, and
    * a third missed week ends the contract.
    */
  private def settleRunningContracts(cycle : Int, flightLinkResult : List[LinkConsumptionDetails]) : Map[Int, Long] = {
    val capacityByRoute : Map[(Int, Int, Int), Int] = flightLinkResult.map { details =>
      val link = details.link
      ((link.airline.id, link.from.id, link.to.id), CargoContract.weeklyCapacity(link.capacity.total))
    }.groupBy(_._1).view.mapValues(_.map(_._2).sum).toMap

    val earned = scala.collection.mutable.Map[Int, Long]()

    CargoContractSource.loadByStatus(CargoContractStatus.ACCEPTED).foreach { contract =>
      if (cycle >= contract.endCycle) {
        CargoContractSource.updateStatus(contract.id, CargoContractStatus.ACCEPTED, CargoContractStatus.COMPLETED)
        tell(contract, cycle, s"The cargo contract between ${airportName(contract.fromAirportId)} and ${airportName(contract.toAirportId)} has run its course")
      } else {
        val carried = capacityByRoute.getOrElse((contract.airlineId, contract.fromAirportId, contract.toAirportId), 0)
        if (carried >= contract.tonnesPerWeek) {
          earned.put(contract.airlineId, earned.getOrElse(contract.airlineId, 0L) + contract.pricePerWeek)
          if (contract.missedWeeks > 0) {
            CargoContractSource.setMissedWeeks(contract.id, 0) //forgiven once it is running again
          }
        } else {
          val missed = contract.missedWeeks + 1
          //the penalty is the week's payment, not a multiple of it
          earned.put(contract.airlineId, earned.getOrElse(contract.airlineId, 0L) - contract.pricePerWeek)
          if (missed >= 3) {
            CargoContractSource.updateStatus(contract.id, CargoContractStatus.ACCEPTED, CargoContractStatus.CANCELLED)
            tell(contract, cycle, s"The cargo contract to ${airportName(contract.toAirportId)} has been cancelled - three weeks it could not be carried")
          } else {
            CargoContractSource.setMissedWeeks(contract.id, missed)
            tell(contract, cycle, s"No room for the ${contract.tonnesPerWeek}t to ${airportName(contract.toAirportId)} this week - a week's payment forfeited ($missed of 3)")
          }
        }
      }
    }

    earned.toMap
  }

  /**
    * Put a few contracts on the table.
    *
    * Only on routes an airline already flies: a contract to somewhere it does
    * not go is not an offer, it is a riddle. The tonnage is a share of what
    * that route can actually hold, so accepting is a judgement about how much
    * of the hold to promise away rather than a guess.
    */
  private def offerNewContracts(cycle : Int, flightLinkResult : List[LinkConsumptionDetails]) : Unit = {
    val openByAirline = CargoContractSource.loadByStatus(CargoContractStatus.OFFERED).groupBy(_.airlineId)
    val runningByAirline = CargoContractSource.loadByStatus(CargoContractStatus.ACCEPTED).groupBy(_.airlineId)

    val realAirlineIds = AirlineSource.loadAllAirlines(false).filter(!_.isGenerated).map(_.id).toSet

    flightLinkResult
      .filter(details => realAirlineIds.contains(details.link.airline.id))
      .groupBy(_.link.airline.id)
      .foreach { case (airlineId, linksOfAirline) =>
        val open = openByAirline.getOrElse(airlineId, List.empty)
        val running = runningByAirline.getOrElse(airlineId, List.empty)

        //a handful on the table at a time, no more
        if (open.size < GameConfig.cargoMaxOffers && ThreadLocalRandom.current().nextInt(100) < GameConfig.cargoOfferChancePercent) {
          val taken = (open ::: running).map(contract => (contract.fromAirportId, contract.toAirportId)).toSet

          val candidates = linksOfAirline.filter { details =>
            val link = details.link
            !taken.contains((link.from.id, link.to.id)) &&
              CargoContract.weeklyCapacity(link.capacity.total) >= 5 //too small to be worth a contract
          }

          if (candidates.nonEmpty) {
            val chosen = candidates(ThreadLocalRandom.current().nextInt(candidates.size)).link
            val capacity = CargoContract.weeklyCapacity(chosen.capacity.total)
            //between a quarter and two thirds of the hold: enough to matter,
            //never so much that one bad week of aircraft juggling breaks it
            val tonnes = Math.max(1, capacity * (25 + ThreadLocalRandom.current().nextInt(40)) / 100)
            val weeks = GameConfig.cargoMinWeeks + ThreadLocalRandom.current().nextInt(Math.max(1, GameConfig.cargoMaxWeeks - GameConfig.cargoMinWeeks + 1))
            val price = CargoContract.price(tonnes, chosen.distance)

            val contract = CargoContract(airlineId, chosen.from.id, chosen.to.id, tonnes, price, weeks, cycle, cycle + weeks, CargoContractStatus.OFFERED)
            CargoContractSource.save(contract)

            tell(contract, cycle,
              f"Freight offered: ${tonnes}t a week from ${chosen.from.iata} to ${chosen.to.iata}, $$${price}%,d a week for $weeks weeks - see Cargo")
          }
        }
      }
  }

  private def airportName(airportId : Int) : String =
    com.patson.util.AirportCache.getAirport(airportId).map(_.iata).getOrElse("somewhere")

  private def tell(contract : CargoContract, cycle : Int, message : String) : Unit = {
    try {
      AirlineSource.loadAirlineById(contract.airlineId).foreach { airline =>
        LogSource.insertLogs(List(Log(airline, message, LogCategory.SELF_NOTE, LogSeverity.INFO, cycle)))
      }
    } catch {
      case NonFatal(e) => println("Could not report the cargo contract: " + e.getMessage)
    }
  }
}
