package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.AphidTending

object AphidHusbandry {
  val SugarPerWorkerSecond: Long = 8000L
  val ProteinPerWorkerSecond: Long = 100L

  def capacity(nest: NestAttributes): Long = Math.max(0L, nest.chambers.aphidChamber.level.toLong) * u

  def workers(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData): Long =
    if (upgrades(AphidTending).unlocked) Math.min(capacity(nest), Math.max(0L, ants.taskWorkers(AntTask.AphidTender))) / u else 0L

  def proteinPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, available: Long): Long =
    Math.min(Math.max(0L, available), workers(ants, nest, upgrades) * ProteinPerWorkerSecond / TicksPerSecond)

  def productionPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, protein: Long): Long =
    proteinPerTick(ants, nest, upgrades, protein) * SugarPerWorkerSecond / ProteinPerWorkerSecond
}
