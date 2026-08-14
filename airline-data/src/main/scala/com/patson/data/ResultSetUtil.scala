package com.patson.data

import java.math.{BigDecimal, RoundingMode}
import java.sql.{PreparedStatement, ResultSet}

/**
  * Reading money out of the database.
  *
  * Several money columns are stored as text rather than as numbers - balance,
  * transaction amounts, the income tables. That is upstream's schema and not
  * something worth migrating a live game for, but it has a sharp edge:
  * whatever string happens to be in the column is what the driver has to turn
  * into a number.
  *
  * saveTransaction used to bind its amount with setDouble, so those rows hold
  * "360000.0" rather than "360000". Connector/J 5.1 quietly truncated that and
  * nobody noticed for years. Every driver written since refuses it:
  *
  *   java.sql.SQLDataException: value '360000.0' cannot be decoded as Long
  *
  * which is what took the game down the night the driver was changed. The
  * writer is fixed, but rows already in the database are not, so reading has
  * to cope with both.
  */
object ResultSetUtil {

  /**
    * Bind one value into a query, translating what JDBC will not accept.
    *
    * The whole data layer is built on generic criteria - loadXByCriteria takes
    * a list of (column, value) and binds each with setObject - so anything the
    * game can put in a criteria list eventually reaches a driver. A Scala
    * Enumeration value is exactly such a thing:
    *
    *   CountrySource.loadCountryAirlineTitlesByCriteria(
    *     List(("airline", airline.id), ("title", Title.NATIONAL_AIRLINE)))
    *
    * Connector/J 5.1 accepted that and quietly called toString on it. The
    * MariaDB driver refuses:
    *
    *   java.sql.SQLException: Type scala.Enumeration$Val not supported type
    *
    * which threw inside the websocket actor and killed the connection between
    * the page and the game every two seconds. Enumerations are stored by their
    * id - they are read back with Title(resultSet.getInt("title")) - so that is
    * what goes down the wire.
    *
    * Everything else is passed through untouched.
    */
  def bind(statement : PreparedStatement, index : Int, value : Any) : Unit = {
    value match {
      case enumValue : Enumeration#Value => statement.setInt(index, enumValue.id)
      case other => statement.setObject(index, other)
    }
  }

  /** A money column stored as text, read as a whole number. Copes with
    * "360000", "360000.0" and "3.6E5" alike, and with an empty column. */
  def money(resultSet : ResultSet, column : String) : Long = {
    val raw = resultSet.getString(column)
    if (raw == null) {
      0L
    } else {
      val trimmed = raw.trim
      if (trimmed.isEmpty) {
        0L
      } else {
        try {
          trimmed.toLong
        } catch {
          case _ : NumberFormatException =>
            new BigDecimal(trimmed).setScale(0, RoundingMode.HALF_UP).longValue()
        }
      }
    }
  }
}
