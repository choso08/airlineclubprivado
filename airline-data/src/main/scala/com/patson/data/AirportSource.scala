package com.patson.data

import com.patson.data.Constants._
import com.patson.model.campaign.Campaign
import com.patson.model.{AirlineAppeal, _}
import com.patson.util.ChampionUtil.ReputationBonus
import com.patson.util.{AirlineCache, AirportCache, AirportChampionInfo}

import java.sql.{Connection, ResultSet, Statement, Types}
import scala.collection.mutable.ListBuffer
import scala.collection.{immutable, mutable}

object AirportSource {
  private[this] val BASE_QUERY = "SELECT * FROM airport"
  def loadAllAirports(fullLoad : Boolean = false, loadFeatures : Boolean = false) = {
      loadAirportsByCriteria(List.empty, fullLoad, loadFeatures)
  }
  
  def loadAirportsByIds(ids : List[Int], fullLoad : Boolean = false) = {
    if (ids.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder(BASE_QUERY + " where id IN (");
      for (i <- 0 until ids.size - 1) {
            queryString.append("?,")
      }
      
      queryString.append("?)")
      loadAirportsByQueryString(queryString.toString(), ids, fullLoad)
    }
  }
  
  def loadAirportsByCriteria(criteria : List[(String, Any)], fullLoad : Boolean = false, loadFeatures : Boolean = false) = {
      var queryString = BASE_QUERY
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      loadAirportsByQueryString(queryString, criteria.map(_._2), fullLoad, loadFeatures)
  }

  def getAirlineGlobalBonuses(): Map[Int, List[AirlineBonus]] = {
    AirlineSource.loadAirlineModifiers().filter(_._2.modifierType == AirlineModifierType.BANNER_LOYALTY_BOOST).map { //only 1 type for now
      case (airlineId, modifier) =>
        val airlineBonus = AirlineBonus(BonusType.BANNER,
          AirlineAppeal(loyalty = modifier.properties.getOrElse(AirlineModifierPropertyType.STRENGTH, 0L).toDouble), expirationCycle = modifier.expiryCycle)
        (airlineId, airlineBonus)
    }.groupMapReduce(_._1) {
      case(airlineId, airlineBonus) => List(airlineBonus)
    } {
      _:::_ //flatten everything by joining the sub-lists
    }
  }

  def getAirlineTitleBonuses(airport : Airport, countryAirlineTitleCache : mutable.HashMap[String, immutable.Map[Int, CountryAirlineTitle]]): Map[Int, List[AirlineBonus]] = {
    //get airport bonus //for now no db
    val airlineTitles: Map[Int, CountryAirlineTitle] = countryAirlineTitleCache.getOrElseUpdate(airport.countryCode, CountrySource.loadCountryAirlineTitlesByCountryCode(airport.countryCode).map(entry => (entry.airline.id, entry)).toMap)

    //map airline titles to bonus
    val bonusByAirlineId =  mutable.HashMap[Int, ListBuffer[AirlineBonus]]()

    airlineTitles.foreach {
      case(airlineId, countryAirlineTitle) =>
        val list = bonusByAirlineId.getOrElseUpdate(airlineId, ListBuffer())
        list.append(AirlineBonus(bonusType = CountryAirlineTitle.getBonusType(countryAirlineTitle.title), bonus = AirlineAppeal(countryAirlineTitle.loyaltyBonus), expirationCycle = None))
    }

    AirportSource.loadAirlineAppealBonusByAirport(airport.id).foreach {
      case (airline, bonusList) =>
        val list = bonusByAirlineId.getOrElseUpdate(airline.id, ListBuffer())
        list.appendAll(bonusList)
    }

    bonusByAirlineId.view.mapValues(_.toList).toMap
  }

  def getCampaignBonuses(airport : Airport, currentCycle : Int): Map[Int, List[AirlineBonus]] = {
    val campaigns: List[Campaign] = CampaignSource.loadCampaignsByAreaAirport(airport.id, true)

    val bonusByAirlineId = mutable.Map[Int, List[AirlineBonus]]()

    val busyDeletesByCampaign: Map[Campaign, List[BusyDelegate]] = DelegateSource.loadBusyDelegatesByCampaigns(campaigns)
    campaigns.groupBy(_.airline.id).foreach {
      case(airlineId, campaigns) => {
        val bonuses: List[AirlineBonus] = campaigns.map { campaign =>
          busyDeletesByCampaign.get(campaign).map { busyDelegates =>
            campaign.getAirlineBonus(airport, busyDelegates.map(_.assignedTask.asInstanceOf[CampaignDelegateTask]), currentCycle)
          }
        }.filter(_.isDefined).map(_.get)

        //if multiple campaigns, only use the one with the highest loyalty bonus
        if (bonuses.nonEmpty) {
          val bonus = bonuses.maxBy(_.bonus.loyalty)
          bonusByAirlineId.put(airlineId, List(bonus))
        }
      }
    }
    bonusByAirlineId.toMap
  }

