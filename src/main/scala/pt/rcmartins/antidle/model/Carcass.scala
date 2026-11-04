package pt.rcmartins.antidle.model

import zio.json._

case class Carcass(
    foodRemaining: Long,
    totalFood: Long,
    proteinReward: Long,
    requiredWorkers: Long,
    retrievalTicks: Long,
)

object Carcass {
  implicit val decoder: JsonDecoder[Carcass] = DeriveJsonDecoder.gen[Carcass]
  implicit val encoder: JsonEncoder[Carcass] = DeriveJsonEncoder.gen[Carcass]
}

case class CarcassRetrieval(carcass: Carcass, targetTick: Long, ages: TravelAges = TravelAges())

object CarcassRetrieval {
  implicit val decoder: JsonDecoder[CarcassRetrieval] = DeriveJsonDecoder.gen[CarcassRetrieval]
  implicit val encoder: JsonEncoder[CarcassRetrieval] = DeriveJsonEncoder.gen[CarcassRetrieval]
}
