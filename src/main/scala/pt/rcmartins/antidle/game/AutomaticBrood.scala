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
    else if (EmergencyRecovery.broodCount(ants) > 0) "Rearing one emergency small worker"
    else if (routine.targetWorkers > 0 && EmergencyRecovery.needed(ants)) {
      if (EmergencyRecovery.canRaise(ants, sugar - routine.sugarReserve)) "Raising one emergency small worker"
      else EmergencyRecovery.status(ants)
    }
    else if (ants.totalWorkers / u + BroodProgression.incoming(ants) + ants.eggOrders >= routine.targetWorkers) "Target covered"
    else if (ants.populationCount >= nest.maxAntsCount) "Population limit reached"
    else if (ants.eggsAndLarvae.size >= BroodProgression.capacity(nest)) "Brood places full"
    else if (sugar - routine.sugarReserve < BroodProgression.eggCost(ants)) "Keeping sugar reserve"
    else if (ants.layingCooldown > 0L) "Queen laying cooldown"
    else "Raising worker eggs"
  }

  def tick(initial: AllData): AllData = {
    val data = EmergencyRecovery.tick(initial)
    val routine = data.ants.queenRoutine
    val covered = data.ants.totalWorkers / u + BroodProgression.incoming(data.ants) + data.ants.eggOrders
    if (EmergencyRecovery.needed(data.ants) || EmergencyRecovery.broodCount(data.ants) > 0 && data.ants.totalWorkers == 0L) data
    else if (!data.upgrades(QueensRoutine).unlocked || !routine.enabled || !data.unlocks.actions.canLayEggs || covered >= routine.targetWorkers) data
    else BroodProgression.lay(data, routine.sugarReserve)
  }
}
