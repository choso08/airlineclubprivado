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

  /** Whether applying to an alliance joins it outright.
    *
    * Upstream makes every application wait for a leader to approve it, which
    * is a rule for a public server full of strangers. Among friends it means
    * somebody has to be logged in and looking at the right panel at the right
    * moment, and if the alliance has lost its leader nobody can ever approve
    * anything - so applications sit there for ever with nothing to say why. */
  val allianceAutoAccept: Boolean = boolean("rules.allianceAutoAccept", false)
}
