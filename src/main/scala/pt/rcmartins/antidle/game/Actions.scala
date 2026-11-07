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

  def promoteReplete(): Unit = actionUpdater.writer.onNext(RepleteProgression.promote)

  def establishDryClearingOutpost(): Unit = actionUpdater.writer.onNext(OutpostProgression.establish)

  def expandDryClearingOutpost(): Unit = actionUpdater.writer.onNext(OutpostProgression.expand)

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
    actionUpdater.writer.onNext(Construction.enqueue(_, buildTask.chamberType))

  def removeBuildTask(index: Int): Unit =
    actionUpdater.writer.onNext(Construction.cancel(_, index))

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
        case ChamberType.Replete     => chambers.repleteChamber
      }
    }

  def getActionBonus(chamberType: ChamberType): Signal[ActionBonus] =
    upgradesSignal.map(upgrades => ChamberBonuses.forChamber(chamberType, upgrades)).distinct

  def buildChamberCost(chamberType: ChamberType): Signal[ActionCost] =
    nestSignal.map(nest => Construction.nextCost(nest, chamberType)).distinct

  def raiseScout(): Unit =
    actionUpdater.writer.onNext(ScoutProgression.raise)

  def explore(workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Unit =
    actionUpdater.writer.onNext(ExplorationHelper.start(_, workers, destination, extraScouts))

  def upgradeExpeditionSupplies(): Unit =
    actionUpdater.writer.onNext(ExplorationProgression.upgradeSupplies)

}
