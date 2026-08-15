package com.patson.model

import com.typesafe.config.ConfigFactory

/**
 * Tunable game economy values.
 *
 * These are numbers upstream hardcodes. On a private instance they are the
 * things you actually want to play with, so they are read from configuration
 * instead - every one defaults to upstream's original value, so leaving them
 * unset changes nothing.
 *
 * Read once at startup: the simulation and the web front-end are separate
 * processes, so BOTH application.conf files need the same values, and both
 * have to be restarted for a change to take effect.
 *
 * Set them through game-settings.env rather than editing here.
 */
object GameConfig {
  private[this] val config = ConfigFactory.load()

  private def int(path: String, fallback: Int): Int =
    if (config.hasPath(path)) config.getInt(path) else fallback

  private def long(path: String, fallback: Long): Long =
    if (config.hasPath(path)) config.getLong(path) else fallback

  private def double(path: String, fallback: Double): Double =
    if (config.hasPath(path)) config.getDouble(path) else fallback

  private def boolean(path: String, fallback: Boolean): Boolean =
    if (config.hasPath(path)) config.getBoolean(path) else fallback

  /** Yearly interest the bank starts at. The simulation then drifts it up and
    * down over time, within bounds derived from this. Upstream: 0.12 (12%). */
  val loanInterestRate: Double = double("economy.loanInterestRate", 0.12)

  /** Largest single loan an airline can take. Upstream: 500,000,000. */
  val maxLoanAmount: Long = long("economy.maxLoanAmount", 500000000L)

  /** How many loans an airline may hold at once. Upstream: 10. */
  val maxLoans: Int = int("economy.maxLoans", 10)

  /** Fuel price the market drifts around, per unit. Raising it squeezes every
    * airline's margins; lowering it makes long routes cheap. Upstream: 70. */
  val fuelPrice: Double = double("economy.fuelPrice", 70)

  /** How much faster delegates gain their levels than upstream. A delegate
    * assigned to a country levels up after 4 weeks, then a year, then three
    * years, then ten. Ten in-game years at five minute cycles is forty-three
    * hours of real time, which no one on a private server will ever see. */
  val delegateLevelSpeed: Int = int("rules.delegateLevelSpeed", 4)

  /** Airlines needed before an alliance counts as established rather than
    * merely forming - only an established alliance gets the code-sharing and
    * the bonuses. Upstream: 3, which on a server of five people means half of
    * everyone has to join one alliance before it does anything. */
  val allianceMinMembers: Int = int("rules.allianceMinMembers", 2)

  /** Weeks a delegate is tied up after negotiating a route, before it can be
    * sent anywhere else. Upstream: 12. */
  val delegateCooldownCycles: Int = int("rules.delegateCooldownCycles", 3)

  /** Reputation needed before an airline may upload its own logo or livery.
    * Upstream requires 40, which is a brake on a public server where anyone
    * can register and start posting images. Among friends it only means nobody
    * gets to decorate their airline for the first several hours. */
  val brandingMinReputation: Int = int("rules.brandingMinReputation", 0)

  /** Days an airline must wait between name changes. Upstream: 30, which is a
    * rule for a public server where a name is an identity other players are
    * owed some consistency in. Among friends it is just an obstacle, so it
    * defaults to none here. 0 removes the wait entirely. */
  val renameCooldownDays: Int = int("rules.renameCooldownDays", 0)

  /** Weeks before the same route may be negotiated again.
    *
    * Upstream: 6. At five minute cycles that is half an hour of real waiting
    * before you may touch a route you have just negotiated - which mostly
    * punishes experimenting, the thing a private game is for. */
  val negotiationCooldownCycles: Int = int("rules.negotiationCooldownCycles", 2)

