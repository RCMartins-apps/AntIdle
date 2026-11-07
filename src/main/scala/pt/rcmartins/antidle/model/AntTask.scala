package pt.rcmartins.antidle.model

import zio.json._

sealed trait AntTask {

  def uiOrder: Int

  def name: String

  val showButtons: Boolean = true

  val hasAntUpkeep: Boolean = true

}

object AntTask {

  implicit val decoder: JsonDecoder[AntTask] = DeriveJsonDecoder.gen[AntTask]
  implicit val encoder: JsonEncoder[AntTask] = DeriveJsonEncoder.gen[AntTask]

  case object SugarCollector extends AntTask {
    val uiOrder: Int = 1
    val name: String = "Sugar Collector"
  }

  case object AphidTender extends AntTask {
    val uiOrder: Int = 2
    val name: String = "Aphid Tender"
  }

//  case object WaterCollector extends AntTask {
//    val uiOrder: Int = 2
//    val name: String = "Water Collector"
//  }

  case object Tunneler extends AntTask {
    val uiOrder: Int = 3
    val name: String = "Tunneler"
  }

  case object SeedForager extends AntTask {
    val uiOrder: Int = 5
    val name: String = "Seed Forager"
  }

  case object RoyalNurse extends AntTask {
    val uiOrder: Int = 6
    val name: String = "Royal Nurse"
  }

//  case object Nursery extends AntTask {
//    val uiOrder: Int = 4
//    val name: String = "Nursery"
//  }

  case object CarcassCarrier extends AntTask {
    val uiOrder: Int = 4
    val name: String = "Carcass Carrier"
    override val showButtons: Boolean = false
    override val hasAntUpkeep: Boolean = false
  }

  case object Explorer extends AntTask {
    val uiOrder: Int = 100
    val name: String = "Explorer"
    override val showButtons: Boolean = false
    override val hasAntUpkeep: Boolean = false
  }

}
