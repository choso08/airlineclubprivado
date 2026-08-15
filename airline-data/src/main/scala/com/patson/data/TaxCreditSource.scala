package com.patson.data

/**
  * VAT waiting to be reclaimed.
  *
  * A company in Europe pays VAT on an aircraft and then gets it back, against
  * what it owes on its profits. In a game that has just started taxing route
  * profit, that is the other half of the same rule: buying an aircraft, or
  * paying the weekly instalment on one, builds up a credit, and the credit
  * pays the tax until it runs out.
  *
  * It is the reason a company that has just invested pays no tax for a while,
  * and it is what makes buying an aircraft a decision about the tax bill and
  * not only about the cash.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops the world and builds it again.
  */
object TaxCreditSource {
  private[this] val TABLE = "airline_tax_credit"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(airline INTEGER PRIMARY KEY, credit BIGINT)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the tax credit table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  def get(airlineId : Int) : Long = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT credit FROM " + TABLE + " WHERE airline = ?")
      statement.setInt(1, airlineId)
      val resultSet = statement.executeQuery()
      val credit = if (resultSet.next()) resultSet.getLong("credit") else 0L
      resultSet.close()
      statement.close()
      credit
    } catch {
      case _ : Throwable => 0L
    } finally {
      connection.close()
    }
  }

  def all() : Map[Int, Long] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE)
      val resultSet = statement.executeQuery()
      val credits = scala.collection.mutable.Map[Int, Long]()
      while (resultSet.next()) {
        credits.put(resultSet.getInt("airline"), resultSet.getLong("credit"))
      }
      resultSet.close()
      statement.close()
      credits.toMap
    } finally {
      connection.close()
    }
  }

  /** Put more aside to reclaim. */
  def add(airlineId : Int, amount : Long) : Unit = {
    if (amount > 0) {
      set(airlineId, get(airlineId) + amount)
    }
  }

  def set(airlineId : Int, credit : Long) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "REPLACE INTO " + TABLE + "(airline, credit) VALUES(?,?)")
      statement.setInt(1, airlineId)
      statement.setLong(2, Math.max(0L, credit))
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }
}
