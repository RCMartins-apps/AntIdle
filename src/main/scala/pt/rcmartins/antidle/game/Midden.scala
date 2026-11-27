package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object Midden {
  val RequiredNurseryLevel = 2
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
    if (!visible(nest)) 0L
    else BaseCapacity + Math.max(0, nest.chambers.wasteChamber.level) * AddedCapacity

  def stored(nest: NestAttributes): Long =
    Math.max(0L, Math.min(capacity(nest), nest.nurseryRefuse))

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
