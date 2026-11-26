package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object Midden {
  val RequiredNurseryLevel = 2
  val Cost: ActionCost = ActionCost(sugar = 1200L * u, tunnelingSpace = 750L * u)
  val BaseCapacity: Long = 100L * u
  val AddedCapacity: Long = 150L * u
  val Places = 5
  val ClearPerSecond: Long = 3L * u

  private val RefusePerLarvalSugar = 2L
  private val CrowdingPercent = 80L
  private val CrowdingDivisor = 3

  def visible(nest: NestAttributes): Boolean =
    nest.chambers.nurseryChamber.level >= RequiredNurseryLevel

  def capacity(nest: NestAttributes): Long =
    if (!visible(nest)) 0L else BaseCapacity + (if (nest.middenBuilt) AddedCapacity else 0L)

  def stored(nest: NestAttributes): Long =
    Math.max(0L, Math.min(capacity(nest), nest.nurseryRefuse))

  def canBuild(data: AllData): Boolean =
    visible(data.nestAttributes) && !data.nestAttributes.middenBuilt && data.hasResources(Cost)

  def build(data: AllData): AllData =
    if (!canBuild(data)) data
    else {
      val paid = data.spendResources(Cost)
      paid.copy(
        nestAttributes = paid.nestAttributes.copy(middenBuilt = true),
        ants = paid.ants.unlockTask(AntTask.RefuseCarrier)
      )
    }

  def carriers(ants: AntsData): Long =
    Math.max(0L, Math.min(Places.toLong, ants.taskWorkers(AntTask.RefuseCarrier) / u))

  def nextRefuse(ants: AntsData, nest: NestAttributes, larvalSugar: Long): Long = {
    val max = capacity(nest)
    if (max == 0L) 0L
    else {
      val made = Math.max(0L, larvalSugar) * RefusePerLarvalSugar
      val carried = carriers(ants) * ClearPerSecond / TicksPerSecond
      Math.max(0L, Math.min(max, stored(nest) + made - carried))
    }
  }

  def blockedBroodPlaces(nest: NestAttributes, places: Int): Int = {
    val max = capacity(nest)
    val maxBlocked = places / CrowdingDivisor
    val threshold = max * CrowdingPercent / 100L
    val refuse = stored(nest)
    if (max == 0L || maxBlocked == 0 || refuse < threshold) 0
    else 1 + ((refuse - threshold) * maxBlocked / (max - threshold + 1L)).toInt
  }

}
