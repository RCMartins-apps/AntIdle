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
      AgingJobReplacement
        .cancel(allData, antTask)
        .modify(_.ants)
        .using(_.removeFromTask(antTask, amountU))
    }

  def incrementAntTask(antTask: AntTask, amountU: Long): Unit =
    actionUpdater.writer.onNext { allData =>
      DefaultWorkerAssignment.assign(allData, antTask, amountU)
    }

  def selectDefaultTask(task: Option[AntTask]): Unit =
    actionUpdater.writer.onNext(DefaultWorkerAssignment.select(_, task))

  def cancelAgingJobReplacements(): Unit =
    actionUpdater.writer.onNext(AgingJobReplacement.cancelAll)

  def configureQueenRoutine(enabled: Boolean, target: Int, reserve: Long): Unit =
    actionUpdater.writer.onNext(AutomaticBrood.configure(_, enabled, target, reserve))

  def raiseEmergencyWorker(): Unit = actionUpdater.writer.onNext(EmergencyRecovery.raise)

  def promoteReplete(): Unit = actionUpdater.writer.onNext(RepleteProgression.promote)

  def buildMidden(): Unit = actionUpdater.writer.onNext(Midden.build)

  def establishDryClearingOutpost(): Unit =
    actionUpdater.writer.onNext(OutpostProgression.establish)

  def specializeDryClearingOutpost(station: String): Unit =
    actionUpdater.writer.onNext(data => OutpostProgression.specialize(data, station))

  def expandDryClearingOutpost(): Unit = actionUpdater.writer.onNext(OutpostProgression.expand)

  def purchaseSharedPaths(): Unit = actionUpdater.writer.onNext(Prestige.purchase)

  def purchaseUnderstanding(id: String): Unit =
    actionUpdater.writer.onNext(Prestige.purchase(_, id))

  def foundDaughterColony(selected: Seq[String] = Nil, drySeason: Boolean = false): Unit =
    saves.SaveLoad.withWriteLock {
      actionUpdater.writer.onNext { data =>
        val next = Prestige.found(data, selected, drySeason)
        if (next == data) data
        else
          saves.SaveLoad.saveToLocalStorage(next) match {
            case Left(saves.SaveLoad.Conflict) => data
            case result =>
              Utils.pause = true
              scala.scalajs.js.timers.setTimeout(0) {
                saves.SaveLoad.reloadDataFromLoadedSave(next, messageAlert = None)
                result.left.foreach(saves.SaveLoad.showError)
              }
              next
          }
      }
    }

  def claimTerritory(workers: Int, guards: Int): Unit =
    actionUpdater.writer.onNext(TerritoryProgression.claim(_, workers, guards))

  def planTerritoryGuards(guards: Int): Unit =
    actionUpdater.writer.onNext(TerritoryProgression.planGuards(_, guards))

  def reinforceTerritory(): Unit = actionUpdater.writer.onNext(TerritoryProgression.reinforce)
  def maintainTerritory(enabled: Boolean): Unit =
    actionUpdater.writer.onNext(TerritoryProgression.setMaintain(_, enabled))

  def withdrawTerritory(): Unit = actionUpdater.writer.onNext(TerritoryProgression.withdraw)

  def assignTrailWorkers(destination: String, delta: Long): Unit =
    actionUpdater.writer.onNext(PheromoneTrails.adjust(_, destination, delta))

  def recallTrail(destination: String): Unit =
    actionUpdater.writer.onNext(PheromoneTrails.recall(_, destination))

  def maximizeTrail(destination: String): Unit =
    actionUpdater.writer.onNext(PheromoneTrails.maximize(_, destination))

  def raiseRoyalEgg(): Unit = actionUpdater.writer.onNext(RoyalBroodProgression.start)

  def setRoyalCarePaused(paused: Boolean): Unit =
    actionUpdater.writer.onNext(RoyalBroodProgression.setPaused(_, paused))

  def retrieveCarcass(index: Int): Unit =
    actionUpdater.writer.onNext(CarcassProgression.retrieve(_, index))

  def assignSmallWorker(task: AntTask, delta: Long): Unit =
    actionUpdater.writer.onNext(BroodProgression.assignSmall(_, task, delta))

  def cancelEggOrders(): Unit = actionUpdater.writer.onNext(BroodProgression.cancel)
  def setBroodPriority(priority: BroodPriority): Unit =
    actionUpdater.writer.onNext(BroodProgression.setPriority(_, priority))

  val layEggActionEnabled: Signal[Boolean] = antsSignal.combineWith(nestSignal).map {
    case (ants, nest) => BroodProgression.orderAmount(ants, nest, 1) > 0
  }

  def workerEggAmount(ants: AntsData, nest: NestAttributes, requested: Int): Int =
    BroodProgression.orderAmount(ants, nest, requested)

  def layEggAction(maxMultiplierUsed: Int): Unit =
    actionUpdater.writer.onNext(BroodProgression.order(_, maxMultiplierUsed))

  def addBuildTask(buildTask: BuildTask): Unit =
    actionUpdater.writer.onNext(Construction.enqueue(_, buildTask.chamberType))

  def removeBuildTask(index: Int): Unit =
    actionUpdater.writer.onNext(Construction.cancel(_, index))

  def unlockUpgrade(upgrade: UpgradesData.UpgradeType): Unit =
    actionUpdater.writer.onNext(UpgradeResearch.purchase(_, upgrade))

  def getChamber(chamberType: ChamberType): Signal[Chamber] =
    chambersSignal.map { chambers =>
      chamberType match {
        case ChamberType.Nest         => chambers.nestChamber
        case ChamberType.Queen        => chambers.queenChamber
        case ChamberType.FoodStorage  => chambers.foodStorageChamber
        case ChamberType.Nursery      => chambers.nurseryChamber
        case ChamberType.Aphid        => chambers.aphidChamber
        case ChamberType.Replete      => chambers.repleteChamber
        case ChamberType.RoyalNursery => chambers.royalNurseryChamber
      }
    }

  def getActionBonus(chamberType: ChamberType): Signal[ActionBonus] =
    antsSignal
      .combineWith(nestSignal, upgradesSignal)
      .map { case (ants, nest, upgrades) =>
        ChamberBonuses.forDisplay(chamberType, ants, nest, upgrades)
      }
      .distinct

  def buildChamberCost(chamberType: ChamberType): Signal[ActionCost] =
    nestSignal.map(nest => Construction.nextCost(nest, chamberType)).distinct

  def raiseScout(amount: Int = 1): Unit =
    actionUpdater.writer.onNext(data => ScoutProgression.raise(data, amount))

  def explore(workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Unit =
    actionUpdater.writer.onNext(ExplorationHelper.start(_, workers, destination, extraScouts))

  def relocateAphids(): Unit = actionUpdater.writer.onNext(AphidRelocation.start)

  def orderSurveys(site: String, count: Int): Unit =
    actionUpdater.writer.onNext(SurveyOrders.start(_, site, count))

  def cancelSurveys(): Unit = actionUpdater.writer.onNext(SurveyOrders.cancel)

  def scoutMap(site: String, count: Int): Unit =
    actionUpdater.writer.onNext(ExplorationMap.scout(_, site, count))

}
