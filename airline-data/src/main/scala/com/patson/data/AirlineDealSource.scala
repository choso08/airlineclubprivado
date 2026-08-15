package com.patson.data

import com.patson.model.{AirlineDeal, DealStatus}

import scala.collection.mutable.ListBuffer

/**
  * Offers between airlines.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops everything and rebuilds - fine for a new world, useless for one that
  * is being played in.
  */
object AirlineDealSource {
  private[this] val TABLE = "airline_deal"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
          "from_airline INTEGER," +
          "to_airline INTEGER," +
          "offered_cash BIGINT," +
          "requested_cash BIGINT," +
          "offered_airplanes VARCHAR(512)," +
          "note VARCHAR(256)," +
          "cycle INTEGER," +
          "status VARCHAR(16))")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the airline deal table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  private[this] def read(resultSet : java.sql.ResultSet) : AirlineDeal = {
    val airplanes = Option(resultSet.getString("offered_airplanes")).getOrElse("")
    AirlineDeal(
      resultSet.getInt("from_airline"),
      resultSet.getInt("to_airline"),
      resultSet.getLong("offered_cash"),
      resultSet.getLong("requested_cash"),
      if (airplanes.trim.isEmpty) List.empty else airplanes.split(",").toList.flatMap(part => scala.util.Try(part.trim.toInt).toOption),
      Option(resultSet.getString("note")).getOrElse(""),
      resultSet.getInt("cycle"),
      scala.util.Try(DealStatus.withName(resultSet.getString("status"))).getOrElse(DealStatus.EXPIRED),
      resultSet.getInt("id"))
  }

  /** Everything either side of this airline has going, newest first. */
  def loadByAirline(airlineId : Int) : List[AirlineDeal] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM " + TABLE + " WHERE from_airline = ? OR to_airline = ? ORDER BY id DESC LIMIT 60")
      statement.setInt(1, airlineId)
      statement.setInt(2, airlineId)
      val resultSet = statement.executeQuery()
      val deals = ListBuffer[AirlineDeal]()
      while (resultSet.next()) {
        deals += read(resultSet)
      }
      resultSet.close()
      statement.close()
      deals.toList
    } finally {
      connection.close()
    }
  }

  def loadById(id : Int) : Option[AirlineDeal] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE id = ?")
      statement.setInt(1, id)
      val resultSet = statement.executeQuery()
      val deal = if (resultSet.next()) Some(read(resultSet)) else None
      resultSet.close()
      statement.close()
      deal
    } finally {
      connection.close()
    }
  }

  def save(deal : AirlineDeal) : Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "INSERT INTO " + TABLE + "(from_airline, to_airline, offered_cash, requested_cash, offered_airplanes, note, cycle, status) VALUES(?,?,?,?,?,?,?,?)",
        java.sql.Statement.RETURN_GENERATED_KEYS)
      statement.setInt(1, deal.fromAirlineId)
      statement.setInt(2, deal.toAirlineId)
      statement.setLong(3, deal.offeredCash)
      statement.setLong(4, deal.requestedCash)
      statement.setString(5, deal.offeredAirplaneIds.mkString(","))
      statement.setString(6, deal.note)
      statement.setInt(7, deal.cycle)
      statement.setString(8, deal.status.toString)
      statement.executeUpdate()
      val keys = statement.getGeneratedKeys
      val id = if (keys.next()) keys.getInt(1) else 0
      keys.close()
      statement.close()
      id
    } finally {
      connection.close()
    }
  }

  /**
    * Move an offer out of PENDING, but only if it is still pending.
    *
    * The check is in the UPDATE rather than around it, so two people pressing
    * Accept and Withdraw at the same moment cannot both succeed - whoever gets
    * there second changes no rows and is told so.
    */
  def settle(id : Int, status : DealStatus.Value) : Boolean = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "UPDATE " + TABLE + " SET status = ? WHERE id = ? AND status = ?")
      statement.setString(1, status.toString)
      statement.setInt(2, id)
      statement.setString(3, DealStatus.PENDING.toString)
      val updated = statement.executeUpdate()
      statement.close()
      updated == 1
    } finally {
      connection.close()
    }
  }

  /** Let old offers lapse rather than sitting there for ever. */
  def expireBefore(cycle : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "UPDATE " + TABLE + " SET status = ? WHERE status = ? AND cycle < ?")
      statement.setString(1, DealStatus.EXPIRED.toString)
      statement.setString(2, DealStatus.PENDING.toString)
      statement.setInt(3, cycle)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }
}
