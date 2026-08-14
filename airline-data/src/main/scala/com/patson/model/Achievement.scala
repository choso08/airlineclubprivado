package com.patson.model

/**
  * Milestones, and who reached each one first.
  *
  * A public server has thousands of players and a world ranking that means
  * something. Five friends have neither, so there is nothing to be first at
  * and nothing to point to afterwards. This is the smallest thing that fixes
  * that: a short list of firsts, each claimed once, kept for good.
  *
  * Only the first airline to reach one is recorded. That is the whole point -
  * a milestone everybody eventually gets is not worth having.
  */
case class Achievement(id : String, title : String, description : String)

case class AchievementRecord(achievement : Achievement, airlineId : Int, airlineName : String, cycle : Int)

object Achievement {
  val FIRST_ROUTE = Achievement("first_route",
    "Wheels up", "Opened the first route on this server")

  val INTERNATIONAL = Achievement("international",
    "Across a border", "First to fly between two countries")

  val INTERCONTINENTAL = Achievement("intercontinental",
    "The long way", "First to fly between two continents")

  val THREE_COUNTRIES = Achievement("three_countries",
    "Three flags", "First to serve airports in three countries")

  val THREE_CONTINENTS = Achievement("three_continents",
    "Three continents", "First to serve airports on three continents")

  val TEN_AIRCRAFT = Achievement("ten_aircraft",
    "A fleet", "First to own ten aircraft")

  val THIRTY_AIRCRAFT = Achievement("thirty_aircraft",
    "An airline, properly", "First to own thirty aircraft")

  val THOUSAND_PASSENGERS = Achievement("thousand_passengers",
    "A thousand a week", "First to carry a thousand passengers in one week")

  val TEN_THOUSAND_PASSENGERS = Achievement("ten_thousand_passengers",
    "Ten thousand a week", "First to carry ten thousand passengers in one week")

  val HUNDRED_THOUSAND_PASSENGERS = Achievement("hundred_thousand_passengers",
    "A hundred thousand a week", "First to carry a hundred thousand passengers in one week")

  val HUNDRED_MILLION = Achievement("hundred_million",
    "A hundred million", "First to hold $100,000,000")

  val BILLION = Achievement("billion",
    "A billion", "First to hold $1,000,000,000")

  /** In the order they are meant to be read, easiest first. */
  val all = List(
    FIRST_ROUTE,
    INTERNATIONAL,
    THREE_COUNTRIES,
    TEN_AIRCRAFT,
    THOUSAND_PASSENGERS,
    INTERCONTINENTAL,
    THREE_CONTINENTS,
    TEN_THOUSAND_PASSENGERS,
    THIRTY_AIRCRAFT,
    HUNDRED_MILLION,
    HUNDRED_THOUSAND_PASSENGERS,
    BILLION)

  val byId : Map[String, Achievement] = all.map(a => (a.id, a)).toMap
}
