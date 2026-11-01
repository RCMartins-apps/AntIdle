package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import zio.json._

case class BasicResources(
    sugar: BasicResource = BasicResource.initial,
    tunnelingSpace: BasicResource = BasicResource.initial,
    protein: BasicResource = BasicResource(0L, 100 * u),
//    water: BasicResource = BasicResource.initial,
    colonyPoints: BasicResource = BasicResource.initial,
//    DNA: BasicResource = BasicResource.initial,
) {

  def spendResources(actionCost: ActionCost): BasicResources =
    copy(
      sugar = sugar - actionCost.sugar,
      tunnelingSpace = tunnelingSpace - actionCost.tunnelingSpace,
      protein = protein - actionCost.protein,
//      water = water - actionCost.water,
      colonyPoints = colonyPoints - actionCost.colonyPoints,
//      DNA = DNA - actionCost.DNA,
    )

  def hasResources(cost: ActionCost): Boolean =
    sugar >= cost.sugar &&
      tunnelingSpace >= cost.tunnelingSpace &&
      protein >= cost.protein &&
//      water >= cost.water &&
      colonyPoints >= cost.colonyPoints
//      DNA >= cost.DNA

}

object BasicResources {

  implicit val decoder: JsonDecoder[BasicResources] = DeriveJsonDecoder.gen[BasicResources]
  implicit val encoder: JsonEncoder[BasicResources] = DeriveJsonEncoder.gen[BasicResources]

  val initial: BasicResources = BasicResources(
    sugar = BasicResource(0L, 1000 * u),
    tunnelingSpace = BasicResource(0L, 1000 * u),
    colonyPoints = BasicResource(0L, 2000 * u),
  )

  case class BasicResource(amount: Long, maxAmount: Long) {

    def >(amount: Long): Boolean = this.amount > amount

    def >=(amount: Long): Boolean = this.amount >= amount

    def +(amount: Long): BasicResource = copy(amount = Math.min(maxAmount, this.amount + amount))

    def -(amount: Long): BasicResource = copy(amount = this.amount - amount)

    def freeSpace: Long = maxAmount - amount

  }

  object BasicResource {

    implicit val decoder: JsonDecoder[BasicResource] = DeriveJsonDecoder.gen[BasicResource]
    implicit val encoder: JsonEncoder[BasicResource] = DeriveJsonEncoder.gen[BasicResource]

    val initial: BasicResource = BasicResource(0L, 0L)

  }

}
