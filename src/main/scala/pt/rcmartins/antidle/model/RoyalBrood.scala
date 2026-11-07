package pt.rcmartins.antidle.model

import zio.json._

case class RoyalBrood(careUnits: Long = 0L, paused: Boolean = false)

object RoyalBrood {
  implicit val decoder: JsonDecoder[RoyalBrood] = DeriveJsonDecoder.gen[RoyalBrood]
  implicit val encoder: JsonEncoder[RoyalBrood] = DeriveJsonEncoder.gen[RoyalBrood]
}
