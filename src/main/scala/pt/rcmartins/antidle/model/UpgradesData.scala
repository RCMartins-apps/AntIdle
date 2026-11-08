package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.Constants.{FoodStorageChamberBonusMaxSugar, u}
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

    case object StandardWorkers extends UpgradeType {
      val name = "StandardWorkers"
      val title = "Standard Worker Development"
      val cost = ActionCost(sugar = 75 * u, tunnelingSpace = 10 * u)
      val description = "Future worker eggs: full-sized workers (2× small-worker output). Brood requires protein. Unlocks Scraps Gatherers. Existing ants and brood keep their caste."
      def shouldShow(data: AllData): Boolean = !data.ants.standardWorkersUnlocked && data.ants.smallWorkers >= 5 * u
    }

    case object UnlockQueensChamber extends UpgradeType {
      def shouldShow(data: AllData): Boolean = earlyUpgradesAvailable(data)
      val name: String = "UnlockQueensChamber"
      val cost: ActionCost = ActionCost(sugar = 100 * u)
      val title: String = "Unlock Queen's Chamber"
      val description: String = "Chamber upgrades: more brood places and faster laying."
    }

    case object ImproveSugarCollectorTask1 extends UpgradeType {
      def shouldShow(data: AllData): Boolean = earlyUpgradesAvailable(data)
      val name: String = "ImproveSugarCollectorTask1"
      val cost: ActionCost = ActionCost(sugar = 150 * u)
      val title: String = "Foraging Techniques 1"
      val description: String = "+15% sugar collection."
    }

    case object ImproveSugarCollectorTask2 extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        (data.unlocks.tabs.exploreTabUnlocked || data.upgrades(UnlockExplorerTask).unlocked) && data.upgrades(ImproveSugarCollectorTask1).unlocked
      val name: String = "ImproveSugarCollectorTask2"
      val cost: ActionCost = ActionCost(sugar = 750 * u, knowledge = 75 * u)
      val title: String = "Foraging Techniques 2"
      val description: String = "+15% sugar collection."
    }

    case object ImproveSugarCollectorTask3 extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(ImproveSugarCollectorTask2).unlocked
      val name: String = "ImproveSugarCollectorTask3"
      val cost: ActionCost = ActionCost(sugar = 2500 * u, knowledge = 300 * u)
      val title: String = "Foraging Techniques 3"
      val description: String = "+15% sugar collection."
    }

    case object UnlockFoodStorageChamber extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(UnlockQueensChamber).unlocked && data.upgrades(ImproveSugarCollectorTask1).unlocked
      val name: String = "UnlockFoodStorageChamber"
      val cost: ActionCost = ActionCost(sugar = 400 * u)
      val title: String = "Unlock Food Storage Chamber"
      val description: String = s"Food Storage Chamber: +${FoodStorageChamberBonusMaxSugar / u} sugar capacity per level."
    }

    case object UnlockExplorerTask extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.upgrades(UnlockFoodStorageChamber).unlocked
      val name: String = "UnlockExplorerTask"
      val cost: ActionCost = ActionCost(sugar = 750 * u)
      val title: String = "Unlock Explorer Task"
      val description: String = "Unlocks exploration expeditions."
    }

    case object DefaultWorkers extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.unlocks.tabs.exploreTabUnlocked || data.upgrades(UnlockExplorerTask).unlocked
      val name: String = "DefaultWorkers"
      val cost: ActionCost = ActionCost(sugar = 500 * u, knowledge = 50 * u)
      val title: String = "Default Workers"
      val description: String = "Idle workers join a chosen default task and remain available for colony actions."
    }

    case object AdditionalExpeditionSlot extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 3
      val name: String = "AdditionalExpeditionSlot"
      val cost: ActionCost = ActionCost(sugar = 1000 * u, knowledge = 100 * u)
      val title: String = "Expedition Coordination"
      val description: String = "+1 expedition slot."
    }

    case object EstablishedTrails extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 2
      val name: String = "EstablishedTrails"
      val cost: ActionCost = ActionCost(sugar = 600 * u, knowledge = 60 * u)
      val title: String = "Established Trails"
      val description: String = "−20% sugar-yield loss per added collector."
    }

    case object ReinforcedMandibles extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 3
      val name: String = "ReinforcedMandibles"
      val cost: ActionCost = ActionCost(sugar = 1200 * u, protein = 20 * u, knowledge = 100 * u)
      val title: String = "Reinforced Mandibles"
      val description: String = "+50% Tunneler output."
    }

    case object BroodCare extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && data.nestAttributes.chambers.nurseryChamber.level > 0 &&
          pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 4
      val name: String = "BroodCare"
      val cost: ActionCost = ActionCost(sugar = 2000 * u, protein = 40 * u, knowledge = 180 * u)
      val title: String = "Brood Care"
      val description: String = "−20% worker/scout development time; includes growing brood."
    }

    case object AphidTending extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.DiscoveryProgression.WildAphids.found(data.exploration)
      val name: String = "AphidTending"
      val cost: ActionCost = ActionCost(sugar = 1500 * u, protein = 20 * u, knowledge = 100 * u)
      val title: String = "Aphid Tending"
      val description: String = "Unlocks Aphid Chamber: +1 Aphid Tender slot per level. Tenders: +0.8 sugar/s · −0.01 protein/s each."
    }

    case object ProteinStorage extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && data.exploration.proteinDiscovered && data.upgrades(UnlockFoodStorageChamber).unlocked
      val name: String = "ProteinStorage"
      val cost: ActionCost = ActionCost(sugar = 1000 * u, protein = 25 * u, knowledge = 75 * u)
      val title: String = "Protein Storage"
      val description: String = "+100 protein capacity per Food Storage Chamber level; includes existing levels."
    }

    case object QueensRoutine extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && data.nestAttributes.chambers.nurseryChamber.level > 0 &&
          pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 3
      val name: String = "QueensRoutine"
      val cost: ActionCost = ActionCost(sugar = 1200 * u, protein = 20 * u, knowledge = 100 * u)
      val title: String = "Queen's Routine"
      val description: String = "Automatic worker eggs and aging-job replacements. Set a population target and sugar reserve in Brood."
    }

    case object ConstructionPlanning extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 2
      val name: String = "ConstructionPlanning"
      val cost: ActionCost = ActionCost(sugar = 500 * u, knowledge = 50 * u)
      val title: String = "Construction Planning"
      val description: String = "+2 construction queue slots (3 total)."
    }

    case object ColonyNutrition extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.ants.ages.active && data.unlocks.tabs.exploreTabUnlocked &&
          pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 5
      val name: String = "ColonyNutrition"
      val cost: ActionCost = ActionCost(sugar = 2500 * u, protein = 60 * u, knowledge = 200 * u)
      val title: String = "Colony Nutrition"
      val description: String = "New workers and scouts: +20% lifespan."
    }

    case object RepleteStorage extends UpgradeType {
      def shouldShow(data: AllData): Boolean =
        data.unlocks.tabs.exploreTabUnlocked && pt.rcmartins.antidle.game.DiscoveryProgression.DewPlants.found(data.exploration)
      val name: String = "RepleteStorage"
      val cost: ActionCost = ActionCost(sugar = 1500 * u, protein = 20 * u, knowledge = 150 * u)
      val title: String = "Replete Storage"
      val description: String = "Unlocks Replete Chamber and worker promotion for water storage."
    }

    case object CoveredTrails extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.nestAttributes.dryClearingOutpost &&
        pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 7
      val name: String = "CoveredTrails"
      val cost: ActionCost = ActionCost(sugar = 3000 * u, protein = 60 * u, knowledge = 300 * u, water = 100 * u)
      val title: String = "Covered Trails"
      val description: String = "−25% Seed Forager water use (0.015 water/s each)."
    }

    case object SeedProcessing extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.nestAttributes.dryClearingOutpost &&
        pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 8
      val name: String = "SeedProcessing"
      val cost: ActionCost = ActionCost(sugar = 6000 * u, protein = 100 * u, knowledge = 700 * u, water = 150 * u)
      val title: String = "Seed Processing"
      val description: String = "+50% Seed Forager protein output (0.06 protein/s each)."
    }

    case object RoyalNurseryPlanning extends UpgradeType {
      def shouldShow(data: AllData): Boolean = data.nestAttributes.chambers.nurseryChamber.level > 0 &&
        pt.rcmartins.antidle.game.DiscoveryProgression.ResinSeep.found(data.exploration) &&
        pt.rcmartins.antidle.game.ExplorationProgression.level(data.exploration.experience) >= 8
      val name: String = "RoyalNurseryPlanning"
      val title: String = "Royal Nursery Planning"
      val cost: ActionCost = ActionCost(sugar = 5000 * u, protein = 100 * u, knowledge = 700 * u, water = 150 * u)
      val description: String = "Unlocks Royal Nursery construction."
    }

    implicit val decoder: JsonDecoder[UpgradeType] = DeriveJsonDecoder.gen[UpgradeType]
    implicit val encoder: JsonEncoder[UpgradeType] = DeriveJsonEncoder.gen[UpgradeType]

    val all: Seq[UpgradeType] = Seq(
      StandardWorkers,
      UnlockQueensChamber,
      ImproveSugarCollectorTask1,
      ImproveSugarCollectorTask2,
      ImproveSugarCollectorTask3,
      UnlockFoodStorageChamber,
      UnlockExplorerTask,
      DefaultWorkers,
      AdditionalExpeditionSlot,
      EstablishedTrails,
      ReinforcedMandibles,
      BroodCare,
      AphidTending,
      ProteinStorage,
      QueensRoutine,
      ConstructionPlanning,
      ColonyNutrition,
      RepleteStorage,
      CoveredTrails,
      SeedProcessing,
      RoyalNurseryPlanning,
    )

  }

  val initial: UpgradesData =
    UpgradesData(UpgradeType.all.map(_ -> UpgradeData.empty).toMap)

}
