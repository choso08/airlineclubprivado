package com.patson.data

import com.patson.model.{AirlineIncident, IncidentKind}

import scala.collection.mutable.ListBuffer

/**
  * Incidents, past and present.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops the world and builds it again.
  */
object AirlineIncidentSource {
  private[this] val TABLE = "airline_incident"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
          "airline INTEGER," +
          "kind VARCHAR(24)," +
          "strength INTEGER," +
          "start_cycle INTEGER," +
          "end_cycle INTEGER)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the airline incident table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  private[this] def read(resultSet : java.sql.ResultSet) : AirlineIncident = AirlineIncident(
    resultSet.getInt("airline"),
    scala.util.Try(IncidentKind.withName(resultSet.getString("kind"))).getOrElse(IncidentKind.MAINTENANCE),
    resultSet.getInt("strength"),
    resultSet.getInt("start_cycle"),
    resultSet.getInt("end_cycle"),
    resultSet.getInt("id"))

  def loadActive(cycle : Int) : List[AirlineIncident] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM " + TABLE + " WHERE start_cycle <= ? AND end_cycle > ?")
      statement.setInt(1, cycle)
      statement.setInt(2, cycle)
      val resultSet = statement.executeQuery()
      val incidents = ListBuffer[AirlineIncident]()
      while (resultSet.next()) {
        incidents += read(resultSet)
      }
      resultSet.close()
      statement.close()
      incidents.toList
    } finally {
      connection.close()
    }
  }

  def loadByAirline(airlineId : Int, sinceCycle : Int) : List[AirlineIncident] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT * FROM " + TABLE + " WHERE airline = ? AND end_cycle >= ? ORDER BY id DESC")
      statement.setInt(1, airlineId)
      statement.setInt(2, sinceCycle)
      val resultSet = statement.executeQuery()
      val incidents = ListBuffer[AirlineIncident]()
      while (resultSet.next()) {
        incidents += read(resultSet)
      }
      resultSet.close()
      statement.close()
      incidents.toList
    } finally {
      connection.close()
    }
  }

  def save(incident : AirlineIncident) : Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "INSERT INTO " + TABLE + "(airline, kind, strength, start_cycle, end_cycle) VALUES(?,?,?,?,?)",
        java.sql.Statement.RETURN_GENERATED_KEYS)
      statement.setInt(1, incident.airlineId)
      statement.setString(2, incident.kind.toString)
      statement.setInt(3, incident.strength)
      statement.setInt(4, incident.startCycle)
      statement.setInt(5, incident.endCycle)
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
