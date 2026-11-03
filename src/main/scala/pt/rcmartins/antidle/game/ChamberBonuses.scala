package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.{ActionBonus, AllData}

object ChamberBonuses {

  // Shared by action details and completed upgrades.
  def forChamber(chamberType: ChamberType): ActionBonus =
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
        ActionBonus(maxSugar = Constants.FoodStorageChamberBonusMaxSugar)
      case ChamberType.Nursery =>
        ActionBonus(maxAnts = Constants.NurseryBonusMaxAnts, maxCarcasses = 1)
    }

  def completeUpgrade(allData: AllData, chamberType: ChamberType): AllData = {
    val withLevel = chamberType match {
      case ChamberType.Nest =>
        allData.modify(_.nestAttributes.chambers.nestChamber.level).using(_ + 1)
      case ChamberType.Queen =>
        allData.modify(_.nestAttributes.chambers.queenChamber.level).using(_ + 1)
      case ChamberType.FoodStorage =>
        allData.modify(_.nestAttributes.chambers.foodStorageChamber.level).using(_ + 1)
      case ChamberType.Nursery =>
        allData.modify(_.nestAttributes.chambers.nurseryChamber.level).using(_ + 1)
    }

    val bonus = forChamber(chamberType)
    withLevel
      .modify(_.nestAttributes.maxAnts)
      .using(_ + bonus.maxAnts)
      .modify(_.nestAttributes.maxEggs)
      .using(_ + bonus.maxEggs)
      .modify(_.basicResources.sugar.maxAmount)
      .using(_ + bonus.maxSugar)
      .modify(_.basicResources.tunnelingSpace.maxAmount)
      .using(_ + bonus.maxTunnelingSpace)
  }

}