  /** The largest discount an aircraft model gets for being unpopular.
    *
    * Upstream: 70, and it is a rule written for a server with thousands of
    * players. The discount grows as the number of that model flying in the
    * world falls short of a threshold - 300 for a small aircraft, 1000 for a
    * medium one. Five friends will never own three hundred of anything, so
    * every model in the game sits permanently at very nearly the maximum, and
    * aircraft cost a fraction of their list price.
    *
    * That also wrecks the income statement, because the game books an aircraft
    * at its list price and counts the discount as a capital gain: buying one
    * plane at 77% off reads as tens of millions of profit in a week the
    * airline actually spent money.
    *
    * 0 turns the mechanism off entirely. */
  val maxModelDiscountPercent: Int = int("rules.maxModelDiscountPercent", 70)

  /** Whether the climb and descent allowance scales with the aircraft.
    *
    * Upstream caps the first 300 km of any flight at 350 km/h, the next 400 at
    * 500 and the next 400 at 700. Those are fixed numbers, so on anything
    * shorter than about 700 km every aircraft faster than 700 km/h takes
    * exactly the same time: a turboprop and a regional jet fly a 500 km leg in
    * the same 75 minutes, and paying for the faster one buys nothing.
    *
    * True makes each cap the same fraction of that aircraft's own cruise
    * speed. An aircraft cruising at exactly 700 km/h is unaffected. */
  val proportionalClimb: Boolean = boolean("rules.proportionalClimb", false)

  /** Whether a route is bought outright instead of negotiated.
    *
    * Upstream sends delegates to negotiate, rolls for success, ties them up
    * for weeks and bars the route for weeks more if it fails. That is a lot of
    * waiting for five friends, and the waiting is the part nobody enjoys.
    *
    * True removes the negotiation entirely: no dice, no cooling off. The
    * difficulty becomes a price instead - the route costs its usual creation
    * cost plus negotiationFeePerPoint for each point of it, so a hard route is
    * still a hard route, in money rather than in weeks.
    *
    * Delegates are not left without a job: see delegateDiscountPercent. */
  val buyRoutes: Boolean = boolean("rules.buyRoutes", false)

  /** What one point of negotiation difficulty costs, when routes are bought. */
  val negotiationFeePerPoint: Long = long("rules.negotiationFeePerPoint", 2000000L)

  /** What one delegate takes off the price of opening a route, as a
    * percentage, when routes are bought rather than negotiated.
    *
    * With buyRoutes there is nothing left to negotiate, which left delegates
    * with no purpose at all - they still existed, still gained levels, and did
    * nothing. This gives them the job instead: send them along and the route
    * costs less. They stay tied up for delegateCooldownCycles weeks
    * afterwards, so every route is a choice about where the pool goes rather
    * than a button that is always worth pressing.
    *
    * 0 turns it off, and delegates go back to being decoration. */
  val delegateDiscountPercent: Int = int("rules.delegateDiscountPercent", 10)

  /** The most delegates can take off a route between them, as a percentage.
    * Without a ceiling a big enough pool would make routes free. */
  val maxDelegateDiscountPercent: Int = int("rules.maxDelegateDiscountPercent", 50)

  /** What building or upgrading a base costs, as a percentage of upstream's
    * price.
    *
    * Both this and baseUpkeepPercent scale a curve that grows by 1.7 for every
    * level, so the top levels are where the money actually is - halving them
    * is the difference between a second base being a project and being out of
    * reach on a server where nobody is grinding for hours a day. */
  val baseCostPercent: Int = int("economy.baseCostPercent", 100)

  /** What a base costs to keep every week, as a percentage of upstream's. */
  val baseUpkeepPercent: Int = int("economy.baseUpkeepPercent", 100)

  /** Base scale needed for a base specialization, as a percentage of
    * upstream's requirement.
    *
    * Upstream asks for scale 8 to 14. A scale 8 base costs a couple of hundred
    * million and 14 is out of any reasonable reach, so on a server of five
    * people the whole idea of a base that is good at something was written,
    * shipped, and never once seen. */
  val baseSpecializationScalePercent: Int = int("rules.baseSpecializationScalePercent", 100)

  /** What an airport asset costs to build, as a percentage of upstream's.
    *
    * Upstream prices these between 200 million and 2 billion - a sensible
    * late-game sink on a server where airlines run for years, and simply out
    * of reach otherwise. */
  val airportAssetCostPercent: Int = int("economy.airportAssetCostPercent", 100)

