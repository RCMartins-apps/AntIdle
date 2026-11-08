package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.{ActionBonus, AllData, AntsData, BasicResources, NestAttributes, UpgradesData}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.ProteinStorage

object ChamberBonuses {

  // Shared by action details and completed upgrades.
  def forChamber(chamberType: ChamberType, upgrades: UpgradesData = UpgradesData.initial): ActionBonus =
    chamberType match {
      case ChamberType.Nest =>
        ActionBonus(
          maxAnts = Constants.NestUpgradeBonusMaxAnts,
          maxTunnelingSpace = Constants.NestUpgradeBonusMaxTunnelingSpace,
        )
      case ChamberType.Queen =>
        ActionBonus(
          maxEggs = Constants.QueenChamberBonusMaxEggs,
          maxAnts = Constants.QueenChamberBonusMaxAnts,
        )
      case ChamberType.FoodStorage =>
        ActionBonus(maxSugar = Constants.FoodStorageChamberBonusMaxSugar,
          maxProtein = if (upgrades(ProteinStorage).unlocked) 100L * Constants.u else 0L)
      case ChamberType.Replete =>
        ActionBonus(repleteSlots = 1)
      case ChamberType.Aphid =>
        ActionBonus(aphidSlots = 1)
      case ChamberType.Nursery =>
        ActionBonus(maxAnts = Constants.NurseryBonusMaxAnts, maxCarcasses = 1)
      case ChamberType.RoyalNursery =>
        ActionBonus(royalBroodSlots = 1)
    }

  // Includes derived effects without adding them again to saved base capacities.
  def forDisplay(chamberType: ChamberType, ants: AntsData, nest: NestAttributes, upgrades: UpgradesData): ActionBonus = {
    val bonus = forChamber(chamberType, upgrades)
    chamberType match {
      case ChamberType.Nursery => bonus.copy(maxEggs = 3L * Constants.u, broodNurseSlots = 5)
      case ChamberType.Queen =>
        val next = nest.copy(chambers = nest.chambers.copy(
          queenChamber = nest.chambers.queenChamber.copy(level = nest.chambers.queenChamber.level + 1)))
        bonus.copy(layingIntervalReduction = BroodProgression.interval(ants, nest) - BroodProgression.interval(ants, next))
      case _ => bonus
    }
  }

  def reconcileProteinCapacity(data: AllData): AllData =
    if (!data.upgrades(ProteinStorage).unlocked) data
    else data.modify(_.basicResources.protein.maxAmount).setTo(
      BasicResources.initial.protein.maxAmount + Math.max(0, data.nestAttributes.chambers.foodStorageChamber.level).toLong * 100L * Constants.u)

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
    reconcileProteinCapacity(withLevel
      .modify(_.nestAttributes.maxAnts)
      .using(_ + bonus.maxAnts)
      .modify(_.nestAttributes.maxEggs)
      .using(_ + bonus.maxEggs)
      .modify(_.basicResources.sugar.maxAmount)
      .using(_ + bonus.maxSugar)
      .modify(_.basicResources.tunnelingSpace.maxAmount)
      .using(_ + bonus.maxTunnelingSpace))
  }

}
