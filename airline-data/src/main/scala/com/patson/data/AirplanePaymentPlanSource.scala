package com.patson.data

import com.patson.model.{AirplanePaymentPlan, PaymentPlanKind}

import scala.collection.mutable.ListBuffer

/**
  * Which aircraft have not been paid for, and what is owed on them.
  *
  * A row here is the only difference between one of these and an aircraft that
  * was bought outright: condition, home, routes and maintenance all work
  * exactly the same. What the row buys is the weekly payment, and the three
  * things somebody who has not paid for an aircraft must not be able to do -
  * sell it, trade it away, or count it as something they own.
  *
  * The table is created on first use rather than in Meta.createSchema, which
  * drops the world and builds it again - fine for a new one, useless for a
  * world that is being played in.
  */
object AirplanePaymentPlanSource {
  private[this] val TABLE = "airplane_payment_plan"

  {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "CREATE TABLE IF NOT EXISTS " + TABLE + "(" +
          "airplane INTEGER PRIMARY KEY," +
          "airline INTEGER," +
          "kind VARCHAR(16)," +
          "weekly_payment BIGINT," +
          "weeks_remaining INTEGER," +
          "start_cycle INTEGER)")
      statement.execute()
      statement.close()
    } catch {
      case e : Throwable => println("Could not make sure the airplane payment plan table exists: " + e.getMessage)
    } finally {
      connection.close()
    }
  }

  //Which aircraft are not paid for, for the sake of the pages that draw a
  //fleet: that question is asked once per aircraft per page, and the answer
  //only changes when somebody signs, ends or finishes a plan.
  @volatile private[this] var plansByAirplaneId : Map[Int, AirplanePaymentPlan] = Map.empty
  @volatile private[this] var plansReadAt : Long = 0
  private[this] val CACHE_MILLIS = 5000

  def forgetCache() : Unit = plansReadAt = 0

  def all : Map[Int, AirplanePaymentPlan] = {
    val now = System.currentTimeMillis()
    if (now - plansReadAt > CACHE_MILLIS) {
      try {
        plansByAirplaneId = load(None)
        plansReadAt = now
      } catch {
        //An unreadable table must not stop a page from drawing; it only means
        //the aircraft are not marked for a moment.
        case e : Throwable => println("Could not read the airplane payment plans: " + e.getMessage)
      }
    }
    plansByAirplaneId
  }

  def get(airplaneId : Int) : Option[AirplanePaymentPlan] = all.get(airplaneId)

  def isFinanced(airplaneId : Int) : Boolean = all.contains(airplaneId)

  private[this] def read(resultSet : java.sql.ResultSet) : AirplanePaymentPlan = AirplanePaymentPlan(
    resultSet.getInt("airplane"),
    resultSet.getInt("airline"),
    scala.util.Try(PaymentPlanKind.withName(resultSet.getString("kind"))).getOrElse(PaymentPlanKind.LEASE),
    resultSet.getLong("weekly_payment"),
    resultSet.getInt("weeks_remaining"),
    resultSet.getInt("start_cycle"))

  private[this] def load(airlineId : Option[Int]) : Map[Int, AirplanePaymentPlan] = {
    val connection = Meta.getConnection()
    try {
      val statement = airlineId match {
        case Some(id) =>
          val withAirline = connection.prepareStatement("SELECT * FROM " + TABLE + " WHERE airline = ?")
          withAirline.setInt(1, id)
          withAirline
        case None => connection.prepareStatement("SELECT * FROM " + TABLE)
      }
      val resultSet = statement.executeQuery()
      val plans = scala.collection.mutable.Map[Int, AirplanePaymentPlan]()
      while (resultSet.next()) {
        val plan = read(resultSet)
        plans.put(plan.airplaneId, plan)
      }
      resultSet.close()
      statement.close()
      plans.toMap
    } finally {
      connection.close()
    }
  }

  def loadByAirline(airlineId : Int) : List[AirplanePaymentPlan] = load(Some(airlineId)).values.toList

  def save(plans : List[AirplanePaymentPlan]) : Unit = {
    if (plans.nonEmpty) {
      val connection = Meta.getConnection()
      try {
        val statement = connection.prepareStatement(
          "REPLACE INTO " + TABLE + "(airplane, airline, kind, weekly_payment, weeks_remaining, start_cycle) VALUES(?,?,?,?,?,?)")
        plans.foreach { plan =>
          statement.setInt(1, plan.airplaneId)
          statement.setInt(2, plan.airlineId)
          statement.setString(3, plan.kind.toString)
          statement.setLong(4, plan.weeklyPayment)
          statement.setInt(5, plan.weeksRemaining)
          statement.setInt(6, plan.startCycle)
          statement.addBatch()
        }
        statement.executeBatch()
        statement.close()
      } finally {
        connection.close()
      }
      forgetCache()
    }
  }

  def delete(airplaneIds : List[Int]) : Unit = {
    if (airplaneIds.nonEmpty) {
      val connection = Meta.getConnection()
      try {
        val statement = connection.prepareStatement("DELETE FROM " + TABLE + " WHERE airplane = ?")
        airplaneIds.foreach { airplaneId =>
          statement.setInt(1, airplaneId)
          statement.addBatch()
        }
        statement.executeBatch()
        statement.close()
      } finally {
        connection.close()
      }
      forgetCache()
    }
  }

  /**
    * Every plan whose aircraft still exists.
    *
    * Joined against the aircraft rather than read on its own, so that a plan
    * whose aircraft is gone - an airline that started again, a world tidied up
    * by hand - cannot go on charging somebody for an aircraft that no longer
    * exists.
    */
  def loadLive() : List[AirplanePaymentPlan] = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "SELECT p.* FROM " + TABLE + " p JOIN airplane a ON a.id = p.airplane")
      val resultSet = statement.executeQuery()
      val plans = ListBuffer[AirplanePaymentPlan]()
      while (resultSet.next()) {
        plans += read(resultSet)
      }
      resultSet.close()
      statement.close()
      plans.toList
    } finally {
      connection.close()
    }
  }

  /** Count one week off every instalment plan. */
  def countDown(plans : List[AirplanePaymentPlan]) : Unit = {
    save(plans.map(plan => plan.copy(weeksRemaining = plan.weeksRemaining - 1)))
  }

  /** Throw away plans whose aircraft no longer exists. */
  def deleteOrphans() : Int = {
    val connection = Meta.getConnection()
    try {
      val statement = connection.prepareStatement(
        "DELETE FROM " + TABLE + " WHERE airplane NOT IN (SELECT id FROM airplane)")
      val deleted = statement.executeUpdate()
      statement.close()
      if (deleted > 0) {
        forgetCache()
      }
      deleted
    } finally {
      connection.close()
    }
  }
}
