package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.RepleteStorage

object RepleteProgression {
  val WaterPerReplete: Long = 100L * u
  val cost: ActionCost = ActionCost(sugar = 100 * u, protein = 5 * u, idleWorkers = u)

  def capacity(data: AllData): Long = BasicResources.initial.water.maxAmount + data.ants.repletes / u * WaterPerReplete
  def slots(data: AllData): Long = Math.max(0L, data.nestAttributes.chambers.repleteChamber.level.toLong - data.ants.repletes / u)

  def reconcile(data: AllData): AllData = data.modify(_.basicResources.water.maxAmount).setTo(capacity(data))

  def canPromote(data: AllData): Boolean =
    data.upgrades(RepleteStorage).unlocked && slots(data) > 0L && data.hasResources(cost)

  def promote(data: AllData): AllData = {
    if (!canPromote(data)) data
    else {
      val ready = PopulationAging.tick(data)
      if (!canPromote(ready)) reconcile(ready)
      else {
        val moved = PopulationAging.promoteReplete(ready)
        val borrowed = Math.max(0L, u - moved.ants.idleWorkersCount)
        val promoted = moved.spendResources(cost).modify(_.ants).using(a => a.copy(
          workers = a.workers - u, repletes = a.repletes + u,
          tasks = a.tasks.map { case (task, amount) => task -> (if (a.effectiveDefaultTask.contains(task)) amount - borrowed else amount) }))
          .modify(_.statistics.repletesPromoted).using(_.addValue(1))
        DefaultWorkerAssignment.normalize(reconcile(promoted))
      }
    }
  }
}
