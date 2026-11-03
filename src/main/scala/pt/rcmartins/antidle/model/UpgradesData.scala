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
    def shouldShow(data: AllData): Boolean
  }

  object UpgradeType {
    private def earlyUpgradesAvailable(data: AllData): Boolean =
      data.nestAttributes.chambers.nestChamber.level > 0 || data.unlocks.tabs.upgradesTabUnlocked || data.unlocks.resources.showKnowledge ||
        data.basicResources.knowledge.amount > 0

    case object UnlockQueensChamber extends UpgradeType {
      def shouldShow(data: AllData): Boolean = earlyUpgradesAvailable(data)
      val name: String = "UnlockQueensChamber"
      val cost: ActionCost = ActionCost(sugar = 100 * u)
      val title: String = "Unlock Queen's Chamber"
      val description: String = "Unlock the ability to improves the queen's chamber abilities."
    }

    case object ImproveSugarCollectorTask1 extends UpgradeType {
      def shouldShow(data: AllData): Boolean = earlyUpgradesAvailable(data)
      val name: String = "ImproveSugarCollectorTask1"
      val cost: ActionCost = ActionCost(sugar = 150 * u)
      val title: String = "Foraging Techniques 1"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object ImproveSugarCollectorTask2 extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(ImproveSugarCollectorTask1).unlocked
      val name: String = "ImproveSugarCollectorTask2"
      val cost: ActionCost = ActionCost(sugar = 750 * u, knowledge = 75 * u)
      val title: String = "Foraging Techniques 2"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object ImproveSugarCollectorTask3 extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(ImproveSugarCollectorTask2).unlocked
      val name: String = "ImproveSugarCollectorTask3"
      val cost: ActionCost = ActionCost(sugar = 2500 * u, knowledge = 300 * u)
      val title: String = "Foraging Techniques 3"
      val description: String = "Improves the ants sugar collecting amount by 15%."
    }

    case object UnlockFoodStorageChamber extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(UnlockQueensChamber).unlocked && data.upgrades(ImproveSugarCollectorTask1).unlocked
      val name: String = "UnlockFoodStorageChamber"
      val cost: ActionCost = ActionCost(sugar = 400 * u)
      val title: String = "Unlock Food Storage Chamber"
      val description: String = "Unlock the ability to increase food storage capacity."
    }

    case object UnlockExplorerTask extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(UnlockFoodStorageChamber).unlocked
      val name: String = "UnlockExplorerTask"
      val cost: ActionCost = ActionCost(sugar = 750 * u)
      val title: String = "Unlock Explorer Task"
      val description: String = "Unlock the ability to send ants to explore the surroundings."
    }

    case object DefaultWorkers extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.unlocks.tabs.exploreTabUnlocked || data.upgrades(UnlockExplorerTask).unlocked
      val name: String = "DefaultWorkers"
      val cost: ActionCost = ActionCost(sugar = 500 * u, knowledge = 50 * u)
      val title: String = "Default Workers"
      val description: String = "Choose a default task for available workers. Its workers can be used wherever idle workers are required."
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
      DefaultWorkers,
    )

  }

  val initial: UpgradesData =
    UpgradesData(UpgradeType.all.map(_ -> UpgradeData.empty).toMap)

}
