package pt.rcmartins.antidle.model

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants.u
import zio.json._

case class AntsData(
    eggsAndLarvae: Seq[AntBrood] = Seq.empty,
    workers: Long = 0,
    tasks: Seq[(AntTask, Long)] = Seq.empty,
    sugarCumulativeDebt: Long = 0,
    scouts: Long = 0L,
) {

  lazy val idleWorkersCount: Long =
    workers - tasks.map(_._2).sum

  lazy val workersCount: Int = (workers / u).toInt

  lazy val workersAndLarvaeCount: Int = workersCount + (scouts / u).toInt + eggsAndLarvae.length

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

  def hasIdleWorkersForCost(cost: ActionCost): Boolean =
    idleWorkersCount >= cost.idleWorkers

  def addToTask(antTask: AntTask, deltaAmountU: Long): AntsData =
    this
      .modify(_.tasks)
      .using(_.map {
        case (task, amount) if task == antTask =>
          (task, Math.max(0L, amount + Math.min(idleWorkersCount, deltaAmountU)))
        case other =>
          other
      })

  def removeFromTask(antTask: AntTask, deltaAmountU: Long): AntsData =
    addToTask(antTask, -deltaAmountU)

}

object AntsData {

  implicit val decoder: JsonDecoder[AntsData] = DeriveJsonDecoder.gen[AntsData]
  implicit val encoder: JsonEncoder[AntsData] = DeriveJsonEncoder.gen[AntsData]

  val initial: AntsData = AntsData()

}
