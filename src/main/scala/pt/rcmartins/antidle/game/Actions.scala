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
      AgingJobReplacement.cancel(allData, antTask).modify(_.ants).using(_.removeFromTask(antTask, amountU))
    }

  def incrementAntTask(antTask: AntTask, amountU: Long): Unit =
    actionUpdater.writer.onNext { allData =>
      DefaultWorkerAssignment.assign(allData, antTask, amountU)
    }

  def selectDefaultTask(task: Option[AntTask]): Unit =
    actionUpdater.writer.onNext(DefaultWorkerAssignment.select(_, task))

  def cancelAgingJobReplacements(): Unit = actionUpdater.writer.onNext(AgingJobReplacement.cancelAll)

  def configureQueenRoutine(enabled: Boolean, target: Int, reserve: Long): Unit =
    actionUpdater.writer.onNext(AutomaticBrood.configure(_, enabled, target, reserve))

  def raiseEmergencyWorker(): Unit = actionUpdater.writer.onNext(EmergencyRecovery.raise)

  def promoteReplete(): Unit = actionUpdater.writer.onNext(RepleteProgression.promote)

  def establishDryClearingOutpost(): Unit = actionUpdater.writer.onNext(OutpostProgression.establish)

  def expandDryClearingOutpost(): Unit = actionUpdater.writer.onNext(OutpostProgression.expand)

  def purchaseSharedPaths(): Unit = actionUpdater.writer.onNext(Prestige.purchase)

  def foundDaughterColony(): Unit = actionUpdater.writer.onNext { data =>
    val next = Prestige.found(data)
    if (next != data) {
      Utils.pause = true
      scala.scalajs.js.timers.setTimeout(0) {
        saves.SaveLoad.reloadDataFromLoadedSave(next, messageAlert = None)
        saves.SaveLoad.saveToLocalStorage(next)
      }
    }
    next
  }

  def claimTerritory(workers: Int): Unit = actionUpdater.writer.onNext(TerritoryProgression.claim(_, workers))
  def reinforceTerritory(): Unit = actionUpdater.writer.onNext(TerritoryProgression.reinforce)
  def maintainTerritory(enabled: Boolean): Unit = actionUpdater.writer.onNext(TerritoryProgression.setMaintain(_, enabled))
  def withdrawTerritory(): Unit = actionUpdater.writer.onNext(TerritoryProgression.withdraw)

  def markSugarTrail(): Unit = actionUpdater.writer.onNext(PheromoneTrails.mark)
  def assignTrailWorkers(delta: Long): Unit = actionUpdater.writer.onNext(PheromoneTrails.assign(_, delta))
  def recallSugarTrail(): Unit = actionUpdater.writer.onNext(PheromoneTrails.recall)

  def raiseRoyalEgg(): Unit = actionUpdater.writer.onNext(RoyalBroodProgression.start)

  def setRoyalCarePaused(paused: Boolean): Unit = actionUpdater.writer.onNext(RoyalBroodProgression.setPaused(_, paused))

  def retrieveCarcass(index: Int): Unit =
    actionUpdater.writer.onNext(CarcassProgression.retrieve(_, index))

  def assignSmallWorker(task: AntTask, delta: Long): Unit = actionUpdater.writer.onNext(BroodProgression.assignSmall(_, task, delta))
  def cancelEggOrders(): Unit = actionUpdater.writer.onNext(BroodProgression.cancel)
  val layEggActionEnabled: Signal[Boolean] = antsSignal.combineWith(nestSignal, sugarSignal).map {
    case (ants, nest, sugar) => BroodProgression.orderAmount(ants, nest, sugar.amount, 1) > 0
  }
  def workerEggAmount(ants: AntsData, nest: NestAttributes, sugar: Long, requested: Int): Int =
    BroodProgression.orderAmount(ants, nest, sugar, requested)
  def layEggAction(maxMultiplierUsed: Int): Unit = actionUpdater.writer.onNext(BroodProgression.order(_, maxMultiplierUsed))

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
        case ChamberType.RoyalNursery => chambers.royalNurseryChamber
      }
    }

  def getActionBonus(chamberType: ChamberType): Signal[ActionBonus] =
    antsSignal.combineWith(nestSignal, upgradesSignal).map { case (ants, nest, upgrades) =>
      ChamberBonuses.forDisplay(chamberType, ants, nest, upgrades)
    }.distinct

  def buildChamberCost(chamberType: ChamberType): Signal[ActionCost] =
    nestSignal.map(nest => Construction.nextCost(nest, chamberType)).distinct

  def raiseScout(amount: Int = 1): Unit =
    actionUpdater.writer.onNext(data => ScoutProgression.raise(data, amount))

  def explore(workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Unit =
    actionUpdater.writer.onNext(ExplorationHelper.start(_, workers, destination, extraScouts))

  def upgradeExpeditionSupplies(): Unit =
    actionUpdater.writer.onNext(ExplorationProgression.upgradeSupplies)

}
