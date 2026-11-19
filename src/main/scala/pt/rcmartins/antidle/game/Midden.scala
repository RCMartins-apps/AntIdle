package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object Midden {
  val Cost: ActionCost = ActionCost(sugar = 1200L * u, tunnelingSpace = 750L * u)
  val Capacity: Long = 100L * u
  val CleanLimit: Long = 20L * u
  val Places = 5
  val ClearPerSecond: Long = 3L * u
  val CareBonusPercent = 20L

  def visible(nest: NestAttributes): Boolean = nest.chambers.nurseryChamber.level >= 2
  def canBuild(data: AllData): Boolean =
    visible(data.nestAttributes) && !data.nestAttributes.middenBuilt && data.hasResources(Cost)

  def build(data: AllData): AllData =
    if (!canBuild(data)) data
    else {
      val paid = data.spendResources(Cost)
      paid.copy(nestAttributes = paid.nestAttributes.copy(middenBuilt = true),
        ants = paid.ants.unlockTask(AntTask.RefuseCarrier))
    }

  def carriers(ants: AntsData): Long = Math.min(Places.toLong, ants.taskWorkers(AntTask.RefuseCarrier) / u)

  def nextRefuse(ants: AntsData, nest: NestAttributes): Long = {
    if (!nest.middenBuilt) 0L
    else {
      val larvae = ants.eggsAndLarvae.count {
        case b: AntBrood.Developing => b.isLarvae && !b.small
        case _ => false
      }
      val change = (larvae.toLong * u - carriers(ants) * ClearPerSecond) / TicksPerSecond
      Math.max(0L, Math.min(Capacity, nest.nurseryRefuse + change))
    }
  }

  def clean(ants: AntsData, nest: NestAttributes): Boolean =
    nest.middenBuilt && carriers(ants) > 0L && nest.nurseryRefuse <= CleanLimit
}
