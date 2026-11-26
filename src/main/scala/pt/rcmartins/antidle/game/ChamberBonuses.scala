package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.balance.ChamberPricing
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.{
  ActionBonus,
  AllData,
  AntsData,
  BasicResources,
  NestAttributes,
  UpgradesData
}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.ProteinStorage
import zio.json._

object ChamberBonuses {
  case class CapacityBase(sugar: Long, tunnelingSpace: Long, population: Option[Long] = None)
  object CapacityBase {
    implicit val codec: JsonCodec[CapacityBase] = DeriveJsonCodec.gen[CapacityBase]
  }

  // Historical rates are used only to interpret saves without an explicit base.
  private val legacySugar = 1000L * Constants.u
  private val legacySpace = 500L * Constants.u
  private def paidStorageLevels(data: AllData): Int =
    Math.max(0, data.nestAttributes.chambers.foodStorageChamber.level - 1)

  private def nestLevels(data: AllData): Int =
    Math.max(0, data.nestAttributes.chambers.nestChamber.level)

  private[game] case class Housing(nest: Int, queen: Int, nursery: Int) {
    def contribution(data: AllData): Long = {
      val chambers = data.nestAttributes.chambers
      (Math.max(0, chambers.nestChamber.level).toLong * nest +
        Math.max(0L, chambers.queenChamber.level.toLong - 1L) * queen +
        Math.max(0, chambers.nurseryChamber.level).toLong * nursery) * Constants.u
    }

  }

  private val legacyHousing = Housing(10, 5, 3)
  private lazy val housing = Housing(
    ChamberPricing.slotsPerLevel(ChamberType.Nest, "population"),
    ChamberPricing.slotsPerLevel(ChamberType.Queen, "population"),
    ChamberPricing.slotsPerLevel(ChamberType.Nursery, "population")
  )

  private def base(data: AllData, sugar: Long, space: Long): CapacityBase =
    CapacityBase(
      data.basicResources.sugar.maxAmount - contribution(sugar, paidStorageLevels(data)),
      data.basicResources.tunnelingSpace.maxAmount - contribution(space, nestLevels(data)),
      Some(data.nestAttributes.maxAnts - housing.contribution(data))
    )

  private def contribution(rate: Long, levels: Int): Long =
    (BigInt(rate) * levels).min(BigInt(Long.MaxValue)).toLong

  private def total(base: Long, rate: Long, levels: Int): Long =
    (BigInt(base) + contribution(rate, levels)).max(BigInt(0L)).min(BigInt(Long.MaxValue)).toLong

  private def storage = ChamberPricing.capacityBonus(ChamberType.FoodStorage, "sugar")
  private def space = ChamberPricing.capacityBonus(ChamberType.Nest, "tunnelingSpace")
  private def protein = ChamberPricing.capacityBonus(ChamberType.FoodStorage, "protein")

  def nurseryBroodPlaces(nest: NestAttributes): Int =
    Math.max(0, nest.chambers.nurseryChamber.level) * ChamberPricing.nurserySlotsPerLevel("brood")

  def nurseryNurseCapacity(nest: NestAttributes): Long =
    Math.max(0, nest.chambers.nurseryChamber.level).toLong * ChamberPricing.nurserySlotsPerLevel(
      "broodNurse"
    ) * Constants.u

  def savedCapacityBase(data: AllData): Option[CapacityBase] =
    Option.when(storage != legacySugar || space != legacySpace || housing != legacyHousing)(
      base(data, storage, space)
    )

  private[game] def restoreCapacity(
      data: AllData,
      saved: Option[CapacityBase],
      sugarPerLevel: Long = storage,
      spacePerLevel: Long = space,
      housingPerLevel: Housing = housing
  ): AllData = {
    val capacity = saved.getOrElse(base(data, legacySugar, legacySpace).copy(population = None))
    val populationBase = saved
      .flatMap(_.population)
      .getOrElse(data.nestAttributes.maxAnts - legacyHousing.contribution(data))
    data
      .modify(_.basicResources.sugar.maxAmount)
      .setTo(total(capacity.sugar, sugarPerLevel, paidStorageLevels(data)))
      .modify(_.basicResources.tunnelingSpace.maxAmount)
      .setTo(total(capacity.tunnelingSpace, spacePerLevel, nestLevels(data)))
      .modify(_.nestAttributes.maxAnts)
      .setTo(total(populationBase, housingPerLevel.contribution(data), 1))
  }

