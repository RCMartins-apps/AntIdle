package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.{AllData, AntTask, BuildTask}

import scala.util.chaining.scalaUtilChainingOps

object TickUpdater {

  def updateAllData(initial: AllData, nextSaveTickOpt: Option[Long]): AllData = {
    var surplusSugarU = 0L

    val updates: Seq[AllData => AllData] =
      Seq[AllData => AllData](
        // Tick update
        allData => allData.modify(_.world.currentTick).using(_ + 1),
        PopulationAging.tick,
        // Brood maturation is shared by workers and the new scout caste.
        ScoutProgression.growBrood,
        // Ants income from tasks
        allData => {
          def countWorkers(task: AntTask): Long =
            allData.ants.tasks.find(_._1 == task).map(_._2 / u).getOrElse(0L)

          val sugarWorkers: Long = countWorkers(AntTask.SugarCollector)
          val tunnelerWorkers: Long = countWorkers(AntTask.Tunneler)

          val totalSugarToGain: Long = Foraging.productionPerTick(sugarWorkers, allData.upgrades) +
            AphidHusbandry.productionPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources.protein.amount)

          val sugarToGive: Long =
            Math.min(allData.basicResources.sugar.freeSpace, totalSugarToGain)

          surplusSugarU += totalSugarToGain - sugarToGive

          val tunnelingSpaceToGive: Long =
            tunnelerWorkers * ColonyDevelopment.tunnelingPerWorkerTick(allData.upgrades)

          val proteinUsed = AphidHusbandry.proteinPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources.protein.amount)
          val harvest = OutpostProgression.harvestPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources)
          allData.modify(_.basicResources.protein).using(_ - proteinUsed)
            .modify(_.basicResources.water).using(_ - harvest.water).giveResources(
            sugar = sugarToGive,
            tunnelingSpace = tunnelingSpaceToGive,
            protein = harvest.protein,
          )
        },
        Construction.tick,
        // Exploration parties update
        allData => {
          val finishedParties = allData.exploration.explorationParties.filter(_.targetTick <= allData.currentTick)
          finishedParties.foldLeft(allData) { case (current, party) =>
            ExplorationHelper.exploreBonus(current, party)
          }
        },
        CarcassProgression.returnCarcasses,
        // Charge upkeep before automatic brood can spend the remaining sugar.
        beforeUpkeep => {
          val (allData, foodOffset) = CarcassProgression.consumeFood(beforeUpkeep)
          val sugarUpkeep = ScoutProgression.upkeepCount(allData.ants, allData.exploration) *
            Constants.antsSugarUpkeepTick - foodOffset
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
                      {
                        val survivors = PopulationAging.starveOne(allData)
                        val losses = (allData.ants.workers - survivors.workers + allData.ants.scouts - survivors.scouts + allData.ants.repletes - survivors.repletes) / u
                        val updated = allData.copy(ants = survivors).modify(_.statistics.starvationDeaths).using(_.addValue(losses))
                        if (losses > 0L) updated.addMessage("An ant died of starvation.") else updated
                      }
                    else
                      allData
                  }
            }
        },
        AutomaticBrood.tick,
        PopulationAging.reconcile,
        UpgradeVisibility.reconcile,
        DefaultWorkerAssignment.normalize,
        ResourceVisibility.reconcile,
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
