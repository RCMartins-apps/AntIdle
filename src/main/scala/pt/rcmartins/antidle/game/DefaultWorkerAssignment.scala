package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object DefaultWorkerAssignment {
  def capacity(task: AntTask, nest: NestAttributes): Long = task match {
    case AntTask.AphidTender => AphidHusbandry.capacity(nest)
    case AntTask.SeedForager => OutpostProgression.capacity(nest)
    case AntTask.Scavenger => 2L * pt.rcmartins.antidle.game.Constants.u
    case AntTask.BroodNurse => nest.chambers.nurseryChamber.level.toLong * 5L * pt.rcmartins.antidle.game.Constants.u
    case AntTask.RoyalNurse => RoyalBroodProgression.capacity(nest)
    case _ => Long.MaxValue
  }

  def availableCapacity(ants: AntsData, task: AntTask, nest: NestAttributes): Long = {
    val small = if (task == AntTask.Scavenger) ants.smallScavengers else if (task == AntTask.BroodNurse) ants.smallNurses else 0L
    Math.max(0L, capacity(task, nest) - small)
  }

  def select(data: AllData, task: Option[AntTask]): AllData =
    if (!data.upgrades(UpgradeType.DefaultWorkers).unlocked) data
    else normalize(data.modify(_.ants).using(_.withDefaultTask(task)))

  def assign(initial: AllData, task: AntTask, amount: Long): AllData = {
    val data = AgingJobReplacement.cancel(initial, task)
    val small = if (task == AntTask.Scavenger) data.ants.smallScavengers else if (task == AntTask.BroodNurse) data.ants.smallNurses else 0L
    val room = Math.max(0L, capacity(task, data.nestAttributes) - small - data.ants.taskWorkers(task))
    normalize(data.modify(_.ants).using(_.addToTask(task, Math.max(0L, Math.min(amount, room)))))
  }

  private def clamp(data: AllData): AllData = {
    val limit = if (data.upgrades(UpgradeType.AphidTending).unlocked) AphidHusbandry.capacity(data.nestAttributes) else 0L
    data.modify(_.ants.tasks).using(_.map { case (task, amount) =>
      task -> (if (task == AntTask.AphidTender) Math.min(amount, limit)
        else if (task == AntTask.Scavenger || task == AntTask.BroodNurse) {
          val small = if (task == AntTask.Scavenger) data.ants.smallScavengers else data.ants.smallNurses
          Math.min(amount, Math.max(0L, capacity(task, data.nestAttributes) - small))
        }
        else if (task == AntTask.SeedForager || task == AntTask.RoyalNurse) Math.min(amount, capacity(task, data.nestAttributes)) else amount)
    })
  }

  def normalize(data: AllData): AllData = {
    val capped = clamp(data)
    val assigned = if (capped.upgrades(UpgradeType.DefaultWorkers).unlocked)
      capped.modify(_.ants).using(_.assignIdleToDefault)
    else if (capped.ants.defaultTask.nonEmpty) capped.modify(_.ants.defaultTask).setTo(None)
    else capped
    clamp(AgingJobReplacement.refill(clamp(assigned)))
  }
}
