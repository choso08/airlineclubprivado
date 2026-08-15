package com.patson.model

/**
  * An agreement to move somebody's freight, week after week.
  *
  * The game has no cargo at all: an aircraft's hold flies empty every week of
  * its life, and the only thing an airline can sell is a seat. Cargo is the
  * obvious missing half of the business, but bolted on as "your routes now
  * also earn a bit for freight" it would be money that arrives without a
  * decision, which is the least interesting thing a game can add.
  *
  * As contracts it is a decision. Somebody offers so many tonnes a week
  * between two airports, for so many weeks, at a price. Taking it means
  * committing hold space you might rather have kept free, on a route you now
  * have to keep flying - and being paid steadily for it whatever the
  * passengers do that week.
  *
  * The hold is real: capacity comes from the aircraft actually assigned to the
  * route that week, so a contract is only worth taking on a route with the
  * size and the frequency to carry it.
  */
case class CargoContract(airlineId : Int,
                         fromAirportId : Int,
                         toAirportId : Int,
                         tonnesPerWeek : Int,
                         pricePerWeek : Long,
                         weeks : Int,
                         startCycle : Int,
                         endCycle : Int,
                         status : CargoContractStatus.Value,
                         missedWeeks : Int = 0,
                         var id : Int = 0) extends IdObject {

  def isOffer : Boolean = status == CargoContractStatus.OFFERED
  def isActive : Boolean = status == CargoContractStatus.ACCEPTED

  def weeksLeft(cycle : Int) : Int = Math.max(0, endCycle - cycle)
}

object CargoContractStatus extends Enumeration {
  type CargoContractStatus = Value
  val OFFERED, ACCEPTED, DECLINED, COMPLETED, CANCELLED, EXPIRED = Value

  val label : CargoContractStatus => String = {
    case OFFERED => "On offer"
    case ACCEPTED => "Running"
    case DECLINED => "Declined"
    case COMPLETED => "Completed"
    case CANCELLED => "Cancelled"
    case EXPIRED => "Lapsed"
  }
}

object CargoContract {
  /**
    * Tonnes one seat's worth of aircraft can also carry, per flight.
    *
    * A narrowbody with 180 seats takes a few tonnes in the hold once the
    * passengers' own bags are in, and that is what this is - the space left
    * over, not the aircraft's structural payload.
    */
  val TONNES_PER_SEAT_PER_FLIGHT = 0.02

  /**
    * What a route can carry in a week.
    *
    * The seat count a link carries is already the week's - every flight
    * counted - so the frequency must not be applied again here. It was, at
    * first, and a nineteen seat aircraft was offered twenty seven tonnes.
    */
  def weeklyCapacity(weeklySeats : Int) : Int =
    Math.floor(weeklySeats * TONNES_PER_SEAT_PER_FLIGHT).toInt

  /** What that freight is worth over that distance. */
  def price(tonnes : Int, distance : Int) : Long =
    Math.max(1L, Math.round(tonnes.toDouble * distance * GameConfig.cargoRatePerTonneKm))

  /** How long an offer stands before nobody is waiting for an answer. */
  val OFFER_WEEKS = 6
}
