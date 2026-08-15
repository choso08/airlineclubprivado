package com.patson

import com.patson.model._

/**
  * Measures the demand curve: what share of passengers still fly at each price.
  * Scratch tool, run by hand:
  *
  *   AIRLINE_PRICE_ELASTICITY=0  sbt "airlineData/Test/runMain com.patson.PriceCurveHarness"
  */
object PriceCurveHarness extends App {
  val airline = Airline("airline 1", id = 1)

  val fromAirport = Airport.fromId(1).copy(baseIncome = 40000, basePopulation = 1)
  fromAirport.initAirlineAppeals(Map(airline.id -> AirlineAppeal(0)))
  fromAirport.initLounges(List.empty)

  val toAirport = Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2)
  toAirport.initAirlineAppeals(Map(airline.id -> AirlineAppeal(0)))
  toAirport.initLounges(List.empty)

  val distance = Util.calculateDistance(fromAirport.latitude, fromAirport.longitude, toAirport.latitude, toAirport.longitude).intValue()
  val suggestedPrice = Pricing.computeStandardPriceForAllClass(distance, fromAirport, toAirport)
  val duration = Computation.computeStandardFlightDuration(distance)
  val linkType = Computation.getFlightType(fromAirport, toAirport, distance)

  println(s"elasticity ${GameConfig.priceElasticityPercent}, distance $distance km, standard economy ${suggestedPrice(ECONOMY)}")

  List(0.8, 0.9, 1.0, 1.05, 1.1, 1.15, 1.2, 1.3, 1.4, 1.5, 1.6, 1.8).foreach { ratio =>
    val quality = fromAirport.expectedQuality(linkType, ECONOMY)
    val link = Link(fromAirport, toAirport, airline, price = suggestedPrice * ratio, distance = distance,
      LinkClassValues.getInstance(10000, 10000, 10000), rawQuality = quality, duration = duration, frequency = 14, linkType)
    link.setQuality(quality)

    var total = 0
    var accepted = 0
    for (_ <- 0 until 200) {
      DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport).pool.foreach {
        case (linkClass, preferences) =>
          preferences.filter(_.loungeLevelRequired == 0).foreach { preference =>
            val cost = preference.computeCost(link, linkClass)
            val considerations = List(LinkConsideration.getExplicit(link, cost, linkClass, false))
            val route = Route(considerations, cost)
            val seed = PassengerSimulation.passengerSeed(PassengerGroup(fromAirport, preference, PassengerType.TOURIST), toAirport)
            if (PassengerSimulation.getRouteRejection(route, fromAirport, toAirport, linkClass, seed).isEmpty) {
              accepted += 1
            }
            total += 1
          }
      }
    }
    println(f"  ${ratio}%.2f x standard price -> ${accepted * 100.0 / total}%5.1f%% still fly")
  }
}
