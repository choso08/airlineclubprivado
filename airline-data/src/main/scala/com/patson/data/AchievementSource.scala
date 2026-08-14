package com.patson.data

import com.patson.model.{Achievement, AchievementRecord}

import scala.collection.mutable.ListBuffer

/**
  * Who reached each milestone first, and when.
  *
  * The table is created here rather than in Meta.createSchema, which drops
  * every table and rebuilds it - fine for a new world, useless for one people
  * are playing in. This runs once per process, creates the table if it is not
  * there, and leaves an existing one alone.
  */
object AchievementSource {
  private[this] val TABLE = "achievement"

  //runs once, when this object is first touched in either half of the game
  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "achievement VARCHAR(64) PRIMARY KEY," +
          "airline INTEGER," +
          "cycle INTEGER)")
      statement.execute()
      statement.close()
    } catch {
      //never let this stop the game starting; the board is not worth that
      case e : Throwable => println("Could not make sure the achievement table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  /** Every milestone already claimed, in the order the catalogue lists them. */
  def loadAll() : List[AchievementRecord] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT a.achievement, a.airline, a.cycle, l.name FROM " + TABLE + " a " +
        "LEFT JOIN " + Constants.AIRLINE_TABLE + " l ON l.id = a.airline")
      val resultSet = statement.executeQuery()
      val records = ListBuffer[AchievementRecord]()
      while (resultSet.next()) {
        Achievement.byId.get(resultSet.getString("achievement")).foreach { achievement =>
          val name = Option(resultSet.getString("name")).getOrElse("a departed airline")
          records += AchievementRecord(achievement, resultSet.getInt("airline"), name, resultSet.getInt("cycle"))
        }
      }
      resultSet.close()
      statement.close()

      val claimedById = records.map(record => (record.achievement.id, record)).toMap
      Achievement.all.flatMap(achievement => claimedById.get(achievement.id))
    } finally {
      connection.close()
    }
  }

  /** The ids already claimed - all the simulation needs to know each week. */
  def loadClaimedIds() : Set[String] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT achievement FROM " + TABLE)
      val resultSet = statement.executeQuery()
      val ids = ListBuffer[String]()
      while (resultSet.next()) {
        ids += resultSet.getString("achievement")
      }
      resultSet.close()
      statement.close()
      ids.toSet
    } finally {
      connection.close()
    }
  }

  /**
    * Claim a milestone. The primary key does the deciding: whoever gets there
    * first keeps it, and a second claim is quietly ignored rather than
    * overwriting somebody else's moment.
    */
  def claim(achievementId : String, airlineId : Int, cycle : Int) : Boolean = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "INSERT IGNORE INTO " + TABLE + "(achievement, airline, cycle) VALUES(?, ?, ?)")
      statement.setString(1, achievementId)
      statement.setInt(2, airlineId)
      statement.setInt(3, cycle)
      val inserted = statement.executeUpdate()
      statement.close()
      inserted > 0
    } finally {
      connection.close()
    }
  }
}
