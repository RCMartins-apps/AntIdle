package pt.rcmartins.antidle.model
import zio.json._
case class Lineage(foundings: Int = 0, dna: Long = 0L, sharedPaths: Boolean = false, beyondWoodland: Boolean = false)
object Lineage {
  implicit val encoder: JsonEncoder[Lineage] = DeriveJsonEncoder.gen[Lineage]
  implicit val decoder: JsonDecoder[Lineage] = DeriveJsonDecoder.gen[Lineage]
}
case class SugarTrail(remaining: Long = 0L, marked: Boolean = false)
object SugarTrail {
  implicit val encoder: JsonEncoder[SugarTrail] = DeriveJsonEncoder.gen[SugarTrail]
  implicit val decoder: JsonDecoder[SugarTrail] = DeriveJsonDecoder.gen[SugarTrail]
}
