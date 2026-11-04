package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.UpgradesData
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.AphidTending

object AphidHusbandry {
  val ProductiveWorkers: Long = 20L
  private val firstYield: Long = 8000L / TicksPerSecond
  private val penalty: Long = 400L / TicksPerSecond

  // A finite aphid colony: marginal yields decline from 0.8 to 0.04 sugar/s.
  def productionPerTick(workers: Long, upgrades: UpgradesData): Long = {
    val count = if (upgrades(AphidTending).unlocked) Math.max(0L, Math.min(ProductiveWorkers, workers)) else 0L
    count * firstYield - penalty * count * (count - 1L) / 2L
  }

  def averagePerSecond(workers: Long, upgrades: UpgradesData): Long =
    productionPerTick(Math.max(1L, workers), upgrades) * TicksPerSecond / Math.max(1L, workers)

  def nextPerSecond(workers: Long, upgrades: UpgradesData): Long = {
    val count = Math.max(0L, workers)
    if (count >= ProductiveWorkers) 0L
    else (productionPerTick(count + 1L, upgrades) - productionPerTick(count, upgrades)) * TicksPerSecond
  }
}
