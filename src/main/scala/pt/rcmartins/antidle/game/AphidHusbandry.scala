package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.AphidTending

object AphidHusbandry {
  val SugarPerWorkerSecond: Long = 8000L
  val ProteinPerWorkerSecond: Long = 100L

  val AphidsPerTender: Long = 10L
  def housing(nest: NestAttributes): Long = Math.max(0L, nest.chambers.aphidChamber.level.toLong) * nest.aphidPlacesPerLevel
  def capacity(nest: NestAttributes): Long = Math.min(housing(nest), Math.max(0L, nest.aphids)) / AphidsPerTender * u

  def reconcile(data: AllData): AllData = {
    val nest = data.nestAttributes.copy(aphidPlacesPerLevel = if (data.upgrades(UpgradesData.UpgradeType.AphidShelters).unlocked) 20 else 10)
    val stocked = if (!nest.aphidsInitialized) nest.copy(aphids = housing(nest), aphidsInitialized = true)
      else nest.copy(aphids = Math.max(0L, Math.min(housing(nest), nest.aphids)))
    val first = stocked.aphids == 0L && housing(stocked) >= AphidsPerTender &&
      data.upgrades(AphidTending).unlocked && DiscoveryProgression.WildAphids.found(data.exploration)
    data.copy(nestAttributes = if (first) stocked.copy(aphids = AphidsPerTender) else stocked)
  }

  def workers(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData): Long =
    if (upgrades(AphidTending).unlocked) Math.min(capacity(nest), Math.max(0L, ants.taskWorkers(AntTask.AphidTender))) / u else 0L

  def proteinPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, available: Long): Long =
    Math.min(Math.max(0L, available), workers(ants, nest, upgrades) * ProteinPerWorkerSecond / TicksPerSecond)

  def productionPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, protein: Long): Long =
    Prestige.sugar(proteinPerTick(ants, nest, upgrades, protein) * SugarPerWorkerSecond / ProteinPerWorkerSecond, ants.lineage)
}