  def saveAirlineAppealBonus(airportId : Int, airlineId : Int, bonus : AirlineBonus) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE + "(airport, airline, bonus_type, loyalty_bonus, awareness_bonus, expiration_cycle) VALUES(?,?,?,?,?,?)")
      preparedStatement.setInt(1, airportId)
      preparedStatement.setInt(2, airlineId)
      preparedStatement.setInt(3, bonus.bonusType.id)
      preparedStatement.setDouble(4, bonus.bonus.loyalty)
      preparedStatement.setDouble(5, 0)
      bonus.expirationCycle match {
        case Some(cycle) =>
          preparedStatement.setInt(6, cycle)
        case None =>
          preparedStatement.setNull(6, java.sql.Types.INTEGER)
      }

      preparedStatement.executeUpdate()
      preparedStatement.close()

      AirportCache.invalidateAirport(airportId)

    } finally {
      connection.close()
    }
  }

  def loadAirlineAppealBonusByAirportAndAirline(airportId : Int, airlineId : Int) : List[AirlineBonus] = {
    val result = loadAirlineAppealBonusByCriteria(List(("airport", airportId), ("airline", airlineId)))
    if (result.isEmpty) {
      List.empty
    } else {
      result.last._2.last._2
    }
  }

  def loadAirlineAppealBonusByAirport(airportId : Int) : Map[Airline, List[AirlineBonus]] = {
    val result = loadAirlineAppealBonusByCriteria(List(("airport", airportId)))
    if (result.isEmpty) {
      Map.empty
    } else {
      result.last._2
    }
  }

  def loadAirlineAppealBonusByCriteria(criteria : List[(String, Any)]): Map[Airport, Map[Airline, List[AirlineBonus]]] = {
    val connection = Meta.getConnection()
    try {
      var queryString = "SELECT * FROM " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      val preparedStatement = connection.prepareStatement(queryString)

      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }


      val resultSet = preparedStatement.executeQuery()

      val result = mutable.HashMap[Airport, mutable.HashMap[Airline, ListBuffer[AirlineBonus]]]()


      while (resultSet.next()) {
        val airportId = resultSet.getInt("airport")
        val airport = AirportCache.getAirport(airportId, false).getOrElse(Airport.fromId(airportId))
        val airlineId = resultSet.getInt("airline")
        val airline = AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId))

        val bonusList = result.getOrElseUpdate(airport, mutable.HashMap()).getOrElseUpdate(airline, ListBuffer())
        val expirationCycle = resultSet.getObject("expiration_cycle")
        bonusList.append(AirlineBonus(
          bonusType = BonusType(resultSet.getInt("bonus_type")),
          bonus = AirlineAppeal(loyalty = resultSet.getDouble("loyalty_bonus")),
          expirationCycle = if (expirationCycle == null) None else Some(expirationCycle.asInstanceOf[Int])))
      }
      resultSet.close()
      preparedStatement.close()

      result.view.mapValues { airlineAppealBonusMap =>
        airlineAppealBonusMap.view.mapValues(_.toList).toMap
      }.toMap
    } finally {
      connection.close()
    }
  }

  def deleteAirlineAppealBonus(airportId : Int, airlineId : Int, bonusType : BonusType.Value) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE + " where airport = ? AND airline = ? AND bonus_type = ?")

      preparedStatement.setInt(1, airportId)
      preparedStatement.setInt(2, airlineId)
      preparedStatement.setInt(3, bonusType.id)

      preparedStatement.executeUpdate()
      preparedStatement.close()

      AirportCache.invalidateAirport(airportId)
    } finally {
      connection.close()
    }
  }

  def purgeAirlineAppealBonus(atOrBeforeCycle : Int) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_AIRLINE_APPEAL_BONUS_TABLE + " where expiration_cycle IS NOT NULL AND expiration_cycle <= ?")

      preparedStatement.setInt(1, atOrBeforeCycle)

      preparedStatement.executeUpdate()
      preparedStatement.close()

    } finally {
      connection.close()
    }
  }




  /**
   * Batch loaders for the per-airport lookups that used to run inside the
   * airport loop.
   *
   * Each of these was one query per airport - roughly 3800 round trips each,
   * about a second apiece - for data that comes back in a single IN query.
   * Same approach as AirportAssetSource.loadAirportAssetsByAirports.
   */
  private[this] def batchByAirport[T](connection : Connection, table : String, airportIds : List[Int])(read : ResultSet => T) : Map[Int, List[T]] = {
    if (airportIds.isEmpty) {
      return Map.empty
    }
    val queryString = new StringBuilder(s"SELECT * FROM $table WHERE airport IN (")
    queryString.append(List.fill(airportIds.size)("?").mkString(","))
    queryString.append(")")

    val statement = connection.prepareStatement(queryString.toString)
    airportIds.zipWithIndex.foreach { case (id, index) => statement.setObject(index + 1, id) }

    try {
      val rs = statement.executeQuery()
      val grouped = mutable.HashMap[Int, ListBuffer[T]]()
      while (rs.next()) {
        val airportId = rs.getInt("airport")
        grouped.getOrElseUpdate(airportId, ListBuffer[T]()).append(read(rs))
      }
      rs.close()
      grouped.view.mapValues(_.toList).toMap
    } finally {
      statement.close()
    }
  }

  def loadAirportsByQueryString(queryString : String, parameters : List[Any], fullLoad : Boolean = false, loadFeatures : Boolean = false) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement(queryString)

      for (i <- 0 until parameters.size) {
        preparedStatement.setObject(i + 1, parameters(i))
      }


      val resultSet = preparedStatement.executeQuery()

      val airportData = new ListBuffer[Airport]()
      //val airlineMap : Map[Int, Airline] = AirlineSource.loadAllAirlines().foldLeft(Map[Int, Airline]())( (container, airline) => container + Tuple2(airline.id, airline))
      val countryAirlineTitleCache = mutable.HashMap[String, immutable.Map[Int, CountryAirlineTitle]]()
      val currentCycle = CycleSource.loadCycle()

      val airlineGlobalBonuses: Map[Int, List[AirlineBonus]] = //key is airline ID
        if (fullLoad) {
          getAirlineGlobalBonuses()
        } else {
          Map.empty
        }
      while (resultSet.next()) {
        val airport = Airport(
          resultSet.getString("iata"),
          resultSet.getString("icao"),
          resultSet.getString("name"),
          resultSet.getDouble("latitude"),
          resultSet.getDouble("longitude"),
          resultSet.getString("country_code"),
          resultSet.getString("city"),
          resultSet.getString("zone"),
          resultSet.getInt("airport_size"),
          resultSet.getInt("income"),
          resultSet.getLong("population"),
          runwayLength = resultSet.getInt("runway_length"))
        airport.id = resultSet.getInt("id")
        airportData += airport

        // Features, runways and bases are batched after this loop - see below.
        // They used to be one query per airport each.

        if (fullLoad) {
          // Assets are loaded for every airport at once, after this loop.
          // Doing it here cost three connection checkouts and three queries per
          // airport. Nothing between here and the end of the loop reads them -
          // initAssets only populates fields, and the appeal computation below
          // does not consult them - so deferring is safe.

          // Removed: a per-airport SELECT on airline_appeal whose result was
          // discarded - the only line in the loop body was already commented
          // out. Loyalty is computed further down from
          // initAirlineAppealsComputeLoyalty instead. That was one round trip
          // per airport, ~3800 per cycle, for nothing.

          // bases: batched after the loop


          // The rest of the per-airport work happens in a second pass below,
          // after the batched loads. It has to: features and bases must be in
          // place before the appeal computation reads them, and they can only
          // be fetched in bulk once every airport id is known.
        }
      }
      
      resultSet.close()
      preparedStatement.close()

      if (fullLoad || loadFeatures) {
        val airportIds = airportData.map(_.id).toList

        val featuresByAirport = com.patson.CycleProfiler.phase("  .. features (batched)") {
          batchByAirport(connection, AIRPORT_FEATURE_TABLE, airportIds) { rs =>
            AirportFeature(AirportFeatureType.withName(rs.getString("feature_type")), rs.getInt("strength"))
          }
        }
        airportData.foreach { airport =>
          airport.initFeatures(featuresByAirport.getOrElse(airport.id, List.empty))
        }
      }

      if (fullLoad) {
        val airportIds = airportData.map(_.id).toList
        // loadLoungesByCriteria wants a mutable Map, so give it one rather than
        // letting the implicit conversion fail at the call site.
        val airportsById = mutable.Map[Int, Airport]() ++ airportData.map(airport => (airport.id, airport))

        val assetsByAirport = com.patson.CycleProfiler.phase("  .. assets (batched)") {
          AirportAssetSource.loadAirportAssetsByAirports(airportData.toList, currentCycle)
        }

        val basesByAirport = com.patson.CycleProfiler.phase("  .. bases (batched)") {
          batchByAirport(connection, AIRLINE_BASE_TABLE, airportIds) { rs =>
            val airlineId = rs.getInt("airline")
            val airline = AirlineCache.getAirline(airlineId).getOrElse(Airline.fromId(airlineId))
            (rs.getInt("airport"), airline, rs.getString("country"), rs.getInt("scale"), rs.getInt("founded_cycle"), rs.getBoolean("headquarter"))
          }
        }

        val runwaysByAirport = com.patson.CycleProfiler.phase("  .. runways (batched)") {
          batchByAirport(connection, AIRPORT_RUNWAY_TABLE, airportIds) { rs =>
            Runway(rs.getInt("length"), rs.getString("code"), RunwayType.withName(rs.getString("runway_type")), rs.getBoolean("lighted"))
          }
        }

        val loyalistsByAirport = com.patson.CycleProfiler.phase("  .. loyalists (batched)") {
          LoyalistSource.loadLoyalistsByCriteria(List.empty).groupBy(_.airport.id)
        }

        val loungesByAirport = com.patson.CycleProfiler.phase("  .. lounges (batched)") {
          AirlineSource.loadLoungesByCriteria(List.empty, airportsById).groupBy(_.airport.id)
        }

        com.patson.CycleProfiler.phase("  .. apply + bonuses") {
          airportData.foreach { airport =>
            airport.initAssets(assetsByAirport.getOrElse(airport.id, List.empty))

            airport.initAirlineBases(basesByAirport.getOrElse(airport.id, List.empty).map {
              case (_, airline, countryCode, scale, foundedCycle, headquarter) =>
                AirlineBase(airline, airport, countryCode, scale, foundedCycle, headquarter)
            })

            val titleBonuses = getAirlineTitleBonuses(airport, countryAirlineTitleCache)
            val campaignBonuses = getCampaignBonuses(airport, currentCycle)
            val airlineBonusesMutable = mutable.Map[Int, ListBuffer[AirlineBonus]]()
            (titleBonuses.toList ++ campaignBonuses.toList ++ airlineGlobalBonuses.toList).foreach {
              case ((airlineId, bonuses)) =>
                airlineBonusesMutable.getOrElseUpdate(airlineId, ListBuffer[AirlineBonus]()).appendAll(bonuses)
            }
            airport.initAirlineAppealsComputeLoyalty(airlineBonusesMutable.view.mapValues(_.toList).toMap, loyalistsByAirport.getOrElse(airport.id, List.empty))

            airport.initLounges(loungesByAirport.getOrElse(airport.id, List.empty))
            airport.setRunways(runwaysByAirport.getOrElse(airport.id, List.empty))
            airport.shouldLoadCities = true //lazy load cities, which could be a lot of data
          }
        }
      }

      airportData.toList
    } finally {
      connection.close()
    }
      
  }

  def updateAirportBaseSpecializations(airportId : Int, airlineId : Int, airlineBaseSpecializationTypes : List[AirlineBaseSpecialization.Value]) = {
    val connection = Meta.getConnection()
    try {
      var purgeStatement = connection.prepareStatement(s"DELETE FROM $AIRLINE_BASE_SPECIALIZATION_TABLE WHERE airport = ? AND airline = ?")
      purgeStatement.setInt(1, airportId)
      purgeStatement.setInt(2, airlineId)
      purgeStatement.executeUpdate()
      purgeStatement.close()

      var preparedStatement = connection.prepareStatement(s"REPLACE INTO $AIRLINE_BASE_SPECIALIZATION_TABLE  (airport, airline, specialization_type) VALUES(?,?,?)")
      airlineBaseSpecializationTypes.foreach { airlineBaseSpecializationType =>
        preparedStatement.setInt(1, airportId)
        preparedStatement.setInt(2, airlineId)
        preparedStatement.setString(3, airlineBaseSpecializationType.toString)
        preparedStatement.executeUpdate()
      }

      preparedStatement.close()


      //in theory we should just use REPLACE, however, we made a mistake that update_cycle was included in the key too...so...we will just purge it before updating
      purgeStatement = connection.prepareStatement(s"DELETE FROM $AIRLINE_BASE_SPECIALIZATION_LAST_UPDATE_TABLE WHERE airport = ? AND airline = ?")
      purgeStatement.setInt(1, airportId)
      purgeStatement.setInt(2, airlineId)
      purgeStatement.executeUpdate()
      purgeStatement.close()

      preparedStatement = connection.prepareStatement(s"REPLACE INTO $AIRLINE_BASE_SPECIALIZATION_LAST_UPDATE_TABLE  (airport, airline, update_cycle) VALUES(?,?,?)")
      preparedStatement.setInt(1, airportId)
      preparedStatement.setInt(2, airlineId)
      preparedStatement.setInt(3, CycleSource.loadCycle())
      preparedStatement.executeUpdate()

      preparedStatement.close()

      AirportCache.invalidateAirport(airportId)
      AirlineCache.invalidateAirline(airlineId)
    } finally {
      connection.close()
    }
  }

  def loadAllAirportBaseSpecializations : List[(Airline, Airport, AirlineBaseSpecialization.Value)] = {
    val connection = Meta.getConnection()
    try {
      val queryString = s"SELECT * FROM $AIRLINE_BASE_SPECIALIZATION_TABLE"

      val preparedStatement = connection.prepareStatement(queryString)

      val resultSet = preparedStatement.executeQuery()

      val result = ListBuffer[(Airline, Airport, AirlineBaseSpecialization.Value)]()

      while (resultSet.next()) {
        val specialization = AirlineBaseSpecialization.withName(resultSet.getString("specialization_type"))
        result.append((AirlineCache.getAirline(resultSet.getInt("airline")).get, AirportCache.getAirport(resultSet.getInt("airport")).get, specialization))


      }
      resultSet.close()
      preparedStatement.close()

      result.toList
    } finally {
      connection.close()
    }
  }

  def loadAirportBaseSpecializations(airportId : Int, airlineId : Int) : List[AirlineBaseSpecialization.Value] = {
    val connection = Meta.getConnection()
    try {
      var queryString = s"SELECT * FROM $AIRLINE_BASE_SPECIALIZATION_TABLE WHERE airport = ? AND airline = ?"

      val preparedStatement = connection.prepareStatement(queryString)

      preparedStatement.setInt(1, airportId)
      preparedStatement.setInt(2, airlineId)


      val resultSet = preparedStatement.executeQuery()

      val result = ListBuffer[AirlineBaseSpecialization.Value]()

      while (resultSet.next()) {
        val specialization = AirlineBaseSpecialization.withName(resultSet.getString("specialization_type"))
        result.append(specialization)
      }
      resultSet.close()
      preparedStatement.close()

      result.toList
    } finally {
      connection.close()
    }
  }

  def loadAirportBaseSpecializationsLastUpdate(airportId : Int, airlineId : Int) : Option[Int] = {
    val connection = Meta.getConnection()
    try {
      var queryString = s"SELECT * FROM $AIRLINE_BASE_SPECIALIZATION_LAST_UPDATE_TABLE WHERE airport = ? AND airline = ?"

      val preparedStatement = connection.prepareStatement(queryString)

      preparedStatement.setInt(1, airportId)
      preparedStatement.setInt(2, airlineId)


      val resultSet = preparedStatement.executeQuery()

      val result =
        if (resultSet.next()) {
          Some(resultSet.getInt("update_cycle"))
        } else {
          None
        }
      resultSet.close()
      preparedStatement.close()

      result
    } finally {
      connection.close()
    }
  }

  
  def loadAirportById(id : Int, fullLoad : Boolean = false) = {
      val result = loadAirportsByCriteria(List(("id", id)), fullLoad)
      if (result.isEmpty) {
        None
      } else {
        Some(result(0))
      }
  }
  def loadAirportByIata(iata : String, fullLoad : Boolean = false) = {
      val result = loadAirportsByCriteria(List(("iata", iata)), fullLoad)
      if (result.isEmpty) {
        None
      } else {
        Some(result(0))
      }
  }
  def loadAirportsByCountry(countryCode : String) = {
    loadAirportsByCriteria(List(("country_code", countryCode)))
  }

  def updateAirlineAppeal(airportId : Int, airlineId : Int, airlineAppeal : AirlineAppeal) = {
   val connection = Meta.getConnection()
   try {  
     connection.setAutoCommit(false)
      if (airlineAppeal.loyalty == 0) {
        val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRLINE_APPEAL_TABLE + " WHERE airport = ? AND airline = ?")
        purgeStatement.setInt(1, airportId)
        purgeStatement.setInt(2, airlineId)
        purgeStatement.executeUpdate()
        purgeStatement.close()
      } else {
        val insertStatement = connection.prepareStatement("REPLACE INTO " + AIRLINE_APPEAL_TABLE + "(airport, airline, loyalty, awareness) VALUES (?,?,?,?)")
        insertStatement.setInt(1, airportId)
        insertStatement.setInt(2, airlineId)
        insertStatement.setDouble(3, airlineAppeal.loyalty)
        insertStatement.setDouble(4, 0)
        insertStatement.executeUpdate()
        insertStatement.close()
      }
     //AirportCache.invalidateAirport(airportId)
     connection.commit()
   } finally {
     connection.close()
   }
  }

  def deleteAirlineAppealsFromAllAirports(airlineId : Int) = {
    val connection = Meta.getConnection()
    try {
      val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRLINE_APPEAL_TABLE + " WHERE airline = ?")
      purgeStatement.setInt(1, airlineId)
      purgeStatement.executeUpdate()
    } finally {
      connection.close()
    }
  }

  def updateAirlineAppeals(airportId : Int, airlineAppeals : Map[Int, AirlineAppeal]) = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRLINE_APPEAL_TABLE + " WHERE airport = ? AND airline = ?")
      val insertStatement = connection.prepareStatement("REPLACE INTO " + AIRLINE_APPEAL_TABLE + "(airport, airline, loyalty, awareness) VALUES (?,?,?,?)")
      airlineAppeals.foreach {
        case (airlineId, airlineAppeal) =>
          if (airlineAppeal.loyalty == 0) {
            purgeStatement.setInt(1, airportId)
            purgeStatement.setInt(2, airlineId)
            purgeStatement.addBatch()
          } else {
            insertStatement.setInt(1, airportId)
            insertStatement.setInt(2, airlineId)
            insertStatement.setDouble(3, airlineAppeal.loyalty)
            insertStatement.setDouble(4, 0)
            insertStatement.addBatch()
          }
      }
      purgeStatement.executeBatch()
      insertStatement.executeBatch()
      purgeStatement.close()
      insertStatement.close()
      //AirportCache.invalidateAirport(airportId)

      connection.commit()
    } finally {
      connection.close()
    }
  }

  
  def saveAirports(airports : List[Airport]) = {
            Class.forName(DB_DRIVER);
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_TABLE + "(iata, icao, name, latitude, longitude, country_code, city, zone, airport_size, income, population, runway_length)  VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)
    
      connection.setAutoCommit(false)
      airports.foreach { 
        airport =>
          preparedStatement.setString(1, airport.iata)
          preparedStatement.setString(2, airport.icao)
          preparedStatement.setString(3, airport.name)
          preparedStatement.setDouble(4, airport.latitude)
          preparedStatement.setDouble(5, airport.longitude)
          preparedStatement.setString(6, airport.countryCode)
          preparedStatement.setString(7, airport.city)
          preparedStatement.setString(8, airport.zone)
          preparedStatement.setInt(9, airport.size)
          preparedStatement.setLong(10, airport.baseIncome)
          preparedStatement.setLong(11, airport.basePopulation)
          preparedStatement.setInt(12, airport.runwayLength)
          
          preparedStatement.executeUpdate()
          val generatedKeys = preparedStatement.getGeneratedKeys
          
          if (generatedKeys.next()) {
            val generatedId = generatedKeys.getInt(1)
            airport.id = generatedId
            
            //insert airline info too
            airport.citiesServed.foreach { 
              case (city, share) =>
              val infoStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_CITY_SHARE_TABLE + "(airport, city, share) VALUES(?,?,?)")
              infoStatement.setInt(1, airport.id)
              infoStatement.setInt(2, city.id)
              infoStatement.setDouble(3, share)
              infoStatement.executeUpdate()
              infoStatement.close()
            }
            //insert features
            airport.getFeatures().foreach { feature =>
              val featureStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_FEATURE_TABLE + "(airport, feature_type, strength) VALUES(?,?,?)")
              featureStatement.setInt(1, airport.id)
              featureStatement.setString(2, feature.featureType.toString())
              featureStatement.setInt(3, feature.strength)
              featureStatement.executeUpdate()
              featureStatement.close()
            }
            //insert runway
            airport.getRunways().foreach { runway =>
              val statement = connection.prepareStatement("INSERT INTO " + AIRPORT_RUNWAY_TABLE + "(airport, code, runway_type, length, lighted) VALUES(?,?,?,?,?)")
              statement.setInt(1, airport.id)
              statement.setString(2, runway.code)
              statement.setString(3, runway.runwayType.toString)
              statement.setInt(4, runway.length)
              statement.setBoolean(5, runway.lighted)
              statement.executeUpdate()
              statement.close()
            }
          }
      }
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }
  
  def fullUpdateAirports(airports : List[Airport]) = {
            Class.forName(DB_DRIVER);
    val connection = Meta.getConnection()

    try {
      val preparedStatement = connection.prepareStatement("UPDATE " + AIRPORT_TABLE + " SET airport_size = ?, income = ?, population = ?, name = ?, city = ?, runway_length = ?  WHERE id = ?")

      connection.setAutoCommit(false)


      airports.foreach {
        airport =>
          preparedStatement.setInt(1, airport.size)
          preparedStatement.setInt(2, airport.baseIncome)
          preparedStatement.setLong(3, airport.basePopulation)
          preparedStatement.setString(6, airport.name)
          preparedStatement.setString(7, airport.city)
          preparedStatement.setInt(8, airport.runwayLength)
          preparedStatement.setInt(9, airport.id)

          preparedStatement.addBatch()
          //preparedStatement.executeUpdate()


          val purgeCityShareStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_CITY_SHARE_TABLE + " WHERE airport = ?");
          purgeCityShareStatement.setInt(1, airport.id)
          purgeCityShareStatement.executeUpdate()
          purgeCityShareStatement.close()
          val purgeFeatureStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_FEATURE_TABLE + " WHERE airport = ?");
          purgeFeatureStatement.setInt(1, airport.id)
          purgeFeatureStatement.executeUpdate()
          purgeFeatureStatement.close()

          //update airline info too
          airport.citiesServed.foreach {
            case (city, share) =>
            val infoStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_CITY_SHARE_TABLE + "(airport, city, share) VALUES(?,?,?)")
            infoStatement.setInt(1, airport.id)
            infoStatement.setInt(2, city.id)
            infoStatement.setDouble(3, share)
            infoStatement.executeUpdate()
            infoStatement.close()
          }
          //insert features
          airport.getFeatures().foreach { feature =>
            val featureStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_FEATURE_TABLE + "(airport, feature_type, strength) VALUES(?,?,?)")
            featureStatement.setInt(1, airport.id)
            featureStatement.setString(2, feature.featureType.toString())
            featureStatement.setInt(3, feature.strength)
            featureStatement.executeUpdate()
            featureStatement.close()
          }

          val purgeRunwayStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_RUNWAY_TABLE + " WHERE airport = ?");
          purgeRunwayStatement.setInt(1, airport.id)
          purgeRunwayStatement.executeUpdate()
          purgeRunwayStatement.close()

          //insert runways
