package com.patson

import com.patson.model._
import com.patson.model.airplane.{Airplane, LinkAssignment, Model}

/**
  * How many flights a week an incident actually spoils. Scratch tool:
  *
  *   sbt "airlineData/Test/runMain com.patson.IncidentEffectHarness"
  */
object IncidentEffectHarness extends App {
  val airline = Airline("Test Air", id = 77)
  val from = Airport("AAA", "AAAA", "From", 40, -8, "PT", "Lisbon", "EU", 5, 0, 0, 0, id = 1)
  val to = Airport("BBB", "BBBB", "To", 51, 0, "GB", "London", "EU", 5, 0, 0, 0, id = 2)
  val model = Model.models.find(_.name == "Airbus A320").getOrElse(Model.models.head)

  def countOverWeeks(weeks : Int, condition : Double) : (Int, Int, Int) = {
    var minor = 0
    var major = 0
    var cancelled = 0
    for (_ <- 0 until weeks) {
      val airplane = Airplane(model, airline, constructedCycle = 0, purchasedCycle = 0, condition = condition,
        depreciationRate = 0, value = model.price, id = 1)
      val link = Link(from, to, airline, price = LinkClassValues.getInstance(100, 200, 300), distance = 1500,
        capacity = LinkClassValues.getInstance(150, 0, 0), rawQuality = 50, duration = 120, frequency = 40, FlightType.SHORT_HAUL_INTERNATIONAL)
      link.setAssignedAirplanes(Map(airplane -> LinkAssignment(40, 40 * 240)))
      link.frequency = 40
      LinkSimulation.simulateLinkError(List(link))
      minor += link.minorDelayCount
      major += link.majorDelayCount
      cancelled += link.cancellationCount
    }
    (minor, major, cancelled)
  }

  def report(label : String, counts : (Int, Int, Int), weeks : Int) : Unit = {
    println(f"  $label%-28s minor ${counts._1.toDouble / weeks}%6.1f  major ${counts._2.toDouble / weeks}%5.1f  cancelled ${counts._3.toDouble / weeks}%5.1f  (per week, of 40 flights)")
  }

  val weeks = 200

  println("A fleet in good condition (85%):")
  ActiveIncidents.clear()
  report("no incident", countOverWeeks(weeks, 85), weeks)

  ActiveIncidents.set(List(AirlineIncident(airline.id, IncidentKind.MAINTENANCE, 100, 0, 5)))
  report("inspection, strength 100", countOverWeeks(weeks, 85), weeks)

  ActiveIncidents.set(List(AirlineIncident(airline.id, IncidentKind.STRIKE, 100, 0, 5)))
  report("strike, strength 100", countOverWeeks(weeks, 85), weeks)

  println("A worn out fleet (35%):")
  ActiveIncidents.clear()
  report("no incident", countOverWeeks(weeks, 35), weeks)

  ActiveIncidents.set(List(AirlineIncident(airline.id, IncidentKind.STRIKE, 100, 0, 5)))
  report("strike, strength 100", countOverWeeks(weeks, 35), weeks)

  ActiveIncidents.clear()
}
