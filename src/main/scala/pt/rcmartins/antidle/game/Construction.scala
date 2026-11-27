package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.balance.ChamberPricing
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.{
  ConstructionPlanning,
  RoyalNurseryPlanning
}

object Construction {
  def level(chambers: AllChamberData, kind: ChamberType): Int = kind match {
    case ChamberType.Nest         => chambers.nestChamber.level
    case ChamberType.Queen        => chambers.queenChamber.level
    case ChamberType.FoodStorage  => chambers.foodStorageChamber.level
    case ChamberType.Nursery      => chambers.nurseryChamber.level
    case ChamberType.Aphid        => chambers.aphidChamber.level
    case ChamberType.Replete      => chambers.repleteChamber.level
    case ChamberType.Waste        => chambers.wasteChamber.level
    case ChamberType.RoyalNursery => chambers.royalNurseryChamber.level
  }

  def cost(kind: ChamberType, level: Int): ActionCost = ChamberPricing.cost(kind, level)

  def nextCost(nest: NestAttributes, kind: ChamberType): ActionCost =
    cost(kind, level(nest.chambers, kind) + nest.buildQueue.count(_.chamberType == kind))

  def blockedReason(
      nest: NestAttributes,
      upgrades: UpgradesData,
      kind: ChamberType
  ): Option[String] = {
    if (kind == ChamberType.RoyalNursery && nest.chambers.royalNurseryChamber.level > 0)
      Some("Already built")
    else if (kind == ChamberType.RoyalNursery && nest.buildQueue.exists(_.chamberType == kind))
      Some("Already queued")
    else if (nest.buildQueue.size >= nest.maxBuildQueue) Some("Build queue full")
    else if (kind == ChamberType.RoyalNursery && !RoyalNurseryProgression.canPlan(nest, upgrades))
      Some("Requires Royal Nursery Planning")
    else if (kind == ChamberType.Waste && !Midden.visible(nest))
      Some(s"Requires Nursery Chamber level ${Midden.RequiredNurseryLevel}")
    else None
  }

  def canEnqueue(nest: NestAttributes, upgrades: UpgradesData, kind: ChamberType): Boolean =
    blockedReason(nest, upgrades, kind).isEmpty

  def reconcile(data: AllData): AllData = {
    val nest = data.nestAttributes
    val (_, queue) =
      nest.buildQueue.foldLeft((Map.empty[ChamberType, Int], List.empty[BuildTask])) {
        case ((planned, queue), task) =>
          val count = planned.getOrElse(task.chamberType, 0)
          val allowed = task.chamberType match {
            case ChamberType.RoyalNursery =>
              data.upgrades(RoyalNurseryPlanning).unlocked &&
                nest.chambers.royalNurseryChamber.level == 0 && count == 0
            case ChamberType.Waste => Midden.visible(nest)
            case _                 => true
          }
          if (!allowed) (planned, queue)
          else
            (
              planned.updated(task.chamberType, count + 1),
              queue :+ task.copy(actionCost =
                cost(task.chamberType, level(nest.chambers, task.chamberType) + count)
              )
            )
      }
    data
      .modify(_.nestAttributes)
      .using(
        _.copy(
          buildQueue = queue,
          maxBuildQueue =
            if (data.upgrades(ConstructionPlanning).unlocked) Math.max(3, nest.maxBuildQueue)
            else nest.maxBuildQueue
        )
      )
  }

  def enqueue(data: AllData, kind: ChamberType): AllData = {
    val ready = reconcile(data)
    if (!canEnqueue(ready.nestAttributes, ready.upgrades, kind)) ready
    else
      reconcile(
        ready
          .modify(_.nestAttributes.buildQueue)
          .using(_ :+ BuildTask(kind, nextCost(ready.nestAttributes, kind)))
      )
  }

  def cancel(data: AllData, index: Int): AllData =
    if (index < 0 || index >= data.nestAttributes.buildQueue.size) data
    else reconcile(data.modify(_.nestAttributes.buildQueue).using(_.patch(index, Nil, 1)))

  def move(data: AllData, from: Int, to: Int): AllData = {
    val queue = data.nestAttributes.buildQueue
    if (from == to || !queue.isDefinedAt(from) || !queue.isDefinedAt(to)) data
    else {
      val reordered = queue.patch(from, Nil, 1).patch(to, List(queue(from)), 0)
      reconcile(data.modify(_.nestAttributes.buildQueue).setTo(reordered))
    }
  }

  def tick(data: AllData): AllData = {
    val ready = reconcile(data)
    ready.nestAttributes.buildQueue match {
      case next :: rest if ready.hasResources(next.actionCost) =>
        reconcile(
          ChamberBonuses.completeUpgrade(
            ready
              .spendResources(next.actionCost)
              .modify(_.nestAttributes.buildQueue)
              .setTo(rest),
            next.chamberType
          )
        )
      case _ => ready
    }
  }

}
