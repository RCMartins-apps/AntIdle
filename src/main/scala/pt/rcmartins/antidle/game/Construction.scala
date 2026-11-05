package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.ConstructionPlanning

object Construction {
  def level(chambers: AllChamberData, kind: ChamberType): Int = kind match {
    case ChamberType.Nest => chambers.nestChamber.level
    case ChamberType.Queen => chambers.queenChamber.level
    case ChamberType.FoodStorage => chambers.foodStorageChamber.level
    case ChamberType.Nursery => chambers.nurseryChamber.level
    case ChamberType.Aphid => chambers.aphidChamber.level
    case ChamberType.Replete => chambers.repleteChamber.level
  }

  def cost(kind: ChamberType, level: Int): ActionCost = ActionCost(
    tunnelingSpace = getChamberTunnelingSpaceNeeded(kind, level),
    protein = if (kind == ChamberType.Nursery) NurseryProteinCost * (level + 1L)
      else if (kind == ChamberType.Aphid) 2L * u * (level + 1L)
      else if (kind == ChamberType.Replete) 5L * u * (level + 1L) else 0L,
  )

  def nextCost(nest: NestAttributes, kind: ChamberType): ActionCost =
    cost(kind, level(nest.chambers, kind) + nest.buildQueue.count(_.chamberType == kind))

  def reconcile(data: AllData): AllData = {
    val nest = data.nestAttributes
    val (_, queue) = nest.buildQueue.foldLeft((Map.empty[ChamberType, Int], List.empty[BuildTask])) {
      case ((planned, queue), task) =>
        val count = planned.getOrElse(task.chamberType, 0)
        (planned.updated(task.chamberType, count + 1),
          queue :+ task.copy(actionCost = cost(task.chamberType, level(nest.chambers, task.chamberType) + count)))
    }
    data.modify(_.nestAttributes).using(_.copy(buildQueue = queue,
      maxBuildQueue = if (data.upgrades(ConstructionPlanning).unlocked) Math.max(3, nest.maxBuildQueue) else nest.maxBuildQueue))
  }

  def enqueue(data: AllData, kind: ChamberType): AllData = {
    val ready = reconcile(data)
    if (ready.nestAttributes.buildQueue.size >= ready.nestAttributes.maxBuildQueue) ready
    else reconcile(ready.modify(_.nestAttributes.buildQueue).using(_ :+ BuildTask(kind, nextCost(ready.nestAttributes, kind))))
  }

  def cancel(data: AllData, index: Int): AllData =
    if (index < 0 || index >= data.nestAttributes.buildQueue.size) data
    else reconcile(data.modify(_.nestAttributes.buildQueue).using(_.patch(index, Nil, 1)))

  def tick(data: AllData): AllData = {
    val ready = reconcile(data)
    ready.nestAttributes.buildQueue match {
      case next :: rest if ready.hasResources(next.actionCost) =>
        reconcile(ChamberBonuses.completeUpgrade(ready.spendResources(next.actionCost)
          .modify(_.nestAttributes.buildQueue).setTo(rest), next.chamberType))
      case _ => ready
    }
  }
}
