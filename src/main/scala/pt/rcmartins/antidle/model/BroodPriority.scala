package pt.rcmartins.antidle.model

import zio.json._

sealed abstract class BroodPriority(val label: String) {
  def prefers(brood: AntBrood.Developing): Boolean
}

object BroodPriority {
  case object Balanced extends BroodPriority("Balanced") {
    def prefers(brood: AntBrood.Developing): Boolean = false
  }

  case object WorkersFirst extends BroodPriority("Workers first") {
    def prefers(brood: AntBrood.Developing): Boolean = brood.kind != "scout"
  }

  case object ScoutsFirst extends BroodPriority("Scouts first") {
    def prefers(brood: AntBrood.Developing): Boolean = brood.kind == "scout"
  }

  val choices: List[BroodPriority] = List(Balanced, WorkersFirst, ScoutsFirst)
  implicit val decoder: JsonDecoder[BroodPriority] = DeriveJsonDecoder.gen[BroodPriority]
  implicit val encoder: JsonEncoder[BroodPriority] = DeriveJsonEncoder.gen[BroodPriority]
}
