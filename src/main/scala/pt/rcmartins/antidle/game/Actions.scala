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
      DefaultWorkerAssignment.assign(allData, antTask, amountU)
    }

  def selectDefaultTask(task: Option[AntTask]): Unit =
    actionUpdater.writer.onNext(DefaultWorkerAssignment.select(_, task))

  def configureQueenRoutine(enabled: Boolean, target: Int, reserve: Long): Unit =
    actionUpdater.writer.onNext(AutomaticBrood.configure(_, enabled, target, reserve))

  def retrieveCarcass(index: Int): Unit =
    actionUpdater.writer.onNext(CarcassProgression.retrieve(_, index))

  val layEggActionEnabled: Signal[Boolean] =
    sugarSignal
      .combineWith(eggsCountSignal, maxEggs, antAndBroodCount, maxAnts)
      .map { case (sugar, eggsCount, maxEggs, antCount, maxAnt) =>
        sugar >= Constants.LayEggSugarCost && eggsCount < maxEggs && antCount < maxAnt
      }

  def workerEggAmount(ants: AntsData, nest: NestAttributes, sugar: Long, requested: Int): Int = {
    val slots = Math.min(nest.maxEggsCount - ants.eggsAndLarvae.count(_.isEgg), nest.maxAntsCount - ants.populationCount)
    Math.max(0L, Math.min(requested.toLong, Math.min(slots.toLong, sugar / Constants.LayEggSugarCost))).toInt
  }

  def layEggAction(maxMultiplierUsed: Int): Unit =
    actionUpdater.writer.onNext { allData =>
      val mult = workerEggAmount(allData.ants, allData.nestAttributes, allData.basicResources.sugar.amount, maxMultiplierUsed)

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
    actionUpdater.writer.onNext(_.hasResourcesUpdate(actionCost)(allData =>
      UpgradeVisibility.reconcile(allData.modify(_.upgrades).using(unlockAction))))

  def getChamber(chamberType: ChamberType): Signal[Chamber] =
    chambersSignal.map { chambers =>
      chamberType match {
        case ChamberType.Nest        => chambers.nestChamber
        case ChamberType.Queen       => chambers.queenChamber
        case ChamberType.FoodStorage => chambers.foodStorageChamber
        case ChamberType.Nursery     => chambers.nurseryChamber
        case ChamberType.Aphid       => chambers.aphidChamber
      }
    }

  def getActionBonus(chamberType: ChamberType): Signal[ActionBonus] =
    upgradesSignal.map(upgrades => ChamberBonuses.forChamber(chamberType, upgrades)).distinct

  def buildChamberCost(chamberType: ChamberType): Signal[ActionCost] =
    getChamber(chamberType)
      .map(_.level)
      .distinct
      .map(level => ActionCost(
        tunnelingSpace = getChamberTunnelingSpaceNeeded(chamberType, level),
        protein = if (chamberType == ChamberType.Nursery) NurseryProteinCost * (level + 1) else if (chamberType == ChamberType.Aphid) 2L * u * (level + 1L) else 0L,
      ))

  def raiseScout(): Unit =
    actionUpdater.writer.onNext(ScoutProgression.raise)

  def explore(workers: Long, destination: ExplorationDestination): Unit =
    actionUpdater.writer.onNext(ExplorationHelper.start(_, workers, destination))

  def upgradeExpeditionSupplies(): Unit =
    actionUpdater.writer.onNext(ExplorationProgression.upgradeSupplies)

}
