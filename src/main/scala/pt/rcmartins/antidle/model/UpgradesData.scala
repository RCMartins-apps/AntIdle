package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.balance.{UpgradeBalance, UpgradeDefinition}
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
    final def definition: UpgradeDefinition = UpgradeBalance(this)
    final def cost: ActionCost = definition.cost
    final def title: String = definition.title
    final def description: String = definition.description
    final def shouldShow(data: AllData): Boolean = definition.requirements.met(data)
  }

  object UpgradeType {
    case object StandardWorkers extends UpgradeType { val name: String = "StandardWorkers" }

    case object UnlockQueensChamber extends UpgradeType { val name: String = "UnlockQueensChamber" }

    case object ImproveSugarCollectorTask1 extends UpgradeType { val name: String = "ImproveSugarCollectorTask1" }

    case object ImproveSugarCollectorTask2 extends UpgradeType { val name: String = "ImproveSugarCollectorTask2" }

    case object ImproveSugarCollectorTask3 extends UpgradeType { val name: String = "ImproveSugarCollectorTask3" }

    case object UnlockFoodStorageChamber extends UpgradeType { val name: String = "UnlockFoodStorageChamber" }

    case object UnlockExplorerTask extends UpgradeType { val name: String = "UnlockExplorerTask" }

    case object DefaultWorkers extends UpgradeType { val name: String = "DefaultWorkers" }

    case object AdditionalExpeditionSlot extends UpgradeType { val name: String = "AdditionalExpeditionSlot" }

    case object ExpeditionSupplies1 extends UpgradeType { val name: String = "ExpeditionSupplies1" }
    case object ExpeditionSupplies2 extends UpgradeType { val name: String = "ExpeditionSupplies2" }
    case object ExpeditionSupplies3 extends UpgradeType { val name: String = "ExpeditionSupplies3" }

    val expeditionSupplies: Seq[UpgradeType] = Seq(ExpeditionSupplies1, ExpeditionSupplies2, ExpeditionSupplies3)

    case object EstablishedTrails extends UpgradeType { val name: String = "EstablishedTrails" }

    case object ReinforcedMandibles extends UpgradeType { val name: String = "ReinforcedMandibles" }

    case object BroodCare extends UpgradeType { val name: String = "BroodCare" }

    case object AphidTending extends UpgradeType { val name: String = "AphidTending" }

    case object ProteinStorage extends UpgradeType { val name: String = "ProteinStorage" }

    case object QueensRoutine extends UpgradeType { val name: String = "QueensRoutine" }

    case object ConstructionPlanning extends UpgradeType { val name: String = "ConstructionPlanning" }

    case object ColonyNutrition extends UpgradeType { val name: String = "ColonyNutrition" }

    case object RepleteStorage extends UpgradeType { val name: String = "RepleteStorage" }

    case object CoveredTrails extends UpgradeType { val name: String = "CoveredTrails" }

    case object SeedProcessing extends UpgradeType { val name: String = "SeedProcessing" }

    case object RoyalNurseryPlanning extends UpgradeType { val name: String = "RoyalNurseryPlanning" }

    case object AphidShelters extends UpgradeType { val name = "AphidShelters" }
    case object TrailLogistics extends UpgradeType { val name = "TrailLogistics" }
    case object CoordinatedExpeditions extends UpgradeType { val name = "CoordinatedExpeditions" }

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
      ExpeditionSupplies1,
      ExpeditionSupplies2,
      ExpeditionSupplies3,
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
      AphidShelters, TrailLogistics, CoordinatedExpeditions,
    )

  }

  val initial: UpgradesData =
    UpgradesData(UpgradeType.all.map(_ -> UpgradeData.empty).toMap)

}