//          "airport INTEGER," +
//            "code VARCHAR(16)," +
//            "type SMALLINT," +
//            "length SMALLINT," +
          airport.getRunways().foreach { runway =>
            val statement = connection.prepareStatement("INSERT INTO " + AIRPORT_RUNWAY_TABLE + "(airport, code, runway_type, length, lighted) VALUES(?,?,?,?,?)")
            statement.setInt(1, airport.id)
            statement.setString(2, runway.code)
            statement.setString(3, runway.runwayType.toString)
            statement.setInt(4, runway.length)
            statement.setBoolean(5, runway.lighted)
            statement.executeUpdate()
            statement.close()
          }


          AirportCache.invalidateAirport(airport.id)
      }
      preparedStatement.executeBatch()
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def updateAirports(airports : List[Airport]) = {
    Class.forName(DB_DRIVER);
    val connection = Meta.getConnection()

    try {
      val preparedStatement = connection.prepareStatement("UPDATE " + AIRPORT_TABLE + " SET airport_size = ?, income = ?, population = ?, runway_length = ?  WHERE id = ?")

      connection.setAutoCommit(false)


      airports.foreach {
        airport =>
          if (airport.id != 0) {
            preparedStatement.setInt(1, airport.size)
            preparedStatement.setLong(2, airport.baseIncome)
            preparedStatement.setLong(3, airport.basePopulation)
            preparedStatement.setInt(4, airport.runwayLength)
            preparedStatement.setInt(5, airport.id)

            preparedStatement.addBatch()
            //preparedStatement.executeUpdate()


            val purgeCityShareStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_CITY_SHARE_TABLE + " WHERE airport = ?");
            purgeCityShareStatement.setInt(1, airport.id)
            purgeCityShareStatement.executeUpdate()
            purgeCityShareStatement.close()
            val purgeFeatureStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_FEATURE_TABLE + " WHERE airport = ?");
            purgeFeatureStatement.setInt(1, airport.id)
            purgeFeatureStatement.executeUpdate()
            purgeFeatureStatement.close()

            println(s"updating airport $airport")

            //update airline info too
            airport.citiesServed.foreach {
              case (city, share) =>
                val infoStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_CITY_SHARE_TABLE + "(airport, city, share) VALUES(?,?,?)")
                infoStatement.setInt(1, airport.id)
                infoStatement.setInt(2, city.id)
                infoStatement.setDouble(3, share)
                infoStatement.executeUpdate()
                infoStatement.close()
            }
            //insert features
            airport.getFeatures().foreach { feature =>
              val featureStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_FEATURE_TABLE + "(airport, feature_type, strength) VALUES(?,?,?)")
              featureStatement.setInt(1, airport.id)
              featureStatement.setString(2, feature.featureType.toString())
              featureStatement.setInt(3, feature.strength)
              featureStatement.executeUpdate()
              featureStatement.close()
            }

            val purgeRunwayStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_RUNWAY_TABLE + " WHERE airport = ?");
            purgeRunwayStatement.setInt(1, airport.id)
            purgeRunwayStatement.executeUpdate()
            purgeRunwayStatement.close()

            //insert runways
            //          "airport INTEGER," +
            //            "code VARCHAR(16)," +
            //            "type SMALLINT," +
            //            "length SMALLINT," +
            airport.getRunways().foreach { runway =>
              val statement = connection.prepareStatement("INSERT INTO " + AIRPORT_RUNWAY_TABLE + "(airport, code, runway_type, length, lighted) VALUES(?,?,?,?,?)")
              statement.setInt(1, airport.id)
              statement.setString(2, runway.code)
              statement.setString(3, runway.runwayType.toString)
              statement.setInt(4, runway.length)
              statement.setBoolean(5, runway.lighted)
              statement.executeUpdate()
              statement.close()
            }

            AirportCache.invalidateAirport(airport.id)
          }
      }
      preparedStatement.executeBatch()
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def updateAirportFeatures(airportId : Int, features : List[AirportFeature]) = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)

      val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_FEATURE_TABLE + " WHERE airport = ?")
      purgeStatement.setInt(1, airportId)
      purgeStatement.executeUpdate()
      purgeStatement.close()

      val featureStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_FEATURE_TABLE + "(airport, feature_type, strength) VALUES(?,?,?)")
      features.foreach { feature =>
        featureStatement.setInt(1, airportId)
        featureStatement.setString(2, feature.featureType.toString())
        featureStatement.setInt(3, feature.strength)
        featureStatement.executeUpdate()
      }
      featureStatement.close()
      AirportCache.invalidateAirport(airportId)

      connection.commit()
    } finally {
      connection.close()
    }
  }


  def saveAirportFeature(airportId : Int, feature : AirportFeature) = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)

      val featureStatement = connection.prepareStatement("REPLACE INTO " + AIRPORT_FEATURE_TABLE + "(airport, feature_type, strength) VALUES(?,?,?)")
      featureStatement.setInt(1, airportId)
      featureStatement.setString(2, feature.featureType.toString())
      featureStatement.setInt(3, feature.strength)
      featureStatement.executeUpdate()

      featureStatement.close()
      AirportCache.invalidateAirport(airportId)
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def deleteAirportFeature(airportId : Int, featureType : AirportFeatureType.Value) = {
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)

      val featureStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_FEATURE_TABLE + " WHERE airport = ? AND feature_type = ?")
      featureStatement.setInt(1, airportId)
      featureStatement.setString(2, featureType.toString())
      featureStatement.executeUpdate()

      featureStatement.close()
      AirportCache.invalidateAirport(airportId)
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def loadAirportFeatures(airportId : Int) = {
    val connection = Meta.getConnection()
    try {
      val featureStatement = connection.prepareStatement("SELECT * FROM " + AIRPORT_FEATURE_TABLE + " WHERE airport = ?")
      featureStatement.setInt(1, airportId)

      val featureResultSet = featureStatement.executeQuery()
      val features = ListBuffer[AirportFeature]()
      while (featureResultSet.next()) {
        val featureType = AirportFeatureType.withName(featureResultSet.getString("feature_type"))
        val strength = featureResultSet.getInt("strength")

        features += AirportFeature(featureType, strength)
      }
      featureResultSet.close()
      featureStatement.close()
      features.toList
    } finally {
      connection.close()
    }
  }


