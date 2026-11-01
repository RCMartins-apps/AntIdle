package pt.rcmartins.antidle.model

import zio.json._

case class Carcass(foodRemaining: Long, totalFood: Long, proteinReward: Long)

object Carcass {
  implicit val decoder: JsonDecoder[Carcass] = DeriveJsonDecoder.gen[Carcass]
  implicit val encoder: JsonEncoder[Carcass] = DeriveJsonEncoder.gen[Carcass]
}
