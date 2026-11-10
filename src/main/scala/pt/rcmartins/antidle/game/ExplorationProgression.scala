package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

/** Crew/logistics bonuses only. Exploration has no XP or level reward multiplier. */
object ExplorationProgression {
  case class SuppliesUpgrade(requiredSites: Int, cost: ActionCost)
  val suppliesUpgrades: Vector[SuppliesUpgrade] = Vector(
    SuppliesUpgrade(1, ActionCost(sugar = 500L * u, knowledge = 20L * u)),
    SuppliesUpgrade(3, ActionCost(sugar = 1500L * u, knowledge = 80L * u)),
    SuppliesUpgrade(5, ActionCost(sugar = 4500L * u, knowledge = 200L * u)))
  val MaxSuppliesLevel: Int = suppliesUpgrades.size
  val MaxWorkers: Long = 37L * u
  val MaxExtraScouts: Int = 3
  val ExtraScoutSugar: Long = 10L * u
  def duration(destination: ExplorationDestination, extraScouts: Int): Long = {
    val speed = 100L + 25L * Math.max(0, Math.min(MaxExtraScouts, extraScouts))
    (destination.duration * 100L + speed - 1L) / speed
  }
  def selectedWorkers(requested: Long, available: Long): Long =
    Math.max(u, Math.min(MaxWorkers, Math.min(requested, Math.max(u, available / u * u))))
  def maxParties(data: ExplorationData, upgrades: UpgradesData): Int =
    (if (data.revealedSites >= 4) 2 else 1) + (if (upgrades(UpgradesData.UpgradeType.AdditionalExpeditionSlot).unlocked) 1 else 0)
  def partyPercent(workers: Long): Long =
    100L + Math.min(300L, (50.0 * Math.sqrt(Math.max(0L, workers / u - 1L).toDouble)).toLong)
  def suppliesPercent(rank: Int): Long = 100L + 20L * Math.max(0, Math.min(MaxSuppliesLevel, rank))
  def partyReward(amount: Long, workers: Long, supplies: Int): Long =
    amount * partyPercent(workers) / 100L * suppliesPercent(supplies) / 100L
  def suppliesRank(data: ExplorationData): Int = Math.max(0, Math.min(MaxSuppliesLevel, data.suppliesLevel))
  private def nextSuppliesUpgrade(data: ExplorationData): SuppliesUpgrade = suppliesUpgrades(Math.min(MaxSuppliesLevel - 1, suppliesRank(data)))
  def suppliesRequiredSites(data: ExplorationData): Int = nextSuppliesUpgrade(data).requiredSites
  def suppliesCost(data: ExplorationData): ActionCost = nextSuppliesUpgrade(data).cost
  def canUpgradeSupplies(data: ExplorationData, resources: BasicResources): Boolean =
    suppliesRank(data) < MaxSuppliesLevel && data.revealedSites >= suppliesRequiredSites(data) && resources.hasResources(suppliesCost(data))
  def upgradeSupplies(data: AllData): AllData =
    if (!canUpgradeSupplies(data.exploration, data.basicResources)) data else data.spendResources(suppliesCost(data.exploration)).copy(
      exploration = data.exploration.copy(suppliesLevel = suppliesRank(data.exploration) + 1))
}
