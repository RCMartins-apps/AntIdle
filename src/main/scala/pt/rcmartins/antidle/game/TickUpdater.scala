package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants.{SugarImprovementBonusPerc, u}
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import pt.rcmartins.antidle.model.{AllData, AntBrood, AntTask, BuildTask}

import scala.util.chaining.scalaUtilChainingOps

object TickUpdater {

  def updateAllData(initial: AllData, nextSaveTickOpt: Option[Long]): AllData = {
    var surplusSugarU = 0L

    val updates: Seq[AllData => AllData] =
      Seq[AllData => AllData](
        // Tick update
        allData => allData.modify(_.world.currentTick).using(_ + 1),
        // Eggs -> Ants update
        allData => {
          val updatedEggsAndLarvae: Seq[Either[Unit, AntBrood]] =
            allData.ants.eggsAndLarvae.map {
              case AntBrood.Egg(initialTick)
                  if allData.currentTick >= initialTick + AntBrood.defaultTicksToGrow =>
                Left(())
              case other =>
                Right(other)
            }
          val newWorkers = updatedEggsAndLarvae.count(_.isLeft) * u

          allData
            .modify(_.ants.eggsAndLarvae)
            .setTo(updatedEggsAndLarvae.flatMap(_.toOption))
            .modify(_.ants.workers)
            .usingIf(newWorkers > 0)(_ + newWorkers)
            .modify(_.statistics.maxAliveWorkers)
            .usingIf(newWorkers > 0)(_.max(allData.ants.workersCount + (newWorkers / u).toInt))
        },
        // Ants income from tasks
        allData => {
          def countWorkers(task: AntTask): Long =
            allData.ants.tasks.find(_._1 == task).map(_._2 / u).getOrElse(0L)

          val sugarWorkers: Long = countWorkers(AntTask.SugarCollector)
          val tunnelerWorkers: Long = countWorkers(AntTask.Tunneler)

          val sugarBonusMultiplier: Double =
            (if (allData.upgrades(ImproveSugarCollectorTask1).unlocked) SugarImprovementBonusPerc else 1.0) *
              (if (allData.upgrades(ImproveSugarCollectorTask2).unlocked) SugarImprovementBonusPerc else 1.0) *
              (if (allData.upgrades(ImproveSugarCollectorTask3).unlocked) SugarImprovementBonusPerc else 1.0)

          val totalSugarToGain: Long =
            (sugarWorkers * Constants.defaultTaskCollectSugarTick * sugarBonusMultiplier).toLong

          val sugarToGive: Long =
            Math.min(allData.basicResources.sugar.freeSpace, totalSugarToGain)

          surplusSugarU += totalSugarToGain - sugarToGive

          val tunnelingSpaceToGive: Long =
            tunnelerWorkers * Constants.defaultTaskTunnelingSpaceTick

          allData.giveResources(
            sugar = sugarToGive,
            tunnelingSpace = tunnelingSpaceToGive,
          )
        },
        // Build Queue update
        allData => {
          allData.nestAttributes.buildQueue match {
            case finishBuilding :: otherBuildings
                if allData.hasResources(finishBuilding.actionCost) =>
              allData
                .spendResources(finishBuilding.actionCost)
                .modify(_.nestAttributes.buildQueue)
                .setTo(otherBuildings)
                .pipe(ChamberBonuses.completeUpgrade(_, finishBuilding.chamberType))
            case _ =>
              allData
          }
        },
        // Colony points update
        allData => {
          allData.giveResources(
            colonyPoints = ChamberBonuses.colonyPointsPerTick(
              allData.nestAttributes.chambers,
              allData.ants.workersCount,
            ),
          )
        },
        // Exploration parties update
        allData => {
          val (updatedExplorationParties, finishedParties) =
            allData.exploration.explorationParties.partition(_.targetTick > allData.currentTick)

          allData
            .modify(_.exploration.explorationParties)
            .setTo(updatedExplorationParties)
            .pipe { allData =>
              finishedParties.foldLeft(allData) { case (allData, party) =>
                ExplorationHelper.exploreBonus(allData, party)
              }
            }
        },
        // Sugar upkeep + death ants by starving (should be the last data update)
        allData => {
          val sugarUpkeep = allData.ants.upkeepWorkersCount * Constants.antsSugarUpkeepTick
          val sugarRemaining =
            Math.min(
              allData.basicResources.sugar.maxAmount,
              allData.basicResources.sugar.amount + surplusSugarU - sugarUpkeep,
            )

          val sugarUsedFromSurplus: Long =
            Math.min(sugarUpkeep, surplusSugarU)

          allData
            .modify(_.basicResources.sugar.amount)
            .setTo(Math.max(0, sugarRemaining))
            .modify(_.statistics.sugarU)
            .usingIf(sugarUsedFromSurplus > 0)(_.addValue(sugarUsedFromSurplus))
            .pipe { allData =>
              if (sugarRemaining >= 0)
                allData
                  .modify(_.ants.sugarCumulativeDebt)
                  .using {
                    case debt if debt > 0 => Math.max(0, debt - sugarRemaining / 2)
                    case _                => 0
                  }
              else
                allData
                  .modify(_.ants.sugarCumulativeDebt)
                  .using { sugarCumulativeDebt => sugarCumulativeDebt - sugarRemaining }
                  .pipe { allData =>
                    if (
                      Actions.antDeathThisTick(
                        allData.world.saveSeed + allData.currentTick,
                        allData.ants.sugarCumulativeDebt,
                      )
                    )
                      allData
                        .addMessage("An ant died of starvation.")
                        .modify(_.ants)
                        .using { antsData =>
                          (
                            if (antsData.idleWorkersCount == 0)
                              antsData.remove1WorkerFromLastUpkeepTask
                            else
                              antsData
                          )
                            .modify(_.workers)
                            .using(_ - 1 * u)
                            .modify(_.sugarCumulativeDebt)
                            .using(debt => Math.max(0, debt - Constants.AntDeathRiskThreshold))
                        }
                    else
                      allData
                  }
            }
        },
        // This is the last step to save data if necessary
        allData => {
          nextSaveTickOpt.foreach {
            case nextSaveTick if allData.currentTick >= nextSaveTick =>
              val lastFromLocalStorageOpt = SaveLoad.loadFromLocalStorage()
              if (lastFromLocalStorageOpt.forall(_.world.currentTick < allData.currentTick)) {
                SaveLoad.saveToLocalStorage(allData)
                Utils.lastSavedTick.set(allData.currentTick)
              }
            case _ =>
          }
          allData
        },
      )

    updates.foldLeft(initial) { case (allData, update) => update(allData) }
  }

}
