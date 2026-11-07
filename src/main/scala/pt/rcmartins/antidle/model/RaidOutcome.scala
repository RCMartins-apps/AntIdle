package pt.rcmartins.antidle.model
import zio.json._
case class RaidOutcome(workerLosses: Int, protein: Long, randomSeed: Long = 0L)
object RaidOutcome {
  implicit val encoder: JsonEncoder[RaidOutcome] = DeriveJsonEncoder.gen[RaidOutcome]
  implicit val decoder: JsonDecoder[RaidOutcome] = DeriveJsonDecoder.gen[RaidOutcome]
}
