package com.patson.model

import com.patson.data.CycleSource

/**
  * The time of year, and what it does to who wants to fly.
  *
  * The game runs a calendar - one cycle is one week, and the page shows a real
  * date - and then ignores it entirely. Demand in January is demand in August,
  * so a route to the Algarve is worth exactly as much in the rain as in the
  * sun, and picking routes is a decision made once and never revisited.
  *
  * With seasons it becomes a decision about time. A holiday route earns its
  * money in a few months and costs you the rest of the year; a business route
  * is steady except when the offices empty. That is a real airline's problem,
  * and it is the cheapest interesting one to give a game that already has a
  * calendar it is not using.
  *
  * Deliberately simple: two curves, one for people going on holiday and one
  * for people going to work, and the hemisphere decides which way round summer
  * is. Nobody needs a climate model to understand that August is busy.
  */
object Seasons {
  private[this] val WEEKS_PER_YEAR = 52

  //Set by the simulation once a week. The web front-end never runs a cycle, so
  //it falls back to asking the database - at most once a minute, because this
  //is called for every pair of airports the planning dialog considers.
  @volatile private[this] var knownWeekOfYear : Int = -1
  @volatile private[this] var lastLookup : Long = 0

  def setCycle(cycle : Int) : Unit = {
    knownWeekOfYear = Math.floorMod(cycle, WEEKS_PER_YEAR)
    lastLookup = System.currentTimeMillis()
  }

  def weekOfYear : Int = {
    val now = System.currentTimeMillis()
    if (knownWeekOfYear < 0 || now - lastLookup > 60000) {
      try {
        setCycle(CycleSource.loadCycle())
      } catch {
        case _ : Throwable => lastLookup = now //do not hammer a database that is down
      }
    }
    if (knownWeekOfYear < 0) 0 else knownWeekOfYear
  }

  /**
    * Where in the year we are, as an angle: 0 at the start of January, half
    * way round by the start of July.
    */
  private def yearFraction(week : Int) : Double = week.toDouble / WEEKS_PER_YEAR

  /**
    * How busy holiday travel is at this airport, this week.
    *
    * Peaks in the local summer - late July in the north, late January in the
    * south - and bottoms out half a year later. Near the equator the swing is
    * small, because there is no summer to speak of, only a wet season nobody
    * here is modelling.
    */
  private def leisureFactor(week : Int, airport : Airport, amplitude : Double) : Double = {
    val northern = airport.latitude >= 0
    //week 29 is late July; the southern peak is half a year away
    val peakWeek = if (northern) 29.0 else 3.0
    val phase = 2 * Math.PI * (yearFraction(week) - peakWeek / WEEKS_PER_YEAR)

    //full swing at the poles, a quarter of it on the equator
    val latitudeWeight = 0.25 + 0.75 * Math.min(1.0, Math.abs(airport.latitude) / 50.0)

    1 + amplitude * latitudeWeight * Math.cos(phase)
  }

  /**
    * How busy business travel is this week.
    *
    * Steady, except that offices empty around Christmas and again in the
    * middle of August - and unlike holidays, that happens at the same time for
    * both ends of the route, because it is about the working year rather than
    * the weather.
    */
  private def businessFactor(week : Int, amplitude : Double) : Double = {
    val quiet =
      if (week >= 50 || week <= 1) 1.0       //Christmas and the new year
      else if (week >= 31 && week <= 34) 0.7 //the August lull
      else 0.0

    1 - amplitude * 0.6 * quiet
  }

  /**
    * What this week does to demand between two airports, as a multiplier.
    *
    * Exactly 1.0 when seasons are switched off, and cheap enough to ask for
    * every pair of airports in the world several times a week.
    */
  def demandMultiplier(from : Airport, to : Airport, passengerType : PassengerType.Value) : Double = {
    val amplitude = GameConfig.seasonStrengthPercent / 100.0
    if (amplitude <= 0) {
      1.0
    } else {
      val week = weekOfYear
      passengerType match {
        case PassengerType.BUSINESS =>
          businessFactor(week, amplitude)
        case PassengerType.OLYMPICS =>
          1.0 //the Olympics happen when they happen
        case _ =>
          //Holiday travel follows the destination: people go where the summer
          //is. The origin matters half as much - schools break up there.
          val atDestination = leisureFactor(week, to, amplitude)
          val atOrigin = leisureFactor(week, from, amplitude)
          (atDestination * 2 + atOrigin) / 3
      }
    }
  }
}
