package controllers

import com.patson.data.{AllianceSource, CycleSource, IncomeSource, LinkSource}
import com.patson.model.{AllianceHistory, Link, Period, TransportType}
import com.patson.util.AirlineCache

import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._

/**
 * What happened in the week that just finished.
 *
 * A cycle is the whole game: passengers fly, money moves, somebody's new route
 * turns out to be a mistake - and none of it was visible anywhere. The game
 * shows you your own numbers and a ranking table, so unless you go looking,
 * a week passing looks exactly like a week not passing.
 *
 * This is deliberately about everyone rather than about you. Five people
 * playing the same world mostly want to know what the other four did, and the
 * rest of the interface answers that question badly - one airline at a time,
 * through the rivals panel.
 *
 * Everything here is already public elsewhere in the game: profits are in the
 * rankings, routes are on the map, alliance moves are in the alliance history.
 * Nothing private is added by putting them in one place.
 */
class DigestApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  private val TOP = 5

  def getWeeklyDigest() = Action {
    // The cycle counter has already moved on to the week now being played, so
    // the week with numbers in it is the one before.
    val currentCycle = CycleSource.loadCycle()
    val lastCycle = currentCycle - 1

    Ok(Json.obj(
      "cycle" -> lastCycle,
      "airlines" -> topAirlines(lastCycle),
      "routes" -> topRoutes(),
      "alliances" -> allianceEvents(lastCycle)
    ))
  }

  /** Who made money last week, best first. */
  private def topAirlines(cycle : Int) : JsArray = {
    val incomes = try {
      IncomeSource.loadIncomeByCriteria(List(("cycle", cycle), ("period", Period.WEEKLY.id)))
    } catch {
      case _ : Throwable => List.empty
    }

    var result = Json.arr()
    incomes.toList.sortBy(- _.profit).take(TOP).foreach { income =>
      AirlineCache.getAirline(income.airlineId, false).foreach { airline =>
        result = result.append(Json.obj(
          "airlineId" -> airline.id,
          "airlineName" -> airline.name,
          "profit" -> income.profit,
          "revenue" -> income.revenue))
      }
    }
    result
  }

  /**
   * The most profitable routes in the world last week.
   *
   * Loss-making ones are the more interesting half for whoever owns them, but
   * this is the shared list, and pointing at someone's worst route is a
   * different sort of feature.
   */
  private def topRoutes() : JsArray = {
    val consumptions = try {
      LinkSource.loadLinkConsumptions(1)
    } catch {
      case _ : Throwable => List.empty
    }

    var result = Json.arr()
    consumptions
      .filter(_.link.transportType == TransportType.FLIGHT)
      .sortBy(- _.profit)
      .take(TOP)
      .foreach { consumption =>
        val link = consumption.link
        result = result.append(Json.obj(
          "airlineName" -> link.airline.name,
          "airlineId" -> link.airline.id,
          "from" -> link.from.iata,
          "to" -> link.to.iata,
          "fromCity" -> link.from.city,
          "toCity" -> link.to.city,
          "profit" -> consumption.profit,
          "revenue" -> consumption.revenue))
      }
    result
  }

  /** Alliances formed, joined and left in the week. */
  private def allianceEvents(cycle : Int) : JsArray = {
    val history : List[AllianceHistory] = try {
      AllianceSource.loadAllianceHistoryByCriteria(List(("cycle", cycle)))
    } catch {
      case _ : Throwable => List.empty
    }

    var result = Json.arr()
    history.foreach { entry =>
      result = result.append(Json.obj(
        "airlineName" -> entry.airline.name,
        "allianceName" -> entry.allianceName,
        "event" -> AllianceUtil.getAllianceEventText(entry.event)))
    }
    result
  }
}
