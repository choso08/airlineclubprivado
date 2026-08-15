package com.patson.model

/**
  * An offer from one airline to another.
  *
  * Five friends sharing a world can do exactly two things to each other:
  * compete for the same passengers, and join an alliance. Everything else they
  * agree on - "I will sell you that 787", "pay me and I will stay out of
  * Madrid", "here is ten million to get you started" - happens in a chat
  * window and then cannot be carried out, because the game has no way for
  * money or aircraft to move between two people.
  *
  * This is that way, and deliberately one mechanism rather than several: the
  * proposer offers cash and aircraft, asks for cash in return, and the other
  * side accepts or declines. Selling an aircraft, paying a debt, buying
  * somebody off, or a gift with nothing asked for are all the same thing with
  * different numbers in it.
  *
  * What the deal is FOR is not the game's business - that is what the chat is
  * for. The game only moves what was agreed.
  */
case class AirlineDeal(fromAirlineId : Int,
                       toAirlineId : Int,
                       offeredCash : Long,
                       requestedCash : Long,
                       offeredAirplaneIds : List[Int],
                       note : String,
                       cycle : Int,
                       status : DealStatus.Value,
                       var id : Int = 0) extends IdObject {

  def isPending : Boolean = status == DealStatus.PENDING
}

object DealStatus extends Enumeration {
  type DealStatus = Value
  val PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED = Value

  val label : DealStatus => String = {
    case PENDING => "Waiting"
    case ACCEPTED => "Accepted"
    case DECLINED => "Declined"
    case CANCELLED => "Withdrawn"
    case EXPIRED => "Expired"
  }
}

object AirlineDeal {
  /** How long an offer stands before it lapses, in weeks. Long enough for
    * somebody to come back tomorrow, short enough that the list is not a
    * museum. */
  val EXPIRY_WEEKS = 20
}
