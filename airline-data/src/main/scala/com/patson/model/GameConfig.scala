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

  /** Weeks a delegate is tied up after negotiating a route, before it can be
    * sent anywhere else. Upstream: 12. */
  val delegateCooldownCycles: Int = int("rules.delegateCooldownCycles", 6)

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
}
