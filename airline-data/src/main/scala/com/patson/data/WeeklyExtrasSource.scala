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
          "airline INTEGER," +
          "cycle INTEGER," +
          "aircraft_payments BIGINT," +
          "cargo BIGINT," +
          "tax BIGINT," +
          "tax_credit_used BIGINT," +
          "subsidy BIGINT DEFAULT 0," +
          "PRIMARY KEY (airline, cycle))")
      statement.execute()
      statement.close()

      //An earlier version of this table kept one row per airline. Widening the
      //key to include the week is what lets a month or a year be added up.
      //Nothing here is worth keeping - it is all derived - so a failure is
      //ignored rather than handled.
      try {
        val widen = connection.prepareStatement("ALTER TABLE " + TABLE + " DROP PRIMARY KEY, ADD PRIMARY KEY (airline, cycle)")
        widen.execute()
        widen.close()
      } catch {
        case _ : Throwable => //already the right shape
      }
      try {
        val addSubsidy = connection.prepareStatement("ALTER TABLE " + TABLE + " ADD COLUMN subsidy BIGINT DEFAULT 0")
        addSubsidy.execute()
        addSubsidy.close()
      } catch {
        case _ : Throwable => //already there
      }
    } catch {
      case e : Throwable => println("Could not make sure the weekly extras table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  case class WeeklyExtras(cycle : Int, aircraftPayments : Long, cargo : Long, tax : Long, taxCreditUsed : Long, subsidy : Long = 0)

  def save(airlineId : Int, extras : WeeklyExtras) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "REPLACE INTO " + TABLE + "(airline, cycle, aircraft_payments, cargo, tax, tax_credit_used, subsidy) VALUES(?,?,?,?,?,?,?)")
      statement.setInt(1, airlineId)
      statement.setInt(2, extras.cycle)
      statement.setLong(3, extras.aircraftPayments)
      statement.setLong(4, extras.cargo)
      statement.setLong(5, extras.tax)
      statement.setLong(6, extras.taxCreditUsed)
      statement.setLong(7, extras.subsidy)
      statement.executeUpdate()
      statement.close()
    } catch {
      //a missing note is not worth failing a cycle for
      case e : Throwable => println("Could not write the weekly extras: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  /** Every week recorded for this airline, newest first. */
  def loadByAirline(airlineId : Int, sinceCycle : Int) : List[WeeklyExtras] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE airline = ? AND cycle >= ? ORDER BY cycle DESC")
      statement.setInt(1, airlineId)
      statement.setInt(2, sinceCycle)
      val resultSet = statement.executeQuery()
      val weeks = scala.collection.mutable.ListBuffer[WeeklyExtras]()
      while (resultSet.next()) {
        weeks += WeeklyExtras(resultSet.getInt("cycle"), resultSet.getLong("aircraft_payments"),
          resultSet.getLong("cargo"), resultSet.getLong("tax"), resultSet.getLong("tax_credit_used"), resultSet.getLong("subsidy"))
      }
      resultSet.close()
      statement.close()
      weeks.toList
    } catch {
      case _ : Throwable => List.empty
    } finally {
      connection.close()
    }
  }

  /** Throw away weeks nobody will look at again. */
  def deleteBefore(cycle : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE cycle < ?")
      statement.setInt(1, cycle)
      statement.executeUpdate()
      statement.close()
    } catch {
      case _ : Throwable => //nothing worth failing a cycle for
    } finally {
      connection.close()
    }
  }

  def load(airlineId : Int) : Option[WeeklyExtras] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE airline = ? ORDER BY cycle DESC LIMIT 1")
      statement.setInt(1, airlineId)
      val resultSet = statement.executeQuery()
      val extras =
        if (resultSet.next()) Some(WeeklyExtras(resultSet.getInt("cycle"), resultSet.getLong("aircraft_payments"),
          resultSet.getLong("cargo"), resultSet.getLong("tax"), resultSet.getLong("tax_credit_used"), resultSet.getLong("subsidy")))
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
