package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.QueensRoutine

object AutomaticBrood {
  def configure(data: AllData, enabled: Boolean, target: Int, reserve: Long): AllData =
    if (!data.upgrades(QueensRoutine).unlocked) data
    else data.modify(_.ants.queenRoutine).setTo(QueenRoutine(enabled, Math.max(0, Math.min(1000000, target)), Math.max(0L, reserve)))

  def status(ants: AntsData, nest: NestAttributes, sugar: Long): String = {
    val routine = ants.queenRoutine
    if (!routine.enabled) "Paused"
    else if (ants.workers / u + ants.eggsAndLarvae.count(_.isInstanceOf[AntBrood.Egg]) >= routine.targetWorkers) "Target covered"
    else if (ants.populationCount >= nest.maxAntsCount) "Population limit reached"
    else if (ants.eggsAndLarvae.count(_.isEgg) >= nest.maxEggsCount) "Egg slots full"
    else if (sugar - routine.sugarReserve < LayEggSugarCost) "Keeping sugar reserve"
    else "Raising worker eggs"
  }

  def tick(data: AllData): AllData = {
    val routine = data.ants.queenRoutine
    if (!data.upgrades(QueensRoutine).unlocked || !routine.enabled || !data.unlocks.actions.canLayEggs) data
    else {
      val incoming = data.ants.eggsAndLarvae.count(_.isInstanceOf[AntBrood.Egg])
      val needed = Math.max(0L, routine.targetWorkers.toLong - data.ants.workers / u - incoming)
      val budget = Math.max(0L, data.basicResources.sugar.amount - routine.sugarReserve)
      val count = Math.min(needed, Actions.workerEggAmount(data.ants, data.nestAttributes, budget, Int.MaxValue).toLong).toInt
      if (count == 0) data
      else data.spendResources(ActionCost(sugar = count.toLong * LayEggSugarCost))
        .modify(_.ants.eggsAndLarvae).using(_ ++ List.fill(count)(AntBrood.Egg(data.currentTick)))
        .modify(_.statistics.eggsCount).using(_.addValue(count))
    }
  }
}
