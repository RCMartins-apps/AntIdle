package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.balance.UpgradeBalance
import pt.rcmartins.antidle.model.{AntTask, AntsData, Lineage, UpgradesData}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

object Foraging {
  val ProductiveCollectors: Long =
    (defaultTaskCollectSugarTick - 1L) / sugarCollectorPenaltyTick + 1L

  private def penaltyPerTick(upgrades: UpgradesData): Long =
    if (upgrades(EstablishedTrails).unlocked) sugarCollectorPenaltyTick * 4L / 5L
    else sugarCollectorPenaltyTick

  def productiveCollectors(upgrades: UpgradesData): Long =
    (defaultTaskCollectSugarTick - 1L) / penaltyPerTick(upgrades) + 1L

  // Sum the positive marginal yields: 0.400, 0.395, 0.390... 0.005 sugar/s.
  // Extra collectors never reduce total output or create negative production.
  def basePerTick(collectors: Long, upgrades: UpgradesData = UpgradesData.initial): Long = {
    val count = Math.max(0L, Math.min(collectors, productiveCollectors(upgrades)))
    count * defaultTaskCollectSugarTick - penaltyPerTick(upgrades) * count * (count - 1L) / 2L
  }

  def productionPerTick(
      collectors: Long,
      upgrades: UpgradesData,
      lineage: Lineage = Lineage()
  ): Long =
    Prestige.sugar(
      UpgradeBalance.production(basePerTick(collectors, upgrades), "sugarCollectors", upgrades),
      lineage
    )

  def mixedBasePerTick(ants: AntsData, upgrades: UpgradesData): Long = {
    val normal = ants.taskWorkers(AntTask.SugarCollector) / u
    val small = ants.smallCollectors / u
    val standard = basePerTick(normal, upgrades)
    standard + (basePerTick(normal + small, upgrades) - standard) / 2L
  }

  private[game] def researchedProduction(base: Long, upgrades: UpgradesData): Long =
    UpgradeBalance.production(base, "sugarCollectors", upgrades)

  def production(ants: AntsData, upgrades: UpgradesData): Long =
    Prestige.sugar(researchedProduction(mixedBasePerTick(ants, upgrades), upgrades), ants.lineage)

  def perCollector(ants: AntsData, upgrades: UpgradesData, small: Boolean): Long = {
    val standard = ants.taskWorkers(AntTask.SugarCollector) / u
    val count = if (small) ants.smallCollectors / u else standard
    if (count == 0L) 0L
    else if (small)
      (productionPerTick(standard + count, upgrades, ants.lineage) -
        productionPerTick(standard, upgrades, ants.lineage)) * TicksPerSecond / (2L * count)
    else averagePerSecond(count, upgrades, ants.lineage)
  }

  def nextStandardPerSecond(ants: AntsData, upgrades: UpgradesData): Long = {
    val collectors = ants.taskWorkers(AntTask.SugarCollector)
    val next = ants.copy(tasks = ants.tasks.map {
      case (AntTask.SugarCollector, _) => AntTask.SugarCollector -> (collectors + u)
      case task                        => task
    })
    (production(next, upgrades) - production(ants, upgrades)) * TicksPerSecond
  }

  def averagePerSecond(
      collectors: Long,
      upgrades: UpgradesData,
      lineage: Lineage = Lineage()
  ): Long = {
    val count = Math.max(1L, collectors)
    productionPerTick(count, upgrades, lineage) * TicksPerSecond / count
  }

  def nextPerSecond(
      collectors: Long,
      upgrades: UpgradesData,
      lineage: Lineage = Lineage()
  ): Long = {
    val count = Math.max(0L, collectors)
    if (count >= productiveCollectors(upgrades)) 0L
    else
      (productionPerTick(count + 1L, upgrades, lineage) - productionPerTick(
        count,
        upgrades,
        lineage
      )) * TicksPerSecond
  }

}
