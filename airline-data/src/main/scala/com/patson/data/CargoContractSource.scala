package com.patson.data

import com.patson.model.{CargoContract, CargoContractStatus}

import scala.collection.mutable.ListBuffer

/**
  * Cargo contracts, offered and running.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops the world and builds it again.
  */
object CargoContractSource {
  private[this] val TABLE = "cargo_contract"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "id INTEGER PRIMARY KEY AUTO_INCREMENT," +
          "airline INTEGER," +
          "from_airport INTEGER," +
          "to_airport INTEGER," +
          "tonnes INTEGER," +
          "price BIGINT," +
          "weeks INTEGER," +
          "start_cycle INTEGER," +
          "end_cycle INTEGER," +
          "status VARCHAR(16)," +
          "missed_weeks INTEGER)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the cargo contract table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  private[this] def read(resultSet : java.sql.ResultSet) : CargoContract = CargoContract(
    resultSet.getInt("airline"),
    resultSet.getInt("from_airport"),
    resultSet.getInt("to_airport"),
    resultSet.getInt("tonnes"),
    resultSet.getLong("price"),
    resultSet.getInt("weeks"),
    resultSet.getInt("start_cycle"),
    resultSet.getInt("end_cycle"),
    scala.util.Try(CargoContractStatus.withName(resultSet.getString("status"))).getOrElse(CargoContractStatus.EXPIRED),
    resultSet.getInt("missed_weeks"),
    resultSet.getInt("id"))

  private[this] def loadByQuery(where : String, parameters : List[Any]) : List[CargoContract] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " " + where)
      parameters.zipWithIndex.foreach {
        case (value : Int, index) => statement.setInt(index + 1, value)
        case (value : String, index) => statement.setString(index + 1, value)
        case (value, index) => statement.setObject(index + 1, value)
      }
      val resultSet = statement.executeQuery()
      val contracts = ListBuffer[CargoContract]()
      while (resultSet.next()) {
        contracts += read(resultSet)
      }
      resultSet.close()
      statement.close()
      contracts.toList
    } finally {
      connection.close()
    }
  }

  def loadByAirline(airlineId : Int) : List[CargoContract] =
    loadByQuery("WHERE airline = ? ORDER BY id DESC LIMIT 60", List(airlineId))

  def loadByStatus(status : CargoContractStatus.Value) : List[CargoContract] =
    loadByQuery("WHERE status = ?", List(status.toString))

  def loadById(id : Int) : Option[CargoContract] =
    loadByQuery("WHERE id = ?", List(id)).headOption

  def save(contract : CargoContract) : Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "INSERT INTO " + TABLE + "(airline, from_airport, to_airport, tonnes, price, weeks, start_cycle, end_cycle, status, missed_weeks) VALUES(?,?,?,?,?,?,?,?,?,?)",
        java.sql.Statement.RETURN_GENERATED_KEYS)
      statement.setInt(1, contract.airlineId)
      statement.setInt(2, contract.fromAirportId)
      statement.setInt(3, contract.toAirportId)
      statement.setInt(4, contract.tonnesPerWeek)
      statement.setLong(5, contract.pricePerWeek)
      statement.setInt(6, contract.weeks)
      statement.setInt(7, contract.startCycle)
      statement.setInt(8, contract.endCycle)
      statement.setString(9, contract.status.toString)
      statement.setInt(10, contract.missedWeeks)
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
    * Move a contract on, but only from the state it is supposed to be in.
    *
    * The check is inside the UPDATE rather than around it, so two people - or
    * a player and the simulation - cannot both act on the same offer.
    */
  def updateStatus(id : Int, from : CargoContractStatus.Value, to : CargoContractStatus.Value, startCycle : Int = -1, endCycle : Int = -1) : Boolean = {
    val connection = Meta.getConnection()
    try {
      val sql =
        if (startCycle >= 0) "UPDATE " + TABLE + " SET status = ?, start_cycle = ?, end_cycle = ? WHERE id = ? AND status = ?"
        else "UPDATE " + TABLE + " SET status = ? WHERE id = ? AND status = ?"
      val statement = connection.prepareStatement(sql)
      statement.setString(1, to.toString)
      if (startCycle >= 0) {
        statement.setInt(2, startCycle)
        statement.setInt(3, endCycle)
        statement.setInt(4, id)
        statement.setString(5, from.toString)
      } else {
        statement.setInt(2, id)
        statement.setString(3, from.toString)
      }
      val updated = statement.executeUpdate()
      statement.close()
      updated == 1
    } finally {
      connection.close()
    }
  }

  def setMissedWeeks(id : Int, missedWeeks : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("UPDATE " + TABLE + " SET missed_weeks = ? WHERE id = ?")
      statement.setInt(1, missedWeeks)
      statement.setInt(2, id)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  /** Let offers nobody answered lapse, and tidy up long-finished ones. */
  def expireOffersBefore(cycle : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "UPDATE " + TABLE + " SET status = ? WHERE status = ? AND start_cycle < ?")
      statement.setString(1, CargoContractStatus.EXPIRED.toString)
      statement.setString(2, CargoContractStatus.OFFERED.toString)
      statement.setInt(3, cycle)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  def deleteBefore(cycle : Int) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "DELETE FROM " + TABLE + " WHERE end_cycle < ? AND status <> ?")
      statement.setInt(1, cycle)
      statement.setString(2, CargoContractStatus.ACCEPTED.toString)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }
}
