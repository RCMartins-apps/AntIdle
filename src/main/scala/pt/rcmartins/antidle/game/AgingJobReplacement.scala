package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

/** Remember only vacancies caused by aging, not a second set of general job targets. */
object AgingJobReplacement {
  private val smallJobs = Seq(AntTask.Tunneler, AntTask.Scavenger, AntTask.BroodNurse)
  private def eligible(task: AntTask): Boolean = task.agesAtHome && (task.showButtons || task == AntTask.TrailForager)
  private def count(ants: AntsData, task: AntTask): Long = ants.taskWorkers(task) + (task match {
    case AntTask.Tunneler => ants.smallTunnelers
    case AntTask.Scavenger => ants.smallScavengers
    case AntTask.BroodNurse => ants.smallNurses
    case _ => 0L
  })

  def cancel(data: AllData, task: AntTask): AllData = data.copy(
    ants = data.ants.copy(agingJobVacancies = data.ants.agingJobVacancies.filterNot(_._1 == task)))
  def cancelAll(data: AllData): AllData = data.copy(ants = data.ants.copy(agingJobVacancies = Seq.empty))

  def record(before: AntsData, after: AllData): AllData = {
    if (!after.upgrades(UpgradeType.QueensRoutine).unlocked) after
    else {
      val roles = (before.tasks.map(_._1) ++ smallJobs).distinct.filter(eligible)
      val losses = roles.filterNot(after.ants.effectiveDefaultTask.contains).map { task =>
        task -> Math.max(0L, count(before, task) - count(after.ants, task))
      }
      val vacancies = (after.ants.agingJobVacancies ++ losses).groupBy(_._1).toSeq
        .map { case (task, entries) => task -> entries.map(_._2).sum }.filter(_._2 > 0L)
        .sortBy { case (task, _) => (task.uiOrder, task.name) }
      after.copy(ants = after.ants.copy(agingJobVacancies = vacancies))
    }
  }

  private def room(data: AllData, ants: AntsData, task: AntTask): Long = {
    val limit = task match {
      case AntTask.AphidTender if !data.upgrades(UpgradeType.AphidTending).unlocked => 0L
      case AntTask.TrailForager =>
        PheromoneTrails.targetWorkers(data.exploration)
      case _ => DefaultWorkerAssignment.capacity(task, data.nestAttributes)
    }
    Math.max(0L, limit - count(ants, task))
  }

  def refill(data: AllData): AllData = {
    if (!data.upgrades(UpgradeType.QueensRoutine).unlocked) cancelAll(data)
    else {
      var ants = data.ants
      val pending = ants.agingJobVacancies.flatMap { case (task, vacancy) =>
        if (!eligible(task) || ants.effectiveDefaultTask.contains(task)) None
        else {
          val wanted = Math.max(0L, vacancy) / u * u
          val normal = Math.min(wanted, Math.min(Math.max(0L, ants.availableWorkersCount), room(data, ants, task))) / u * u
          if (normal > 0L) ants = ants.unlockTask(task).addToTask(task, normal)
          val smallAllowed = smallJobs.contains(task) && (task != AntTask.Scavenger || ants.standardWorkersUnlocked)
          val small = if (!smallAllowed) 0L else Math.min(wanted - normal, Math.min(ants.smallCollectors, room(data, ants, task))) / u * u
          if (small > 0L) ants = task match {
            case AntTask.Tunneler => ants.copy(smallTunnelers = ants.smallTunnelers + small)
            case AntTask.Scavenger => ants.copy(smallScavengers = ants.smallScavengers + small)
            case AntTask.BroodNurse => ants.copy(smallNurses = ants.smallNurses + small)
            case _ => ants
          }
          Option.when(wanted > normal + small)(task -> (wanted - normal - small))
        }
      }
      data.copy(ants = ants.copy(agingJobVacancies = pending))
    }
  }
}
