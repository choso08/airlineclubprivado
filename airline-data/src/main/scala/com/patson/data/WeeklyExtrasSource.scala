package com.patson.data

/**
  * What this fork's own mechanics cost or earned an airline each week.
  *
  * Aircraft payments, freight and country tax all land in the income
  * statement's asset line, because its columns are fixed in the database and
  * adding one would mean altering the table of every world that already
  * exists. That is safe, but it leaves one number standing for three things.
  *
  * So the three are also written down here, week by week, and the Payments
  * panel takes them apart again. The income statement stays exactly as it was;
  * what is inside it is no longer a mystery.
  *
  * The table is created on first use.
  */
object WeeklyExtrasSource {
  private[this] val TABLE = "airline_weekly_extras"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "airline INTEGER PRIMARY KEY," +
          "cycle INTEGER," +
          "aircraft_payments BIGINT," +
          "cargo BIGINT," +
          "tax BIGINT," +
          "tax_credit_used BIGINT)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the weekly extras table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  case class WeeklyExtras(cycle : Int, aircraftPayments : Long, cargo : Long, tax : Long, taxCreditUsed : Long)

  def save(airlineId : Int, extras : WeeklyExtras) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "REPLACE INTO " + TABLE + "(airline, cycle, aircraft_payments, cargo, tax, tax_credit_used) VALUES(?,?,?,?,?,?)")
      statement.setInt(1, airlineId)
      statement.setInt(2, extras.cycle)
      statement.setLong(3, extras.aircraftPayments)
      statement.setLong(4, extras.cargo)
      statement.setLong(5, extras.tax)
      statement.setLong(6, extras.taxCreditUsed)
      statement.executeUpdate()
      statement.close()
    } catch {
      //a missing note is not worth failing a cycle for
      case e : Throwable => println("Could not write the weekly extras: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  def load(airlineId : Int) : Option[WeeklyExtras] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE airline = ?")
      statement.setInt(1, airlineId)
      val resultSet = statement.executeQuery()
      val extras =
        if (resultSet.next()) Some(WeeklyExtras(resultSet.getInt("cycle"), resultSet.getLong("aircraft_payments"),
          resultSet.getLong("cargo"), resultSet.getLong("tax"), resultSet.getLong("tax_credit_used")))
        else None
      resultSet.close()
      statement.close()
      extras
    } catch {
      case _ : Throwable => None
    } finally {
      connection.close()
    }
  }
}
