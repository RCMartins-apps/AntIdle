package pt.rcmartins.antidle.game.saves

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.{DefaultWorkerAssignment, PopulationAging, ResourceVisibility, UpgradeVisibility, ExplorationProgression}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AllDataForSave(
    version: Int,
    world: WorldData,
    ants: AntsData,
    basicResources: BasicResources,
    nestAttributes: NestAttributes,
    unlocks: Unlocks,
    upgrades: UpgradesDataForSave,
    exploration: ExplorationData,
    statistics: StatisticsData,
    messages: Seq[String],
) {

  def toAllData: AllData = {
    val loaded = AllData(
      version = version,
      world = world,
      ants = ants,
      basicResources = basicResources,
      nestAttributes = nestAttributes,
      unlocks = unlocks,
      upgrades = toUpgrades,
      exploration = exploration,
      statistics = statistics,
      messages = messages,
    )
    DefaultWorkerAssignment.normalize(UpgradeVisibility.reconcile(ResourceVisibility.reconcile(PopulationAging.reconcile(ExplorationProgression.migrateLegacySupplies(loaded)))))
  }

  private def toUpgrades: UpgradesData = {
    UpgradesData(
      UpgradesData.initial.upgrades.map { case (upgradeType, initialUpgrade) =>
        upgradeType -> {
          upgrades.upgrades.find(_.name == upgradeType.name) match {
            case None          => initialUpgrade
            case Some(upgrade) => UpgradeData(upgrade.show, upgrade.unlocked)
          }
        }
      }
    )
  }

}

object AllDataForSave {

  implicit val decoder: JsonDecoder[AllDataForSave] = DeriveJsonDecoder.gen[AllDataForSave]
  implicit val encoder: JsonEncoder[AllDataForSave] = DeriveJsonEncoder.gen[AllDataForSave]

  def fromAllData(allData: AllData): AllDataForSave =
    AllDataForSave(
      version = allData.version,
      world = allData.world,
      ants = allData.ants,
      basicResources = allData.basicResources,
      nestAttributes = allData.nestAttributes,
      unlocks = allData.unlocks,
      upgrades = UpgradesDataForSave(
        allData.upgrades.upgrades.toSeq
          .map { case (upgradeType, upgradeData) =>
            UpgradeDataForSave(
              name = upgradeType.name,
              show = upgradeData.show,
              unlocked = upgradeData.unlocked
            )
          }
          .sortBy(_.name)
      ),
      exploration = allData.exploration,
      statistics = allData.statistics,
      messages = allData.messages,
    )

}
