package com.patson.data

import com.patson.model.{WorldEvent, WorldEventKind}

import scala.collection.mutable.ListBuffer

/**
  * Where world events are kept.
  *
  * The table is created here rather than in Meta.createSchema, which drops
  * every table and rebuilds it - fine for a new world, useless for one people
  * are playing in.
  */
object WorldEventSource {
  private[this] val TABLE = "world_event"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
          "kind VARCHAR(32)," +
          "scope_country VARCHAR(4)," +
          "scope_airport INTEGER," +
          "scope_name VARCHAR(128)," +
          "strength INTEGER," +
          "start_cycle INTEGER," +
          "end_cycle INTEGER)")
      statement.execute()
      statement.close()
    } catch {
      //the world carrying on quietly is better than the game not starting
      case e : Throwable => println("Could not make sure the world event table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  private[this] def read(resultSet : java.sql.ResultSet) : Option[WorldEvent] = {
    try {
      Some(WorldEvent(
        WorldEventKind.withName(resultSet.getString("kind")),
        Option(resultSet.getString("scope_country")),
        { val id = resultSet.getInt("scope_airport"); if (resultSet.wasNull()) None else Some(id) },
        Option(resultSet.getString("scope_name")).getOrElse(""),
        resultSet.getInt("strength"),
        resultSet.getInt("start_cycle"),
        resultSet.getInt("end_cycle"),
        resultSet.getInt("id")))
    } catch {
      //a kind this version does not know about - ignore it rather than throw
      case _ : Throwable => None
    }
  }

  /** Everything that has not finished yet, newest first. */
  def loadActive(cycle : Int) : List[WorldEvent] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM " + TABLE + " WHERE start_cycle <= ? AND end_cycle > ? ORDER BY start_cycle DESC")
      statement.setInt(1, cycle)
      statement.setInt(2, cycle)
      val resultSet = statement.executeQuery()
      val events = ListBuffer[WorldEvent]()
      while (resultSet.next()) {
        read(resultSet).foreach(events += _)
      }
      resultSet.close()
      statement.close()
      events.toList
    } finally {
      connection.close()
    }
  }

  /** The recent past as well, for the panel - so a player who logs in after
    * something ended can still see what they missed. */
  def loadRecent(sinceCycle : Int) : List[WorldEvent] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM " + TABLE + " WHERE end_cycle > ? ORDER BY start_cycle DESC")
      statement.setInt(1, sinceCycle)
      val resultSet = statement.executeQuery()
      val events = ListBuffer[WorldEvent]()
      while (resultSet.next()) {
        read(resultSet).foreach(events += _)
      }
      resultSet.close()
      statement.close()
      events.toList
    } finally {
      connection.close()
    }
  }

  def save(event : WorldEvent) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "INSERT INTO " + TABLE + "(kind, scope_country, scope_airport, scope_name, strength, start_cycle, end_cycle) VALUES(?,?,?,?,?,?,?)")
      statement.setString(1, event.kind.toString)
      event.scopeCountry match {
        case Some(code) => statement.setString(2, code)
        case None => statement.setNull(2, java.sql.Types.VARCHAR)
      }
      event.scopeAirportId match {
        case Some(id) => statement.setInt(3, id)
        case None => statement.setNull(3, java.sql.Types.INTEGER)
      }
      statement.setString(4, event.scopeName)
      statement.setInt(5, event.strengthPercent)
      statement.setInt(6, event.startCycle)
      statement.setInt(7, event.endCycle)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  def deleteBefore(cycle : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE end_cycle < ?")
      statement.setInt(1, cycle)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }
}
