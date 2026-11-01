package pt.rcmartins.antidle.model

import zio.json._

case class ActionCost(
    sugar: Long = 0L,
    protein: Long = 0L,
//    water: Long = 0L,
    tunnelingSpace: Long = 0L,
    colonyPoints: Long = 0L,
//    DNA: Long = 0L,
    idleWorkers: Long = 0L,
)

object ActionCost {

  implicit val decoder: JsonDecoder[ActionCost] = DeriveJsonDecoder.gen[ActionCost]
  implicit val encoder: JsonEncoder[ActionCost] = DeriveJsonEncoder.gen[ActionCost]

  val empty: ActionCost = ActionCost()

}