  /** What a lounge costs to build, as a percentage of upstream's 50 million
    * per level. */
  val loungeCostPercent: Int = int("economy.loungeCostPercent", 100)

  /** How many office staff a base supports, as a percentage of upstream's.
    *
    * This is what really limits how many routes an airline can run: every
    * route needs staff at the airport it leaves from, a base supports
    * 60 + 80 per level at a headquarters and 60 per level elsewhere, and going
    * over is charged as overtime every week for ever. So "I need a bigger
    * headquarters before I can open anything else" is the shape of the whole
    * mid-game.
    *
    * That is a fine rule for a server where people play for months. Raising
    * this lets a given headquarters carry proportionally more routes without
    * removing the idea that a bigger one carries more. */
  val officeStaffCapacityPercent: Int = int("rules.officeStaffCapacityPercent", 100)

  /** What going over that capacity costs, as a percentage of upstream's
    * overtime. 0 removes the penalty entirely - and with it any reason to
    * upgrade a headquarters at all. */
  val officeOvertimePercent: Int = int("rules.officeOvertimePercent", 100)

  /** Whether an aircraft can be had without paying for it all at once - by
    * renting it week by week, or by paying it off in instalments.
    *
    * Buying outright is otherwise the only way to get one, and an aircraft
    * costs more than anything else in the game: the first hour is spent
    * waiting for money, a route that turns out badly is paid for twice - once
    * by the route and again by selling the aircraft back at a loss - and a
    * route worth flying only in summer is not worth flying at all, because the
    * aircraft is yours all winter as well. */
  val aircraftFinancingEnabled: Boolean = boolean("rules.aircraftFinancingEnabled", false)

  /** A year of rent as a percentage of what the aircraft costs new.
    *
    * At 18 a lease pays for the aircraft roughly every six years, so keeping
    * one for a long time is dearer than buying it - which is the trade being
    * offered, and the reason to eventually buy. */
  val leaseAnnualRatePercent: Int = int("rules.leaseAnnualRatePercent", 18)

  /** Paid once when the lease is signed, as a percentage of the price, and not
    * given back. Without it a lease would be a free week of flying whenever
    * demand happened to be high. */
  val leaseDepositPercent: Int = int("rules.leaseDepositPercent", 10)

  /** Instalments: what has to be paid on the day, as a percentage of the
    * price. Higher than a lease deposit because this one ends with the
    * aircraft belonging to the airline. */
  val instalmentDepositPercent: Int = int("rules.instalmentDepositPercent", 25)

  /** Instalments: how many weeks of payments. 104 is two in-game years. */
  val instalmentWeeks: Int = int("rules.instalmentWeeks", 104)

  /** Instalments: how far below the bank's rate the interest sits, in points.
    *
    * It follows the bank rather than sitting at a number of its own, because
    * it is the same money market - and below it, because this is a loan
    * against the aircraft itself: miss the payments and the aircraft goes
    * back. The bank has no such comfort and charges for it. */
  val instalmentRateBelowBankPercent: Double = double("rules.instalmentRateBelowBankPercent", 3)

  /** How much passengers differ from one another in the price they will
    * accept, as a percentage. 0 restores the old behaviour.
    *
    * Every passenger has a price above which they do not fly, and that limit
    * was exactly the same for all of them - which is why there was a wall:
    * measured, at 1.2 times the suggested fare 4% of them still flew, and at
    * 1.3 times none did. Charging a little more did not cost you some
    * passengers, it cost you all of them, so the price box had a right answer
    * rather than a decision in it.
    *
    * With this on each group has its own limit, spread evenly this far either
    * side of the old one - either side, so that the average is unchanged and
    * the world does not simply become richer. Demand then slopes away instead
    * of falling off a cliff: at 40, 1.2 times the fare keeps 13% and 1.3 times
    * keeps 4%.
    *
    * Overcharging is not free even when they do fly: satisfaction is already
    * nil at that price, so loyalty at that airport falls week after week. */
  val priceElasticityPercent: Int = int("simulation.priceElasticityPercent", 0)

