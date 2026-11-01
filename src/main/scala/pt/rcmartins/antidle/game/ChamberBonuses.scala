package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.{ActionBonus, AllChamberData, AllData}

object ChamberBonuses {

  // Shared by action details, completed upgrades, and ongoing income.
  def forChamber(chamberType: ChamberType): ActionBonus =
    chamberType match {
      case ChamberType.Nest =>
        ActionBonus(
          colonyPointsEachWorker = Constants.defaultNestLevelColonyPointsSecond,
          maxWorkers = Constants.NestUpgradeBonusMaxWorkers,
          maxColonyPoints = Constants.NestUpgradeBonusMaxColonyPoints,
        )
      case ChamberType.Queen =>
        ActionBonus(
          maxEggs = Constants.QueenChamberBonusMaxEggs,
          maxWorkers = Constants.QueenChamberBonusMaxWorkers,
        )
      case ChamberType.FoodStorage =>
        ActionBonus(maxSugar = Constants.FoodStorageChamberBonusMaxSugar)
      case ChamberType.Nursery =>
        ActionBonus(maxScouts = Constants.NurseryBonusMaxScouts)
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
      .modify(_.nestAttributes.maxWorkers)
      .using(_ + bonus.maxWorkers)
      .modify(_.nestAttributes.maxEggs)
      .using(_ + bonus.maxEggs)
      .modify(_.basicResources.sugar.maxAmount)
      .using(_ + bonus.maxSugar)
      .modify(_.basicResources.colonyPoints.maxAmount)
      .using(_ + bonus.maxColonyPoints)
      .modify(_.nestAttributes.maxScouts)
      .using(_ + bonus.maxScouts)
  }

  def colonyPointsPerTick(chambers: AllChamberData, workersCount: Int): Long =
    Seq(
      chambers.nestChamber,
      chambers.queenChamber,
      chambers.foodStorageChamber,
      chambers.nurseryChamber,
    )
      .map { chamber =>
        val perWorkerTick = forChamber(chamber.chamberType).colonyPointsEachWorker /
          Constants.TicksPerSecond
        chamber.level.toLong * workersCount * perWorkerTick
      }
      .sum

}