//  def updateAirportImages(airports : List[Airport]) = {
//    val connection = Meta.getConnection()
//    try {
//      connection.setAutoCommit(false)
//      airports.foreach { airport =>
//        val purgeStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_IMAGE_TABLE + " WHERE airport = ?")
//        purgeStatement.setInt(1, airport.id)
//        purgeStatement.executeUpdate()
//        purgeStatement.close()
//
//        val featureStatement = connection.prepareStatement("INSERT INTO " + AIRPORT_IMAGE_TABLE + "(airport, city_url, airport_url) VALUES(?,?,?)")
//        featureStatement.setInt(1, airport.id)
//        featureStatement.setString(2, airport.getCityImageUrl().getOrElse(null))
//        featureStatement.setString(3, airport.getAirportImageUrl().getOrElse(null))
//        featureStatement.executeUpdate()
//
//        featureStatement.close()
//
//        //AirportCache.invalidateAirport(airport.id)
//      }
//      connection.commit()
//    } finally {
//      connection.close()
//    }
//  }


  def deleteAirports(airportIds : List[Int]) = {
    //open the hsqldb
    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)

      val preparedStatement = connection.prepareStatement("DELETE FROM " + AIRPORT_TABLE + " WHERE id = ?")
      connection.setAutoCommit(false)
      airportIds.foreach { airportId =>
          preparedStatement.setInt(1, airportId)

          preparedStatement.executeUpdate()
          AirportCache.invalidateAirport(airportId)
      }

      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }
  
  def deleteAllAirports() = {
    //open the hsqldb
    val connection = Meta.getConnection()
    try {
      var queryString = "DELETE FROM " + AIRPORT_TABLE
      
      val preparedStatement = connection.prepareStatement(queryString)
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      println("Deleted " + deletedCount + " airport records")
      deletedCount
    } finally {
      connection.close()
    }
  }


  def loadAirportSharesOnCity(cityId : Int) : List[(Airport, Double)] = {
    CitySource.loadCityById(cityId) match {
      case Some(city) =>
          //open the hsqldb
        val connection = Meta.getConnection()
        try {  
          //var queryString = "SELECT * FROM " + AIRPORT_CITY_SHARE_TABLE + " c LEFT JOIN " + AIRPORT_TABLE + " a ON c.airport = a.id WHERE c.city = ?"
          val queryString = "SELECT * FROM " + AIRPORT_CITY_SHARE_TABLE + " WHERE city = ?"
          
          val preparedStatement = connection.prepareStatement(queryString)
          
          preparedStatement.setInt(1, cityId)
          
          val resultSet = preparedStatement.executeQuery()
          
          val airportShareList = new ListBuffer[(Airport, Double)]()
          
          while (resultSet.next()) {
            AirportCache.getAirport(resultSet.getInt("airport")).foreach { airport =>
              airportShareList.append((airport, resultSet.getDouble("share")))
            } 
          }
          
          resultSet.close()
          preparedStatement.close()
          airportShareList.toList
        } finally {
          connection.close()
        }        
      case None => List.empty 
    }
  }

  def updateChampionInfo(info : List[AirportChampionInfo]) = {

    val connection = Meta.getConnection()
    try {
      connection.setAutoCommit(false)
      val purgeStatement = connection.prepareStatement(s"DELETE FROM $AIRPORT_CHAMPION_TABLE")
      purgeStatement.executeUpdate()
      purgeStatement.close()

      val preparedChampStatement = connection.prepareStatement(s"INSERT INTO $AIRPORT_CHAMPION_TABLE(airport, airline, loyalist, ranking, reputation_boost) VALUES(?,?,?,?,?)")
      val preparedChampBonusStatement = connection.prepareStatement(s"INSERT INTO $AIRPORT_CHAMPION_BONUS_TABLE(airport, airline, description) VALUES(?,?,?)")

      info.foreach {
        entry =>
          preparedChampStatement.setInt(1, entry.loyalist.airport.id)
          preparedChampStatement.setInt(2, entry.loyalist.airline.id)
          preparedChampStatement.setInt(3, entry.loyalist.amount)
          preparedChampStatement.setInt(4, entry.ranking)
          preparedChampStatement.setDouble(5, entry.reputationBoost)

          preparedChampStatement.executeUpdate()

          entry.bonuses.foreach { bonus =>
            preparedChampBonusStatement.setInt(1, entry.loyalist.airport.id)
            preparedChampBonusStatement.setInt(2, entry.loyalist.airline.id)
            preparedChampBonusStatement.setString(3, bonus.description)
            preparedChampBonusStatement.executeUpdate()
          }
      }

      preparedChampStatement.close()
      preparedChampBonusStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def loadChampionInfoByCriteria(criteria : List[(String, Any)]) = {
    val connection = Meta.getConnection()
    try {
      var queryString = "SELECT * FROM " + AIRPORT_CHAMPION_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      val preparedStatement = connection.prepareStatement(queryString)

      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }

      var queryBonusString = "SELECT * FROM " + AIRPORT_CHAMPION_BONUS_TABLE

      if (criteria.nonEmpty) {
        queryBonusString += " WHERE " + criteria.map(criterion => s"${criterion._1} = ?").mkString(" AND ")
      }

      val preparedBonusStatement = connection.prepareStatement(queryBonusString)

      for (i <- 0 until criteria.size) {
        preparedBonusStatement.setObject(i + 1, criteria(i)._2)
      }

      val resultBonusSet = preparedBonusStatement.executeQuery()
      val bonusMap = mutable.HashMap[(Int, Int), ListBuffer[ReputationBonus]]() //key is airportId, airlineId
      while (resultBonusSet.next()) {
        val airportId = resultBonusSet.getInt("airport")
        val airlineId = resultBonusSet.getInt("airline")
        val entry = bonusMap.getOrElseUpdate((airportId, airlineId), ListBuffer[ReputationBonus]())
        entry.append(ReputationBonus(resultBonusSet.getString("description")))
      }


      val resultSet = preparedStatement.executeQuery()
      val result = ListBuffer[AirportChampionInfo]()


      while (resultSet.next()) {
        val airportId = resultSet.getInt("airport")
        val airlineId = resultSet.getInt("airline")
        val loyalist = Loyalist(AirportCache.getAirport(airportId).get, AirlineCache.getAirline(airlineId).get, resultSet.getInt("loyalist"))
        result += AirportChampionInfo(loyalist, ranking = resultSet.getInt("ranking"), reputationBoost = resultSet.getDouble("reputation_boost"), bonusMap.get((airportId, airlineId)).map(_.toList).getOrElse(List.empty))
      }
      resultSet.close()
      preparedStatement.close()

      result.toList
    } finally {
      connection.close()
    }
  }

  def loadCitiesServed(airportId : Int) : List[(City,Double)] = {
    val connection = Meta.getConnection()
    try {
      val cityStatement = connection.prepareStatement("SELECT a.*, c.* FROM " + AIRPORT_CITY_SHARE_TABLE + " a LEFT JOIN " + CITY_TABLE + " c ON a.city = c.id WHERE airport = ?")
      cityStatement.setInt(1, airportId)
      val result = ListBuffer[(City, Double)]()
      val cityResultSet = cityStatement.executeQuery()
      while (cityResultSet.next()) {
        val city = City(
          cityResultSet.getString("name"),
          cityResultSet.getDouble("latitude"),
          cityResultSet.getDouble("longitude"),
          cityResultSet.getString("country_code"),
          cityResultSet.getInt("population"),
          cityResultSet.getInt("income"),
          cityResultSet.getInt("city"))
        result.append((city, cityResultSet.getDouble("share")))
      }
      cityResultSet.close()
      cityStatement.close()
      result.toList
    } finally {
      connection.close()
    }
  }
}
