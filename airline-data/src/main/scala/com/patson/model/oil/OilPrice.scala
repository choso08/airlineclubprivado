package com.patson.model.oil

case class OilPrice(price : Double, cycle : Int)

object OilPrice {
  //OilSimulation's MAX_PRICE is derived from this, so it moves the whole band.
  val DEFAULT_PRICE : Double = com.patson.model.GameConfig.fuelPrice //upstream default: 70
    //the price used for actual simulation calculation
}