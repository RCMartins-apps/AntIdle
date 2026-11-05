package pt.rcmartins.antidle.model

import zio.json._

case class AgeCohort(expiresAt: Long, count: Long)
object AgeCohort {
  implicit val decoder: JsonDecoder[AgeCohort] = DeriveJsonDecoder.gen[AgeCohort]
  implicit val encoder: JsonEncoder[AgeCohort] = DeriveJsonEncoder.gen[AgeCohort]
}

case class TravelAges(workers: List[AgeCohort] = Nil, scouts: List[AgeCohort] = Nil)
object TravelAges {
  implicit val decoder: JsonDecoder[TravelAges] = DeriveJsonDecoder.gen[TravelAges]
  implicit val encoder: JsonEncoder[TravelAges] = DeriveJsonEncoder.gen[TravelAges]
}

case class ColonyAges(active: Boolean = false, workers: List[AgeCohort] = Nil, scouts: List[AgeCohort] = Nil,
    pendingWorkers: Long = 0L, pendingScouts: Long = 0L, repletes: List[AgeCohort] = Nil, pendingRepletes: Long = 0L)
object ColonyAges {
  implicit val decoder: JsonDecoder[ColonyAges] = DeriveJsonDecoder.gen[ColonyAges]
  implicit val encoder: JsonEncoder[ColonyAges] = DeriveJsonEncoder.gen[ColonyAges]
}

case class QueenRoutine(enabled: Boolean = false, targetWorkers: Int = 0, sugarReserve: Long = 1000000L)
object QueenRoutine {
  implicit val decoder: JsonDecoder[QueenRoutine] = DeriveJsonDecoder.gen[QueenRoutine]
  implicit val encoder: JsonEncoder[QueenRoutine] = DeriveJsonEncoder.gen[QueenRoutine]
}
