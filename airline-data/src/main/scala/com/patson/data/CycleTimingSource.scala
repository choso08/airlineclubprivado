package com.patson.data

/**
  * When the last week finished, and how long it took.
  *
  * The clock on the page needs three things: which week it is, how far into it
  * we are, and how long a week takes. Only the first was ever written down -
  * the other two lived in the simulation's memory and were asked for over the
  * link between the two processes.
  *
  * That link is the whole problem. When it is down, or when the simulation has
  * just restarted and the web site is still holding a dead address, nothing
  * answers, and a page opens with no clock at all and no aircraft moving on
  * the map - which is exactly what was reported, twice.
  *
  * Written down here instead, the web site can answer the question by itself,
  * from the database both halves already share. The link becomes an
  * improvement rather than a requirement.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops the world and builds it again.
  */
object CycleTimingSource {
  private[this] val TABLE = "cycle_timing"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "id INTEGER PRIMARY KEY," +
          "cycle INTEGER," +
          "ended_at BIGINT," +
          "duration_millis BIGINT)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the cycle timing table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  case class CycleTiming(cycle : Int, endedAt : Long, durationMillis : Long)

  /** Note that a week has just finished. One row, always replaced. */
  def save(cycle : Int, endedAt : Long, durationMillis : Long) : Unit = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "REPLACE INTO " + TABLE + "(id, cycle, ended_at, duration_millis) VALUES(1,?,?,?)")
      statement.setInt(1, cycle)
      statement.setLong(2, endedAt)
      statement.setLong(3, durationMillis)
      statement.executeUpdate()
      statement.close()
    } finally {
      connection.close()
    }
  }

  def load() : Option[CycleTiming] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE id = 1")
      val resultSet = statement.executeQuery()
      val timing =
        if (resultSet.next()) Some(CycleTiming(resultSet.getInt("cycle"), resultSet.getLong("ended_at"), resultSet.getLong("duration_millis")))
        else None
      resultSet.close()
      statement.close()
      timing
    } finally {
      connection.close()
    }
  }
}
