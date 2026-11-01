package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model._

import scala.util.Random

object Actions {

  def antDeathThisTick(seed: Long, sugarCumulativeDebt: Long): Boolean =
    if (sugarCumulativeDebt < AntDeathRiskThreshold) false
    else if (sugarCumulativeDebt >= AntDeathRiskThreshold * (MaxExponent - 1)) true
    else {
      val exponent = (sugarCumulativeDebt / AntDeathRiskThreshold).toInt
      val chance = exponent0_95(exponent)
      new Random(seed).nextDouble() > chance
    }

  def reduceAntTask(antTask: AntTask, amountU: Long): Unit =
    actionUpdater.writer.onNext { allData =>
      allData.modify(_.ants).using(_.removeFromTask(antTask, amountU))
    }

  def incrementAntTask(antTask: AntTask, amountU: Long): Unit =
    actionUpdater.writer.onNext { allData =>
      allData.modify(_.ants).using(_.addToTask(antTask, amountU))
    }

  val layEggActionEnabled: Signal[Boolean] =
    sugarSignal
      .combineWith(eggsCountSignal, maxEggs, antAndBroodCount, maxWorkers)
      .map { case (sugar, eggsCount, maxEggs, antCount, maxAnt) =>
        sugar >= Constants.LayEggSugarCost && eggsCount < maxEggs && antCount < maxAnt
      }

  def layEggAction(maxMultiplierUsed: Int): Unit =
    actionUpdater.writer.onNext { allData =>
      val maxEggs: Int =
        Math.min(
          allData.nestAttributes.maxEggsCount,
          allData.nestAttributes.maxWorkersCount - allData.ants.workersAndLarvaeCount,
        )

      val mult: Int =
        Math.min(
          Math.min(maxMultiplierUsed, maxEggs),
          (allData.basicResources.sugar.amount / Constants.LayEggSugarCost).toInt,
        )

      allData
        .modify(_.basicResources.sugar)
        .using(_ - Constants.LayEggSugarCost * mult)
        .modify(_.ants.eggsAndLarvae)
        .using(_ ++ Seq.fill(mult)(AntBrood.Egg(allData.world.currentTick)))
        .modify(_.statistics.eggsCount)
        .using(_.addValue(mult))
    }

  def addBuildTask(buildTask: BuildTask): Unit =
    actionUpdater.writer.onNext { allData =>
      if (allData.nestAttributes.buildQueue.sizeIs < allData.nestAttributes.maxBuildQueue)
        allData
          .modify(_.nestAttributes.buildQueue)
          .using(_ :+ buildTask)
      else
        allData
    }

  def removeBuildTask(index: Int): Unit =
    actionUpdater.writer.onNext { allData =>
      allData
        .modify(_.nestAttributes.buildQueue)
        .using(_.patch(index, Nil, 1))
    }

  def unlockUpgrade(actionCost: ActionCost, unlockAction: UpgradesData => UpgradesData): Unit =
    actionUpdater.writer.onNext { allData =>
      allData
        .spendResources(actionCost)
        .modify(_.upgrades)
        .using(unlockAction)
    }

  def getChamber(chamberType: ChamberType): Signal[Chamber] =
    chambersSignal.map { chambers =>
      chamberType match {
        case ChamberType.Nest        => chambers.nestChamber
        case ChamberType.Queen       => chambers.queenChamber
        case ChamberType.FoodStorage => chambers.foodStorageChamber
        case ChamberType.Nursery     => chambers.nurseryChamber
      }
    }

  def getActionBonus(chamberType: ChamberType): Signal[ActionBonus] =
    Val(ChamberBonuses.forChamber(chamberType))

  def buildChamberCost(chamberType: ChamberType): Signal[ActionCost] =
    getChamber(chamberType)
      .map(_.level)
      .distinct
      .map(level => ActionCost(tunnelingSpace = getChamberTunnelingSpaceNeeded(chamberType, level)))

  def explore(actionCost: ActionCost): Unit =
    actionUpdater.writer.onNext {
      _.hasResourcesUpdate(actionCost) { allData =>
        allData
          .modify(_.ants)
          .using(_.addToTask(AntTask.Explorer, actionCost.idleWorkers))
          .modify(_.exploration.explorationParties)
          .using(
            ExplorationParty.create(
              actionCost.idleWorkers,
              allData.world.currentTick,
              Constants.ExplorationTimeTicks,
            ) :: _
          )
          .modify(_.statistics.explorationsCount)
          .using(_.addValue(1))
          .modify(_.statistics.explorationsWorkersSentU)
          .using(_.addValue(actionCost.idleWorkers))
      }

    }

}
