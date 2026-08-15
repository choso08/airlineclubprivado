package controllers

import com.patson.data._
import com.patson.model._
import com.patson.model.airplane.Airplane
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._

/**
  * Deals between two airlines.
  *
  * The proposer offers cash and aircraft and asks for cash in return; the
  * other side accepts or declines. That one shape covers selling an aircraft,
  * paying what was agreed in the chat, buying somebody out of a route, and a
  * straight gift.
  *
  * Everything is checked again at the moment of acceptance, not when the offer
  * was made. An offer can sit for weeks, and in that time the aircraft can be
  * sold, put on a route, or the money spent - so what was true on Monday is
  * not a reason to move anything on Friday.
  */
class AirlineDealApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  private def airplaneJson(airplane : Airplane) : JsObject = Json.obj(
    "id" -> airplane.id,
    "name" -> airplane.model.name,
    "condition" -> Math.round(airplane.condition),
    "value" -> airplane.value)

  private def dealJson(deal : AirlineDeal, viewerId : Int) : JsObject = {
    val fromName = AirlineSource.loadAirlineById(deal.fromAirlineId).map(_.name).getOrElse("a departed airline")
    val toName = AirlineSource.loadAirlineById(deal.toAirlineId).map(_.name).getOrElse("a departed airline")
    val airplanes = deal.offeredAirplaneIds.flatMap(AirplaneSource.loadAirplaneById(_)).map(airplaneJson)

    Json.obj(
      "id" -> deal.id,
      "fromAirlineId" -> deal.fromAirlineId,
      "fromAirlineName" -> fromName,
      "toAirlineId" -> deal.toAirlineId,
      "toAirlineName" -> toName,
      "offeredCash" -> deal.offeredCash,
      "requestedCash" -> deal.requestedCash,
      "offeredAirplanes" -> airplanes,
      "note" -> deal.note,
      "cycle" -> deal.cycle,
      "status" -> deal.status.toString,
      "statusLabel" -> DealStatus.label(deal.status),
      "incoming" -> (deal.toAirlineId == viewerId))
  }

  def getDeals(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val deals = AirlineDealSource.loadByAirline(airlineId)
    Ok(Json.obj("deals" -> deals.map(dealJson(_, airlineId))))
  }

  /** Who an offer can be sent to: everybody else who is actually playing. */
  def getCounterparties(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val others = AirlineSource.loadAllAirlines(false)
      .filter(airline => !airline.isGenerated && airline.id != airlineId)
      .map(airline => Json.obj("id" -> airline.id, "name" -> airline.name))
    Ok(Json.obj("airlines" -> others))
  }

  /** The aircraft this airline could put into an offer: its own, idle ones. */
  def getOfferableAirplanes(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val financed = AirplanePaymentPlanSource.all
    val airplanes = AirplaneSource.loadAirplanesByOwner(airlineId)
      .filter(airplane => !airplane.isSold && airplane.isReady)
      .filter(airplane => !financed.contains(airplane.id)) //aircraft we have not paid for are not ours to give
      .filter(airplane => AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplane.id).assignments.isEmpty)
    Ok(Json.obj("airplanes" -> airplanes.map(airplaneJson)))
  }

  def createDeal(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val json = request.body.asInstanceOf[AnyContentAsJson].json
    val toAirlineId = json.\("toAirlineId").as[Int]
    val offeredCash = Math.max(0L, json.\("offeredCash").asOpt[Long].getOrElse(0L))
    val requestedCash = Math.max(0L, json.\("requestedCash").asOpt[Long].getOrElse(0L))
    val airplaneIds = json.\("offeredAirplaneIds").asOpt[List[Int]].getOrElse(List.empty)
    val note = json.\("note").asOpt[String].getOrElse("").take(200)

    if (toAirlineId == airlineId) {
      BadRequest("Cannot make a deal with yourself")
    } else if (AirlineSource.loadAirlineById(toAirlineId).isEmpty) {
      BadRequest("No such airline")
    } else if (offeredCash == 0 && requestedCash == 0 && airplaneIds.isEmpty) {
      BadRequest("An empty deal is not a deal")
    } else if (offeredCash > request.user.getBalance()) {
      BadRequest("You do not have that much to offer")
    } else {
      //the aircraft have to be yours, idle, and not already sold
      val yours = AirplaneSource.loadAirplanesByOwner(airlineId).map(_.id).toSet
      if (!airplaneIds.forall(yours.contains)) {
        BadRequest("Those are not all your aircraft")
      } else {
        val id = AirlineDealSource.save(AirlineDeal(airlineId, toAirlineId, offeredCash, requestedCash,
          airplaneIds, note, CycleSource.loadCycle(), DealStatus.PENDING))
        //Tell them. An offer nobody knows about is worse than no offer, since
        //the aircraft in it are held out of use while it stands.
        AirlineSource.loadAirlineById(toAirlineId).foreach { other =>
          LogSource.insertLogs(List(Log(other,
            s"${request.user.name} has sent you a deal - see the Deals panel",
            LogCategory.SELF_NOTE, LogSeverity.INFO, CycleSource.loadCycle())))
        }
        Ok(Json.obj("id" -> id))
      }
    }
  }

  def cancelDeal(airlineId : Int, dealId : Int) = AuthenticatedAirline(airlineId) { request =>
    AirlineDealSource.loadById(dealId) match {
      case Some(deal) if deal.fromAirlineId == airlineId && deal.isPending =>
        if (AirlineDealSource.settle(dealId, DealStatus.CANCELLED)) Ok(Json.obj("ok" -> true))
        else BadRequest("That deal has already been settled")
      case Some(_) => Forbidden("Not your deal to withdraw")
      case None => NotFound("No such deal")
    }
  }

  def declineDeal(airlineId : Int, dealId : Int) = AuthenticatedAirline(airlineId) { request =>
    AirlineDealSource.loadById(dealId) match {
      case Some(deal) if deal.toAirlineId == airlineId && deal.isPending =>
        if (AirlineDealSource.settle(dealId, DealStatus.DECLINED)) Ok(Json.obj("ok" -> true))
        else BadRequest("That deal has already been settled")
      case Some(_) => Forbidden("Not your deal to decline")
      case None => NotFound("No such deal")
    }
  }

  /**
    * Accept, and move everything at once.
    *
    * Every condition is checked here rather than trusted from when the offer
    * was made: weeks can pass, and in them the aircraft can be sold or put to
    * work and the money can be spent.
    */
  def acceptDeal(airlineId : Int, dealId : Int) = AuthenticatedAirline(airlineId) { request =>
    AirlineDealSource.loadById(dealId) match {
      case None => NotFound("No such deal")
      case Some(deal) if deal.toAirlineId != airlineId => Forbidden("Not your deal to accept")
      case Some(deal) if !deal.isPending => BadRequest("That deal has already been settled")
      case Some(deal) =>
        val proposerOption = AirlineSource.loadAirlineById(deal.fromAirlineId)
        if (proposerOption.isEmpty) {
          BadRequest("The airline that offered this is gone")
        } else {
          val proposer = proposerOption.get

          val airplanes = deal.offeredAirplaneIds.flatMap(AirplaneSource.loadAirplaneById(_))
          val financed = AirplanePaymentPlanSource.all
          val stillTheirs = airplanes.size == deal.offeredAirplaneIds.size &&
            airplanes.forall(airplane => airplane.owner.id == deal.fromAirlineId && !airplane.isSold && !financed.contains(airplane.id))
          val stillIdle = airplanes.forall(airplane =>
            AirplaneSource.loadAirplaneLinkAssignmentsByAirplaneId(airplane.id).assignments.isEmpty)

          val homeOption = request.user.getBases().find(_.headquarter)

          if (!stillTheirs) {
            BadRequest("Those aircraft are no longer theirs to give")
          } else if (!stillIdle) {
            BadRequest("Those aircraft are flying routes now - they cannot change hands")
          } else if (airplanes.nonEmpty && homeOption.isEmpty) {
            BadRequest("You need a headquarters before you can take on aircraft")
          } else if (request.user.getBalance() < deal.requestedCash) {
            BadRequest("You cannot afford this deal")
          } else if (proposer.getBalance() < deal.offeredCash) {
            BadRequest("They can no longer afford what they offered")
          } else if (!AirlineDealSource.settle(dealId, DealStatus.ACCEPTED)) {
            BadRequest("That deal has already been settled")
          } else {
            //From here everything has been checked and the deal is claimed.
            if (deal.offeredCash > 0) {
              AirlineSource.adjustAirlineBalance(deal.fromAirlineId, -deal.offeredCash)
              AirlineSource.adjustAirlineBalance(airlineId, deal.offeredCash)
              AirlineSource.saveCashFlowItem(AirlineCashFlowItem(deal.fromAirlineId, CashFlowType.ASSET_TRANSACTION, -deal.offeredCash))
              AirlineSource.saveCashFlowItem(AirlineCashFlowItem(airlineId, CashFlowType.ASSET_TRANSACTION, deal.offeredCash))
            }
            if (deal.requestedCash > 0) {
              AirlineSource.adjustAirlineBalance(airlineId, -deal.requestedCash)
              AirlineSource.adjustAirlineBalance(deal.fromAirlineId, deal.requestedCash)
              AirlineSource.saveCashFlowItem(AirlineCashFlowItem(airlineId, CashFlowType.ASSET_TRANSACTION, -deal.requestedCash))
              AirlineSource.saveCashFlowItem(AirlineCashFlowItem(deal.fromAirlineId, CashFlowType.ASSET_TRANSACTION, deal.requestedCash))
            }

            //The aircraft move to the buyer's headquarters: their old home is
            //a base the new owner probably does not have.
            homeOption.foreach { home =>
              val moved = airplanes.map { airplane =>
                airplane.owner = request.user
                airplane.home = home.airport
                airplane
              }
              if (moved.nonEmpty) {
                AirplaneSource.updateAirplanes(moved)
              }
            }

            val cycle = CycleSource.loadCycle()
            val summary = describe(deal, proposer.name, request.user.name, airplanes.size)
            LogSource.insertLogs(List(
              Log(proposer, summary, LogCategory.SELF_NOTE, LogSeverity.INFO, cycle),
              Log(request.user, summary, LogCategory.SELF_NOTE, LogSeverity.INFO, cycle)))

            Ok(Json.obj("ok" -> true))
          }
        }
    }
  }

  private def describe(deal : AirlineDeal, fromName : String, toName : String, airplaneCount : Int) : String = {
    val parts = scala.collection.mutable.ListBuffer[String]()
    if (airplaneCount > 0) parts += s"$airplaneCount aircraft"
    if (deal.offeredCash > 0) parts += f"$$${deal.offeredCash}%,d"
    val given = if (parts.isEmpty) "nothing" else parts.mkString(" and ")
    val paid = if (deal.requestedCash > 0) f" for $$${deal.requestedCash}%,d" else " for nothing in return"
    s"Deal done: $fromName gave $toName $given$paid"
  }
}
