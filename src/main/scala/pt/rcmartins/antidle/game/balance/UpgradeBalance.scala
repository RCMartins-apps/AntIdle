package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.game.{DiscoveryProgression, MilestoneResearch}
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import zio.json._

/** Conditions for earning visibility. Once earned, visibility normally persists. These are
  * progression prerequisites, not affordability checks.
  */
@jsonExplicitNull
case class UpgradeRequirements(
    upgrades: List[UpgradeType] = Nil,
    minimumRevealedSites: Int = 0,
    discovery: Option[String] = None,
    nursery: Boolean = false,
    outpost: Boolean = false,
    aging: Boolean = false,
    proteinDiscovered: Boolean = false,
    minimumSmallWorkers: Int = 0,
    earlyResearch: Boolean = false,
    beyondWoodland: Boolean = false,
    completedMissions: Int = 0,
    matureWorkers: Int = 0,
    territoryContests: Int = 0,
) {
  def met(data: AllData): Boolean =
    data.statistics.explorationsCompleted.thisRun >= completedMissions &&
      data.statistics.territoryContests.thisRun >= territoryContests &&
      MilestoneResearch.standardWorkers(data.statistics) >= matureWorkers &&
      (!beyondWoodland || data.ants.lineage.beyondWoodland) && upgrades.forall(
        data.upgrades(_).unlocked
      ) &&
      data.exploration.revealedSites >= minimumRevealedSites &&
      discovery.forall(DiscoveryProgression.has(data.exploration, _)) &&
      (!nursery || data.nestAttributes.chambers.nurseryChamber.level > 0) &&
      (!outpost || data.nestAttributes.dryClearingOutpost) &&
      (!aging || data.ants.ages.active) &&
      (!proteinDiscovered || data.exploration.proteinDiscovered) &&
      (minimumSmallWorkers == 0 || (!data.ants.standardWorkersUnlocked && data.ants.smallWorkers >= minimumSmallWorkers.toLong * u)) &&
      (!earlyResearch || data.nestAttributes.chambers.nestChamber.level > 0 || data.unlocks.tabs.upgradesTabUnlocked ||
        data.unlocks.resources.showKnowledge || data.basicResources.knowledge.amount > 0L)

}

object UpgradeRequirements {
  implicit private val upgradeIdListEncoder: JsonEncoder[List[UpgradeType]] =
    implicitly[JsonEncoder[List[String]]].contramap(_.map(_.name))

  implicit private val upgradeIdListDecoder: JsonDecoder[List[UpgradeType]] =
    implicitly[JsonDecoder[List[String]]].mapOrFail { ids =>
      val registry = UpgradeType.all.iterator.map(upgrade => upgrade.name -> upgrade).toMap
      ids.find(id => !registry.contains(id)) match {
        case Some(id) => Left(s"Unknown upgrade: $id")
        case None     => Right(ids.map(registry))
      }
    }

  implicit val jsonCodec: JsonCodec[UpgradeRequirements] = DeriveJsonCodec.gen[UpgradeRequirements]
}

@jsonDiscriminator("type")
sealed trait UpgradeBonus {
  def description: String
}

object UpgradeBonus {
  @jsonHint("productionPercent")
  case class ProductionPercent(source: String, percent: Int) extends UpgradeBonus {
    def description: String = {
      val output = source match {
        case "sugarCollectors" => "sugar collection"
        case "scrapsGatherers" => "protein from Scraps Gatherers"
        case "aphidTenders"    => "sugar from Aphid Tenders"
        case "dewGatherers"    => "water from Dew Gatherers"
      }
      s"+$percent% $output."
    }

  }

  @jsonHint("storageCapacity")
  case class StorageCapacity(resource: String, amount: Long) extends UpgradeBonus {
    def description: String = s"$resource storage capacity"
  }

  @jsonHint("slotCapacity")
  case class SlotCapacity(slot: String, count: Int) extends UpgradeBonus {
    def description: String =
      if (slot == "expedition") s"+$count expedition ${if (count == 1) "slot" else "slots"}."
      else s"$slot capacity"

  }

  implicit val jsonCodec: JsonCodec[UpgradeBonus] = DeriveJsonCodec.gen[UpgradeBonus]
}

@jsonExplicitNull
case class UpgradeDefinition(
    title: String,
    cost: ActionCost,
    requirements: UpgradeRequirements,
    description: String,
    bonuses: List[UpgradeBonus] = Nil
) {
  def help: String =
    (bonuses.map(_.description) :+ description).filter(_.nonEmpty).mkString(" ")

}

object UpgradeDefinition {
  implicit val jsonCodec: JsonCodec[UpgradeDefinition] = DeriveJsonCodec.gen[UpgradeDefinition]
}

/** Apply catalog data using the stable upgrade IDs stored in saves. */
object UpgradeBalance {
  def apply(upgrade: UpgradeType): UpgradeDefinition = definitions(upgrade)

  lazy val definitions: Map[UpgradeType, UpgradeDefinition] =
    UpgradeTuning
      .load(UpgradeOverrides.json)
      .fold(
        error => throw new IllegalArgumentException(s"src/main/balance/upgrades.json: $error"),
        identity
      )

  private def ownedBonuses(
      upgrades: UpgradesData,
      catalog: Map[UpgradeType, UpgradeDefinition]
  ): Iterator[UpgradeBonus] =
    UpgradeType.all.iterator.filter(upgrades(_).unlocked).flatMap(id => catalog(id).bonuses)

  def addedSlots(slot: String, upgrades: UpgradesData): Int =
    addedSlots(slot, upgrades, definitions)

  private[balance] def addedSlots(
      slot: String,
      upgrades: UpgradesData,
      catalog: Map[UpgradeType, UpgradeDefinition]
  ): Int =
    ownedBonuses(upgrades, catalog).collect { case UpgradeBonus.SlotCapacity(`slot`, count) =>
      count
    }.sum

  def production(amount: Long, source: String, upgrades: UpgradesData): Long =
    production(amount, source, upgrades, definitions)

  private[balance] def production(
      amount: Long,
      source: String,
      upgrades: UpgradesData,
      catalog: Map[UpgradeType, UpgradeDefinition]
  ): Long =
    ownedBonuses(upgrades, catalog)
      .collect {
        case bonus: UpgradeBonus.ProductionPercent if bonus.source == source => bonus
      }
      .foldLeft(amount) { (value, bonus) =>
        (BigDecimal(value) * (100 + bonus.percent) / 100).toLong
      }

}
