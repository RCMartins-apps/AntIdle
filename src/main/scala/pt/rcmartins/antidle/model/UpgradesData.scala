package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import zio.json._

case class UpgradesData(
    upgrades: Map[UpgradeType, UpgradeData],
) {

  def apply(upgradeType: UpgradeType): UpgradeData =
    upgrades(upgradeType)

  def modify(upgradeType: UpgradeType)(f: UpgradeData => UpgradeData): UpgradesData =
    copy(upgrades = upgrades.updatedWith(upgradeType)(_.map(f)))

  def unlock(upgradeTypes: UpgradeType): UpgradesData =
    modify(upgradeTypes)(_.copy(unlocked = true))

  def unlock(upgradeTypes: UpgradeType*): UpgradesData =
    upgradeTypes.foldLeft(this)(_.unlock(_))

  def show(upgradeTypes: UpgradeType): UpgradesData =
    modify(upgradeTypes)(_.copy(show = true))

  def show(upgradeTypes: UpgradeType*): UpgradesData =
    upgradeTypes.foldLeft(this)(_.show(_))

}

object UpgradesData {

  sealed trait UpgradeType {
    def name: String
    def cost: ActionCost
    def title: String
    def description: String
  }

  object UpgradeType {
    case object UnlockQueensChamber extends UpgradeType {
      val name: String = "UnlockQueensChamber"
      val cost: ActionCost = ActionCost(colonyPoints = 50 * u)
      val title: String = "Unlock Queen's Chamber"
      val description: String = "Unlock the ability to improves the queen's chamber abilities."
    }

    case object ImproveSugarCollectorTask1 extends UpgradeType {
      val name: String = "ImproveSugarCollectorTask1"
      val cost: ActionCost = ActionCost(sugar = 100 * u, colonyPoints = 100 * u)
      val title: String = "Foraging Techniques 1"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object ImproveSugarCollectorTask2 extends UpgradeType {
      val name: String = "ImproveSugarCollectorTask2"
      val cost: ActionCost = ActionCost(sugar = 500 * u, colonyPoints = 500 * u)
      val title: String = "Foraging Techniques 2"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object ImproveSugarCollectorTask3 extends UpgradeType {
      val name: String = "ImproveSugarCollectorTask3"
      val cost: ActionCost = ActionCost(sugar = 2500 * u, colonyPoints = 2500 * u)
      val title: String = "Foraging Techniques 3"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object UnlockFoodStorageChamber extends UpgradeType {
      val name: String = "UnlockFoodStorageChamber"
      val cost: ActionCost = ActionCost(colonyPoints = 500 * u)
      val title: String = "Unlock Sugar Storage Chamber"
      val description: String = "Unlock the ability to increase sugar storage capacity."
    }

    case object UnlockExplorerTask extends UpgradeType {
      val name: String = "UnlockExplorerTask"
      val cost: ActionCost = ActionCost(colonyPoints = 1000 * u)
      val title: String = "Unlock Explorer Task"
      val description: String = "Unlock the ability to send ants to explore the surroundings."
    }

    implicit val decoder: JsonDecoder[UpgradeType] = DeriveJsonDecoder.gen[UpgradeType]
    implicit val encoder: JsonEncoder[UpgradeType] = DeriveJsonEncoder.gen[UpgradeType]

    val all: Seq[UpgradeType] = Seq(
      UnlockQueensChamber,
      ImproveSugarCollectorTask1,
      ImproveSugarCollectorTask2,
      ImproveSugarCollectorTask3,
      UnlockFoodStorageChamber,
      UnlockExplorerTask,
    )

  }

  val initial: UpgradesData =
    UpgradesData(UpgradeType.all.map(_ -> UpgradeData.empty).toMap)

}