  /** How much the time of year moves demand, as a percentage. 0 is off.
    *
    * The game runs a calendar and then ignores it: demand in January is demand
    * in August, so a route to the Algarve is worth the same in the rain as in
    * the sun and choosing routes is a decision made once and never revisited.
    *
    * With seasons on, holiday travel follows the summer - which is a different
    * month in each hemisphere - and business travel dips when the offices
    * empty at Christmas and in August. At 30 a beach route swings roughly a
    * third either side of its average across the year. */
  val seasonStrengthPercent: Int = int("simulation.seasonStrengthPercent", 0)

  /** The most anything added to this fork may move demand or costs, as a
    * percentage, however many of them happen at once.
    *
    * Seasons, world events and everything else here are meant to give a week
    * its own character, not to decide it. A summer that doubles a route and a
    * winter that empties it is not a season, it is a lottery - and two of them
    * lining up in the same week would be worse still.
    *
    * So the whole lot is multiplied together and then held inside this band.
    * At 30 the very worst week this fork can produce is seven tenths of an
    * ordinary one, and the very best is thirteen tenths. */
  val maxAddedSwingPercent: Int = int("simulation.maxAddedSwingPercent", 30)

  /** Hold a multiplier inside that band. */
  def limitAddedSwing(multiplier : Double) : Double = {
    val swing = Math.max(0, maxAddedSwingPercent) / 100.0
    Math.max(1 - swing, Math.min(1 + swing, multiplier))
  }

  /** Seconds a week takes during the quiet hours. 0 keeps one pace all day.
    *
    * A private server runs all night for people who are asleep. At three
    * minutes a week that is a hundred and sixty weeks between going to bed and
    * waking up - three years of a world nobody watched. Slowing the night down
    * is the difference between coming back to your airline and coming back to
    * a stranger's. */
  val nightCycleSeconds: Int = int("simulation.nightCycleSeconds", 0)

  /** The quiet hours, on the server's own clock, from one and up to the other.
    * Crossing midnight is allowed: 23 to 10 is a night. */
  val nightFromHour: Int = int("simulation.nightFromHour", 0)
  val nightToHour: Int = int("simulation.nightToHour", 10)

  /** Whether the country a route leaves from taxes its profit.
    *
    * The game has countries with an income and a name and asks nothing of an
    * airline for being there, so one airport is as good as another once the
    * passengers are counted. A tax on the routes leaving a base makes where
    * you put your bases a question about the country too. */
  val taxEnabled: Boolean = boolean("simulation.taxEnabled", false)

  /** The rate a middling country charges, as a percentage of route profit. */
  val taxBasePercent: Double = double("simulation.taxBasePercent", 15)

  /** How far the rate moves either side of that with the country's income.
    * At 8 on a base of 15, the poorest countries charge 7% and the richest
    * 23%. */
  val taxIncomeSpreadPercent: Double = double("simulation.taxIncomeSpreadPercent", 8)

  /** Whether freight can be contracted for.
    *
    * An aircraft's hold flies empty every week of its life here, and the only
    * thing an airline can sell is a seat. Cargo arrives as contracts rather
    * than as money quietly added to every route, so that taking one is a
    * decision: hold space promised away on a route that now has to keep
    * flying, paid for steadily whatever the passengers do. */
  val cargoEnabled: Boolean = boolean("simulation.cargoEnabled", false)

  /** What a tonne of freight is worth over a kilometre. */
  val cargoRatePerTonneKm: Double = double("simulation.cargoRatePerTonneKm", 2.0)

  /** The chance each week, per airline, of another offer turning up, and how
    * many may sit on the table unanswered. */
  val cargoOfferChancePercent: Int = int("simulation.cargoOfferChancePercent", 25)
  val cargoMaxOffers: Int = int("simulation.cargoMaxOffers", 3)

  /** How long a contract runs, in weeks. */
  val cargoMinWeeks: Int = int("simulation.cargoMinWeeks", 8)
  val cargoMaxWeeks: Int = int("simulation.cargoMaxWeeks", 26)

