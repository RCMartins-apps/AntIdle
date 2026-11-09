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
        TerritoryProgression.tick,
        PheromoneTrails.tick,
        // Ants income from tasks
        allData => {
          def countWorkers(task: AntTask): Long =
            allData.ants.tasks.find(_._1 == task).map(_._2 / u).getOrElse(0L)

          val sugarWorkers: Long = countWorkers(AntTask.SugarCollector)
          val tunnelerWorkers: Long = countWorkers(AntTask.Tunneler)

          val totalSugarToGain: Long = Foraging.production(allData.ants, allData.upgrades) +
            AphidHusbandry.productionPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources.protein.amount)

          val sugarToGive: Long =
            Math.min(allData.basicResources.sugar.freeSpace, totalSugarToGain)

          surplusSugarU += totalSugarToGain - sugarToGive

          val tunnelingSpaceToGive: Long =
            (tunnelerWorkers * 2L + allData.ants.smallTunnelers / u) * ColonyDevelopment.tunnelingPerWorkerTick(allData.upgrades) / 2L

          val proteinUsed = AphidHusbandry.proteinPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources.protein.amount)
          val harvest = OutpostProgression.harvestPerTick(allData.ants, allData.nestAttributes, allData.upgrades, allData.basicResources)
          allData.modify(_.basicResources.protein).using(_ - proteinUsed)
            .modify(_.basicResources.water).using(_ - harvest.water).giveResources(
            sugar = sugarToGive,
            tunnelingSpace = tunnelingSpaceToGive,
            protein = harvest.protein + BroodProgression.scavenged(allData.ants, allData.basicResources.protein.freeSpace - harvest.protein + proteinUsed),
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
        // Charge adult upkeep first, then royal care, before automatic worker eggs.
        beforeUpkeep => {
          val (allData, foodOffset) = CarcassProgression.consumeFood(beforeUpkeep)
          val sugarUpkeep = ScoutProgression.upkeepPerTick(allData.ants, allData.exploration) - foodOffset
          val sugarRemaining =
            Math.min(
              allData.basicResources.sugar.maxAmount,
              allData.basicResources.sugar.amount + surplusSugarU - sugarUpkeep,
            )
          // Let fresh surplus feed royal brood before storage overflow is discarded.
          val sugarBeforeCare = Math.max(0L, allData.basicResources.sugar.amount + surplusSugarU - sugarUpkeep)

          val sugarUsedFromSurplus: Long =
            Math.min(sugarUpkeep, surplusSugarU)

          val afterUpkeep = allData
            .modify(_.basicResources.sugar.amount)
            .setTo(sugarBeforeCare)
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
                        val losses = (allData.ants.workers - survivors.workers + allData.ants.smallWorkers - survivors.smallWorkers + allData.ants.scouts - survivors.scouts +
                          allData.ants.repletes - survivors.repletes + allData.ants.daughterQueens - survivors.daughterQueens) / u
                        val updated = allData.copy(ants = survivors).modify(_.statistics.starvationDeaths).using(_.addValue(losses))
                        if (allData.ants.daughterQueens > survivors.daughterQueens) updated.addMessage("A daughter queen died of starvation.")
                        else if (losses > 0L) updated.addMessage("An ant died of starvation.") else updated
                      }
                    else
                      allData
                  }
            }
          val tended = BroodProgression.tick(RoyalBroodProgression.tick(afterUpkeep))
          val royalSugarSpent = afterUpkeep.basicResources.sugar.amount - tended.basicResources.sugar.amount
          val royalSurplusUsed = Math.min(royalSugarSpent, Math.max(0L, surplusSugarU - sugarUpkeep))
          tended.modify(_.basicResources.sugar.amount).using(amount => Math.min(amount, tended.basicResources.sugar.maxAmount))
            .modify(_.statistics.sugarU).usingIf(royalSurplusUsed > 0L)(_.addValue(royalSurplusUsed))
        },
        BroodProgression.layOrdered,
        AutomaticBrood.tick,
        PopulationAging.reconcile,
        UpgradeVisibility.reconcile,
        DefaultWorkerAssignment.normalize,
        UnlockUtils.reconcile,
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
