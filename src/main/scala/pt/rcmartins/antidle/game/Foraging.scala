package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.UpgradesData
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

object Foraging {
  private val techniques = List(ImproveSugarCollectorTask1, ImproveSugarCollectorTask2, ImproveSugarCollectorTask3)
  val ProductiveCollectors: Long = (defaultTaskCollectSugarTick - 1L) / sugarCollectorPenaltyTick + 1L

  private def penaltyPerTick(upgrades: UpgradesData): Long =
    if (upgrades(EstablishedTrails).unlocked) sugarCollectorPenaltyTick * 4L / 5L else sugarCollectorPenaltyTick

  def productiveCollectors(upgrades: UpgradesData): Long =
    (defaultTaskCollectSugarTick - 1L) / penaltyPerTick(upgrades) + 1L

  // Sum the positive marginal yields: 0.400, 0.395, 0.390... 0.005 sugar/s.
  // Extra collectors never reduce total output or create negative production.
  def basePerTick(collectors: Long, upgrades: UpgradesData = UpgradesData.initial): Long = {
    val count = Math.max(0L, Math.min(collectors, productiveCollectors(upgrades)))
    count * defaultTaskCollectSugarTick - penaltyPerTick(upgrades) * count * (count - 1L) / 2L
  }

  def productionPerTick(collectors: Long, upgrades: UpgradesData): Long =
    techniques.filter(upgrades(_).unlocked).foldLeft(basePerTick(collectors, upgrades)) { (amount, _) =>
      (BigDecimal(amount) * BigDecimal.decimal(SugarImprovementBonusPerc)).toLong
    }

  def averagePerSecond(collectors: Long, upgrades: UpgradesData): Long = {
    val count = Math.max(1L, collectors)
    productionPerTick(count, upgrades) * TicksPerSecond / count
  }

  def nextPerSecond(collectors: Long, upgrades: UpgradesData): Long = {
    val count = Math.max(0L, collectors)
    if (count >= productiveCollectors(upgrades)) 0L
    else (productionPerTick(count + 1L, upgrades) - productionPerTick(count, upgrades)) * TicksPerSecond
  }
}
