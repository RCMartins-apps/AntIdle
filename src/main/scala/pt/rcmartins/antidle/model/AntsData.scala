package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.Constants.u
import zio.json._

case class AntsData(
    eggsAndLarvae: Seq[AntBrood] = Seq.empty,
    workers: Long = 0,
    tasks: Seq[(AntTask, Long)] = Seq.empty,
    sugarCumulativeDebt: Long = 0,
    scouts: Long = 0L,
    defaultTask: Option[AntTask] = None,
    ages: ColonyAges = ColonyAges(),
    queenRoutine: QueenRoutine = QueenRoutine(),
) {

  lazy val idleWorkersCount: Long =
    workers - tasks.map(_._2).sum

  lazy val workersCount: Int = (workers / u).toInt

  lazy val populationCount: Int = workersCount + (scouts / u).toInt + eggsAndLarvae.length

  lazy val upkeepWorkersCount: Long =
    (scouts + idleWorkersCount +
      tasks.map { case (task, amount) => if (task.hasAntUpkeep) amount else 0 }.sum) / u

  def unlockTask(antTask: AntTask): AntsData =
    if (tasks.exists(_._1 == antTask))
      this
    else
      copy(tasks = sortTasks(tasks :+ (antTask, 0)))

  private def sortTasks(tasks: Seq[(AntTask, Long)]): Seq[(AntTask, Long)] =
    tasks.sortBy { case (antTask, _) => antTask.uiOrder }

  def remove1WorkerFromLastUpkeepTask: AntsData =
    tasks.zipWithIndex.findLast { case ((task, amount), _) =>
      task.hasAntUpkeep && amount > 0
    } match {
      case None =>
        this
      case Some(((task, count), index)) =>
        val newCount = count - 1 * u
        copy(tasks = tasks.updated(index, (task, newCount)))
    }

  def defaultTaskChoices: Seq[AntTask] =
    tasks.map(_._1).filter(task => task.showButtons && task.hasAntUpkeep)

  def effectiveDefaultTask: Option[AntTask] = defaultTask.filter(defaultTaskChoices.contains)

  def taskWorkers(task: AntTask): Long = tasks.find(_._1 == task).map(_._2).getOrElse(0L)

  lazy val availableWorkersCount: Long =
    idleWorkersCount + effectiveDefaultTask.map(taskWorkers).getOrElse(0L)

  def availableWorkersLabel: String = effectiveDefaultTask.map { task =>
    if (idleWorkersCount > 0L) "available workers" else s"${task.name} workers"
  }.getOrElse("idle workers")

  def assignIdleToDefault: AntsData = effectiveDefaultTask match {
    case Some(task) if idleWorkersCount > 0 =>
      copy(tasks = tasks.map { case (current, amount) =>
        current -> (if (current == task) amount + idleWorkersCount else amount)
      })
    case _ => this
  }

  def withDefaultTask(next: Option[AntTask]): AntsData = {
    if (next.exists(task => !defaultTaskChoices.contains(task))) this
    else {
      val released = effectiveDefaultTask.filterNot(next.contains)
      copy(defaultTask = next, tasks = tasks.map { case (task, amount) =>
        task -> (if (released.contains(task)) 0L else amount)
      }).assignIdleToDefault
    }
  }

  def hasIdleWorkersForCost(cost: ActionCost): Boolean =
    availableWorkersCount >= cost.idleWorkers

  def addToTask(antTask: AntTask, deltaAmountU: Long): AntsData = {
    if (!tasks.exists(_._1 == antTask)) this
    else if (deltaAmountU < 0) {
      copy(tasks = tasks.map { case (task, amount) =>
        task -> (if (task == antTask) Math.max(0L, amount + deltaAmountU) else amount)
      }).assignIdleToDefault
    } else {
      val available = if (effectiveDefaultTask.contains(antTask)) idleWorkersCount else availableWorkersCount
      val moved = Math.max(0L, Math.min(available, deltaAmountU))
      val borrowed = Math.max(0L, moved - idleWorkersCount)
      copy(tasks = tasks.map { case (task, amount) =>
        task -> (if (task == antTask) amount + moved
          else if (effectiveDefaultTask.contains(task)) amount - borrowed else amount)
      }).assignIdleToDefault
    }
  }

  def removeFromTask(antTask: AntTask, deltaAmountU: Long): AntsData =
    addToTask(antTask, -deltaAmountU)

}

object AntsData {

  implicit val decoder: JsonDecoder[AntsData] = DeriveJsonDecoder.gen[AntsData]
  implicit val encoder: JsonEncoder[AntsData] = DeriveJsonEncoder.gen[AntsData]

  val initial: AntsData = AntsData()

}