  /** Whether things go wrong at one airline at a time.
    *
    * World events give everybody something to react to at once, but they
    * cannot single anybody out - and a game between five people needs that
    * too: a week where one of you is in trouble and the other four can see it.
    *
    * Not bad luck landing on whoever it feels like: the chance is worked out
    * from the state of the fleet, so new aircraft almost never have an
    * incident and worn out ones have them regularly. */
  val incidentsEnabled: Boolean = boolean("simulation.incidentsEnabled", false)

  /** The chance, per real airline per week, at an average fleet - as a
    * percentage. A new fleet is a third of this, a worn out one twice it. */
  val incidentChancePercent: Double = double("simulation.incidentChancePercent", 2)

  /** How long a strike or an inspection lasts, in weeks. A breakdown is always
    * a single week - it is the repair bill that hurts, not a spell. */
  val incidentMinWeeks: Int = int("simulation.incidentMinWeeks", 2)
  val incidentMaxWeeks: Int = int("simulation.incidentMaxWeeks", 5)

  /** How hard it hits, as a percentage: how much likelier delays and
    * cancellations become, and how much of an aircraft's value a repair
    * costs. */
  val incidentMinStrengthPercent: Int = int("simulation.incidentMinStrengthPercent", 100)
  val incidentMaxStrengthPercent: Int = int("simulation.incidentMaxStrengthPercent", 300)

  /** Whether things happen to the world now and again.
    *
    * The game's event system is used for exactly one thing - the Olympics,
    * once every four in-game years - so between those, nothing ever happens
    * that anybody has to react to. The world on week 300 is the world on week
    * 3, and the only news is what the players did to each other.
    *
    * True lets a fuel crisis, a tourism boom, a slump, or an airport in
    * trouble turn up now and then, each for a few weeks, each announced to
    * everybody. */
  val worldEventsEnabled: Boolean = boolean("simulation.worldEventsEnabled", false)

  /** Roughly how many weeks between events. One is drawn with this chance each
    * week, and only ever one at a time. */
  val worldEventEveryWeeks: Int = int("simulation.worldEventEveryWeeks", 25)

  /** How long one lasts, in weeks. */
  val worldEventMinWeeks: Int = int("simulation.worldEventMinWeeks", 4)
  val worldEventMaxWeeks: Int = int("simulation.worldEventMaxWeeks", 10)

  /** How hard it hits, as a percentage. */
  val worldEventMinStrengthPercent: Int = int("simulation.worldEventMinStrengthPercent", 15)
  val worldEventMaxStrengthPercent: Int = int("simulation.worldEventMaxStrengthPercent", 40)

  /** Whether the game slows down when nobody is playing.
    *
    * A private server runs all week for people who play on some evenings. Left
    * alone it advances a week every few minutes regardless, so friends who
    * miss two days come back to a world that has moved months without them -
    * which is how a game between five people quietly turns into a game between
    * whoever logged in most.
    *
    * True makes the simulation rest while nobody is around: it still wakes on
    * schedule, but only actually plays out one week in holidaySlowdown. The
    * moment anybody logs in it is back to full speed on the next tick. */
  val holidayMode: Boolean = boolean("simulation.holidayMode", false)

  /** How long with nobody playing before the game starts resting. */
  val holidayIdleMinutes: Int = int("simulation.holidayIdleMinutes", 30)

  /** How much slower it runs while resting: 4 means one week in four. */
  val holidaySlowdown: Int = int("simulation.holidaySlowdown", 4)

  /** Whether applying to an alliance joins it outright.
    *
    * Upstream makes every application wait for a leader to approve it, which
    * is a rule for a public server full of strangers. Among friends it means
    * somebody has to be logged in and looking at the right panel at the right
    * moment, and if the alliance has lost its leader nobody can ever approve
    * anything - so applications sit there for ever with nothing to say why. */
  val allianceAutoAccept: Boolean = boolean("rules.allianceAutoAccept", false)
}
