package com.patson.model

/**
  * An aircraft that has not been paid for in full, and what is owed on it
  * every week.
  *
  * Buying outright is otherwise the only way to get an aircraft, and an
  * aircraft costs more than anything else in the game. So the first hour is
  * spent waiting for money, a route that turns out badly is paid for twice -
  * once by the route and again by selling the aircraft back at a loss - and a
  * route worth flying only in summer is not worth flying at all, because the
  * aircraft is yours all winter as well.
  *
  * Two answers, one mechanism, because they are the same thing with different
  * numbers: a weekly payment against an aircraft that arrives today.
  *
  *   LEASE       small deposit, smaller weekly payment, forever, and it is
  *               never yours. Hand it back the week you stop needing it.
  *   INSTALMENTS bigger deposit, bigger weekly payment, for a fixed number of
  *               weeks - and when the last one is paid the aircraft is yours
  *               like any other.
  *
  * Until the last instalment is paid, both are somebody else's aircraft: they
  * cannot be sold, traded away, or counted as something the airline owns.
  */
case class AirplanePaymentPlan(airplaneId : Int,
                               airlineId : Int,
                               kind : PaymentPlanKind.Value,
                               weeklyPayment : Long,
                               weeksRemaining : Int, //-1 for a lease, which does not end
                               startCycle : Int) {

  val isLease : Boolean = kind == PaymentPlanKind.LEASE
}

object PaymentPlanKind extends Enumeration {
  type PaymentPlanKind = Value
  val LEASE, INSTALMENTS = Value
}

object AirplanePaymentPlan {
  /** A lease: what it costs every week to keep one. */
  def leaseWeeklyPayment(price : Int) : Long = {
    val perYear = price.toDouble * GameConfig.leaseAnnualRatePercent / 100
    Math.max(1L, Math.round(perYear / 52))
  }

  /** A lease: paid once when it is signed, and not given back. It is what
    * stops a lease being a free week of flying whenever demand happens to be
    * high. */
  def leaseDeposit(price : Int) : Long = Math.round(price.toDouble * GameConfig.leaseDepositPercent / 100)

  /** Instalments: what has to be found on the day. */
  def instalmentDeposit(price : Int) : Long = Math.round(price.toDouble * GameConfig.instalmentDepositPercent / 100)

  def instalmentWeeks : Int = Math.max(1, GameConfig.instalmentWeeks)

  /**
    * The yearly interest on instalments, as a percentage.
    *
    * It follows the bank's rate rather than sitting at a number of its own,
    * because it is the same money market - and it sits below it, because this
    * is a loan against the aircraft itself. Miss the payments and the aircraft
    * goes back; the bank has no such comfort, and charges for it.
    *
    * Fixed at the moment the plan is signed: the weekly payment is written
    * into the plan and never moves afterwards. What varies is the rate you are
    * offered, not the one you already took.
    */
  def instalmentAnnualRatePercent : Double = {
    val bankRate =
      try {
        com.patson.data.BankSource.loadLoanInterestRateByCycle(com.patson.data.CycleSource.loadCycle()) match {
          case Some(rate) => rate.annualRate.toDouble * 100
          case None => GameConfig.loanInterestRate * 100
        }
      } catch {
        case _ : Throwable => GameConfig.loanInterestRate * 100
      }
    //never free money, however low the bank goes
    Math.max(1.0, bankRate - GameConfig.instalmentRateBelowBankPercent)
  }

  /**
    * Instalments: the weekly payment on what is left after the deposit.
    *
    * The same arithmetic a bank does - equal payments that cover the interest
    * and clear the debt by the last week - so the total paid comes to a little
    * over the price of the aircraft rather than exactly it.
    */
  def instalmentWeeklyPayment(price : Int) : Long = {
    val borrowed = price - instalmentDeposit(price)
    val weeks = instalmentWeeks
    val weeklyRate = instalmentAnnualRatePercent / 100.0 / 52
    if (weeklyRate <= 0) {
      Math.max(1L, Math.round(borrowed.toDouble / weeks))
    } else {
      val growth = Math.pow(1 + weeklyRate, weeks)
      Math.max(1L, Math.ceil(borrowed * weeklyRate * growth / (growth - 1)).toLong)
    }
  }

  /** What the whole thing comes to, deposit included - the number worth
    * showing next to the price of buying it outright. */
  def instalmentTotal(price : Int) : Long = instalmentDeposit(price) + instalmentWeeklyPayment(price) * instalmentWeeks
}
