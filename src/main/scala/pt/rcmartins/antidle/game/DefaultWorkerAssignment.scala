package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object DefaultWorkerAssignment {
  def capacity(task: AntTask, nest: NestAttributes): Long = task match {
    case AntTask.AphidTender => AphidHusbandry.capacity(nest)
    case AntTask.SeedForager => OutpostProgression.capacity(nest)
    case AntTask.RoyalNurse => RoyalBroodProgression.capacity(nest)
    case _ => Long.MaxValue
  }

  def select(data: AllData, task: Option[AntTask]): AllData =
    if (!data.upgrades(UpgradeType.DefaultWorkers).unlocked) data
    else normalize(data.modify(_.ants).using(_.withDefaultTask(task)))

  def assign(data: AllData, task: AntTask, amount: Long): AllData = {
    val room = Math.max(0L, capacity(task, data.nestAttributes) - data.ants.taskWorkers(task))
    normalize(data.modify(_.ants).using(_.addToTask(task, Math.max(0L, Math.min(amount, room)))))
  }

  private def clamp(data: AllData): AllData = {
    val limit = if (data.upgrades(UpgradeType.AphidTending).unlocked) AphidHusbandry.capacity(data.nestAttributes) else 0L
    data.modify(_.ants.tasks).using(_.map { case (task, amount) =>
      task -> (if (task == AntTask.AphidTender) Math.min(amount, limit)
        else if (task == AntTask.SeedForager || task == AntTask.RoyalNurse) Math.min(amount, capacity(task, data.nestAttributes)) else amount)
    })
  }

  def normalize(data: AllData): AllData = {
    val capped = clamp(data)
    val assigned = if (capped.upgrades(UpgradeType.DefaultWorkers).unlocked)
      capped.modify(_.ants).using(_.assignIdleToDefault)
    else if (capped.ants.defaultTask.nonEmpty) capped.modify(_.ants.defaultTask).setTo(None)
    else capped
    clamp(assigned)
  }
}
