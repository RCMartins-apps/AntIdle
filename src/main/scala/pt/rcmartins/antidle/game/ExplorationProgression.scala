package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

/** Crew/logistics bonuses only. Exploration has no XP or level reward multiplier. */
object ExplorationProgression {
  val MaxSuppliesLevel: Int = UpgradesData.UpgradeType.expeditionSupplies.size
  val WorkerStep: Long = 5L * u
  val MinWorkers: Long = WorkerStep
  val MaxWorkers: Long = 50L * u
  val MaxExtraScouts: Int = 3
  val ExtraScoutSugar: Long = 10L * u
  def duration(destination: ExplorationDestination, extraScouts: Int): Long = {
    val speed = 100L + 25L * Math.max(0, Math.min(MaxExtraScouts, extraScouts))
    (destination.duration * 100L + speed - 1L) / speed
  }
  def selectedWorkers(requested: Long, available: Long): Long =
    Math.max(MinWorkers, Math.min(MaxWorkers, Math.min(requested, Math.max(MinWorkers, available))) / WorkerStep * WorkerStep)
  def maxParties(data: ExplorationData, upgrades: UpgradesData): Int =
    (if (data.revealedSites >= 4) 2 else 1) + (if (upgrades(UpgradesData.UpgradeType.AdditionalExpeditionSlot).unlocked) 1 else 0)
  def suppliesPercent(rank: Int): Long = 100L + 20L * Math.max(0, Math.min(MaxSuppliesLevel, rank))
  // Preserve the former 10-worker yield (2.5x base), now linear at every crew size.
  def partyReward(amount: Long, workers: Long, supplies: Int): Long =
    amount * Math.max(0L, workers) / (4L * u) * suppliesPercent(supplies) / 100L
  def suppliesRank(upgrades: UpgradesData): Int =
    UpgradesData.UpgradeType.expeditionSupplies.takeWhile(upgrades(_).unlocked).size

  // Read the former standalone rank once; all current effects use standard upgrades.
  def migrateLegacySupplies(data: AllData): AllData = {
    val rank = Math.max(0, Math.min(MaxSuppliesLevel, data.exploration.suppliesLevel))
    data.copy(upgrades = UpgradesData.UpgradeType.expeditionSupplies.take(rank)
      .foldLeft(data.upgrades)((upgrades, tier) => upgrades.unlock(tier)),
      exploration = data.exploration.copy(suppliesLevel = 0))
  }
}
