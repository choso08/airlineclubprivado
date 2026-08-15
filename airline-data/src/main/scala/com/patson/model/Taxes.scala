package com.patson.model

/**
  * What a country takes from the routes flown out of it.
  *
  * The game has countries with an income, an openness and a name, and asks
  * nothing of an airline for being there. So one airport is as good as another
  * once the passengers are counted, and where an airline puts its bases is a
  * question about demand only.
  *
  * A tax on the profit of the routes leaving a base makes it a question about
  * the country as well. The rate follows the country's income, which is the
  * only measure of a country the game keeps: the rich ones charge more, and
  * flying out of a poorer one keeps more of what the route earns. That is a
  * real airline's calculation and it costs the game nothing new to know.
  *
  * Only profit is taxed, and only route by route: a route that loses money is
  * not taxed, and it does not shelter a route that does. Nothing is ever
  * refunded, because a government that pays airlines for losing money is a
  * different game.
  */
object Taxes {
  /**
    * The rate this country charges, as a percentage of route profit.
    *
    * Centred on the configured base rate at an average income, and moved by
    * the spread either side of it - up in a rich country, down in a poor one.
    */
  def ratePercent(country : Country) : Double = {
    if (!GameConfig.taxEnabled) {
      0
    } else {
      val low = Country.LOW_INCOME_THRESHOLD.toDouble
      val high = Country.HIGH_INCOME_THRESHOLD.toDouble
      //0 at the poorest, 1 at the richest, and everything between
      val position = Math.max(0.0, Math.min(1.0, (country.income - low) / (high - low)))
      val spread = GameConfig.taxIncomeSpreadPercent
      val rate = GameConfig.taxBasePercent - spread + position * spread * 2
      Math.max(0.0, rate)
    }
  }

  /** The VAT on something bought, which can be reclaimed against tax owed. */
  def reclaimableVat(amount : Long) : Long = {
    if (GameConfig.vatPercent <= 0 || amount <= 0) 0L
    else Math.round(amount * GameConfig.vatPercent / 100)
  }

  /** What is owed on a route that earned this much in a country at this rate. */
  def taxOnProfit(profit : Long, ratePercent : Double) : Long = {
    if (profit <= 0 || ratePercent <= 0) 0L
    else Math.round(profit * ratePercent / 100)
  }
}
