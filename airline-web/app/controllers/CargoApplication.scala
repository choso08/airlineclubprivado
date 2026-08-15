package controllers

import com.patson.data._
import com.patson.model._
import com.patson.util.AirportCache
import controllers.AuthenticationObject.AuthenticatedAirline

import javax.inject.Inject
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._

/**
  * Freight contracts: what is on the table, and what is running.
  *
  * Accepting is checked here rather than trusted from when the offer was made,
  * because an offer stands for weeks and in that time the route it depends on
  * can be closed, shrunk, or given different aircraft.
  */
class CargoApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  private def airportText(airportId : Int) : String =
    AirportCache.getAirport(airportId).map(airport => s"${airport.city} (${airport.iata})").getOrElse("somewhere")

  private def contractJson(contract : CargoContract, currentCycle : Int) : JsObject = Json.obj(
    "id" -> contract.id,
    "fromAirportId" -> contract.fromAirportId,
    "toAirportId" -> contract.toAirportId,
    "from" -> airportText(contract.fromAirportId),
    "to" -> airportText(contract.toAirportId),
    "tonnesPerWeek" -> contract.tonnesPerWeek,
    "pricePerWeek" -> contract.pricePerWeek,
    "weeks" -> contract.weeks,
    "weeksLeft" -> contract.weeksLeft(currentCycle),
    "missedWeeks" -> contract.missedWeeks,
    "status" -> contract.status.toString,
    "statusLabel" -> CargoContractStatus.label(contract.status),
    "capacityNow" -> capacityFor(contract))

  /** What that airline's route between those two airports can hold this week. */
  private def capacityFor(contract : CargoContract) : Int = {
    LinkSource.loadFlightLinkByAirportsAndAirline(contract.fromAirportId, contract.toAirportId, contract.airlineId) match {
      case Some(link) => CargoContract.weeklyCapacity(link.capacity.total)
      case None => 0
    }
  }

  def getContracts(airlineId : Int) = AuthenticatedAirline(airlineId) { request =>
    val currentCycle = CycleSource.loadCycle()
    val contracts = CargoContractSource.loadByAirline(airlineId)
    Ok(Json.obj(
      "enabled" -> GameConfig.cargoEnabled,
      "contracts" -> contracts.map(contractJson(_, currentCycle))))
  }

  def acceptContract(airlineId : Int, contractId : Int) = AuthenticatedAirline(airlineId) { request =>
    CargoContractSource.loadById(contractId) match {
      case None => NotFound("No such contract")
      case Some(contract) if contract.airlineId != airlineId => Forbidden("Not your contract")
      case Some(contract) if !contract.isOffer => BadRequest("That contract is no longer on the table")
      case Some(contract) =>
        //The route has to be able to carry it now, not when it was offered
        if (capacityFor(contract) < contract.tonnesPerWeek) {
          BadRequest(s"That route can only hold ${capacityFor(contract)}t a week now - it needs ${contract.tonnesPerWeek}t")
        } else {
          val currentCycle = CycleSource.loadCycle()
          if (CargoContractSource.updateStatus(contractId, CargoContractStatus.OFFERED, CargoContractStatus.ACCEPTED,
                startCycle = currentCycle, endCycle = currentCycle + contract.weeks)) {
            Ok(Json.obj("ok" -> true))
          } else {
            BadRequest("That contract has already been settled")
          }
        }
    }
  }

  def declineContract(airlineId : Int, contractId : Int) = AuthenticatedAirline(airlineId) { request =>
    CargoContractSource.loadById(contractId) match {
      case None => NotFound("No such contract")
      case Some(contract) if contract.airlineId != airlineId => Forbidden("Not your contract")
      case Some(contract) if !contract.isOffer => BadRequest("That contract is no longer on the table")
      case Some(_) =>
        if (CargoContractSource.updateStatus(contractId, CargoContractStatus.OFFERED, CargoContractStatus.DECLINED)) {
          Ok(Json.obj("ok" -> true))
        } else {
          BadRequest("That contract has already been settled")
        }
    }
  }

  /**
    * Walk away from a contract that is running.
    *
    * It costs four weeks of payments - enough that taking one on is a
    * commitment, little enough that a route that stopped making sense is not a
    * sentence.
    */
  def cancelContract(airlineId : Int, contractId : Int) = AuthenticatedAirline(airlineId) { request =>
    CargoContractSource.loadById(contractId) match {
      case None => NotFound("No such contract")
      case Some(contract) if contract.airlineId != airlineId => Forbidden("Not your contract")
      case Some(contract) if !contract.isActive => BadRequest("That contract is not running")
      case Some(contract) =>
        val penalty = contract.pricePerWeek * 4
        if (CargoContractSource.updateStatus(contractId, CargoContractStatus.ACCEPTED, CargoContractStatus.CANCELLED)) {
          AirlineSource.adjustAirlineBalance(airlineId, -penalty)
          AirlineSource.saveCashFlowItem(AirlineCashFlowItem(airlineId, CashFlowType.ASSET_TRANSACTION, -penalty))
          LogSource.insertLogs(List(Log(request.user,
            f"Walked away from the freight to ${airportText(contract.toAirportId)} - $$${penalty}%,d to break it",
            LogCategory.SELF_NOTE, LogSeverity.WARN, CycleSource.loadCycle())))
          Ok(Json.obj("ok" -> true, "penalty" -> penalty))
        } else {
          BadRequest("That contract has already been settled")
        }
    }
  }
}