  // Shared by action details and completed upgrades.
  def forChamber(
      chamberType: ChamberType,
      upgrades: UpgradesData = UpgradesData.initial
  ): ActionBonus =
    chamberType match {
      case ChamberType.Nest =>
        ActionBonus(
          maxAnts = housing.nest.toLong * Constants.u,
          maxTunnelingSpace = space,
        )
      case ChamberType.Queen =>
        ActionBonus(
          maxEggs = Constants.QueenChamberBonusMaxEggs,
          maxAnts = housing.queen.toLong * Constants.u,
        )
      case ChamberType.FoodStorage =>
        ActionBonus(
          maxSugar = storage,
          maxProtein = if (upgrades(ProteinStorage).unlocked) protein else 0L
        )
      case ChamberType.Replete =>
        ActionBonus(repleteSlots = 1)
      case ChamberType.Aphid =>
        ActionBonus(aphidSlots =
          if (upgrades(pt.rcmartins.antidle.model.UpgradesData.UpgradeType.AphidShelters).unlocked)
            AphidHusbandry.ShelteredPlacesPerLevel
          else AphidHusbandry.BasePlacesPerLevel
        )
      case ChamberType.Nursery =>
        ActionBonus(
          maxAnts = housing.nursery.toLong * Constants.u,
          maxCarcasses = ChamberPricing.nurserySlotsPerLevel("carcass")
        )
      case ChamberType.RoyalNursery =>
        ActionBonus(royalBroodSlots = 1)
    }

  // Includes derived effects without adding them again to saved base capacities.
  def forDisplay(
      chamberType: ChamberType,
      ants: AntsData,
      nest: NestAttributes,
      upgrades: UpgradesData
  ): ActionBonus = {
    val bonus = forChamber(chamberType, upgrades)
    chamberType match {
      case ChamberType.Nursery =>
        val opensRefuse = nest.chambers.nurseryChamber.level == Midden.RequiredNurseryLevel - 1
        bonus.copy(
          maxEggs = ChamberPricing.nurserySlotsPerLevel("brood").toLong * Constants.u,
          broodNurseSlots = ChamberPricing.nurserySlotsPerLevel("broodNurse"),
          maxRefuse = if (opensRefuse) Midden.BaseCapacity else 0L
        )
      case ChamberType.Queen =>
        val next = nest.copy(chambers =
          nest.chambers.copy(
            queenChamber =
              nest.chambers.queenChamber.copy(level = nest.chambers.queenChamber.level + 1)
          )
        )
        bonus.copy(layingIntervalReduction =
          BroodProgression.interval(ants, nest, upgrades) - BroodProgression.interval(
            ants,
            next,
            upgrades
          )
        )
      case _ => bonus
    }
  }

  def reconcileProteinCapacity(data: AllData): AllData =
    if (!data.upgrades(ProteinStorage).unlocked) data
    else
      data
        .modify(_.basicResources.protein.maxAmount)
        .setTo(
          total(
            BasicResources.initial.protein.maxAmount,
            protein,
            Math.max(0, data.nestAttributes.chambers.foodStorageChamber.level)
          )
        )

  def completeUpgrade(allData: AllData, chamberType: ChamberType): AllData = {
    val withLevel = chamberType match {
      case ChamberType.Nest =>
        allData.modify(_.nestAttributes.chambers.nestChamber.level).using(_ + 1)
      case ChamberType.Queen =>
        allData.modify(_.nestAttributes.chambers.queenChamber.level).using(_ + 1)
      case ChamberType.FoodStorage =>
        allData.modify(_.nestAttributes.chambers.foodStorageChamber.level).using(_ + 1)
      case ChamberType.Replete =>
        allData.modify(_.nestAttributes.chambers.repleteChamber.level).using(_ + 1)
      case ChamberType.Aphid =>
        allData.modify(_.nestAttributes.chambers.aphidChamber.level).using(_ + 1)
      case ChamberType.Nursery =>
        allData.modify(_.nestAttributes.chambers.nurseryChamber.level).using(_ + 1)
      case ChamberType.RoyalNursery =>
        allData.modify(_.nestAttributes.chambers.royalNurseryChamber.level).setTo(1)
    }

    val bonus = forChamber(chamberType, allData.upgrades)
    reconcileProteinCapacity(
      restoreCapacity(withLevel, Some(base(allData, storage, space)))
        .modify(_.nestAttributes.maxEggs)
        .using(_ + bonus.maxEggs)
    )
  }

}
