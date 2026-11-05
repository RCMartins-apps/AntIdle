package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object ExplorationProgression {
  val MaxLevel: Int = 20
  val MaxSuppliesLevel: Int = 3
  val MaxWorkers: Long = 37L * u
  val MaxExtraScouts: Int = 3
  val ExtraScoutSugar: Long = 10L * u

  def duration(destination: ExplorationDestination, extraScouts: Int): Long = {
    val speed = 100L + 25L * Math.max(0, Math.min(MaxExtraScouts, extraScouts))
    (destination.duration * 100L + speed - 1L) / speed
  }

  def selectedWorkers(requested: Long, available: Long): Long =
    Math.max(u, Math.min(MaxWorkers, Math.min(requested, Math.max(u, available / u * u))))


  // Each new level costs 100 XP more: 100, 200, 300... Total to level 20: 19,000.
  def threshold(level: Int): Long = 50L * (level - 1) * level

  def level(experience: Long): Int =
    (1 to MaxLevel).reverse.find(threshold(_) <= experience).getOrElse(1)

  def maxParties(experience: Long, upgrades: UpgradesData): Int =
    (if (level(experience) >= 5) 2 else 1) +
      (if (upgrades(UpgradesData.UpgradeType.AdditionalExpeditionSlot).unlocked) 1 else 0)

  // 1 worker: 100%, 5: 200%, 17: 300%, 37+: 400%.
  def partyPercent(workers: Long): Long =
    100L + Math.min(300L, (50.0 * Math.sqrt(Math.max(0L, workers / u - 1L).toDouble)).toLong)

  def suppliesPercent(rank: Int): Long = 100L + 20L * Math.max(0, Math.min(MaxSuppliesLevel, rank))

  def partyReward(amount: Long, workers: Long, supplies: Int): Long =
    amount * partyPercent(workers) / 100L * suppliesPercent(supplies) / 100L

  def earned(party: ExplorationParty): Long =
    partyReward(ExplorationDestination.forParty(party).baseXP, party.amountWorkers, party.suppliesLevel)

  def bonusPercent(experience: Long): Int = (level(experience) - 1) * 25

  def reward(amount: Long, experience: Long): Long =
    amount + amount * bonusPercent(experience) / 100L

  def expeditionReward(amount: Long, experience: Long, workers: Long, supplies: Int): Long =
    reward(partyReward(amount, workers, supplies), experience)

  def addExperience(experience: Long, earned: Long): Long =
    Math.min(Math.max(0L, experience), Long.MaxValue - earned) + earned

  def suppliesRank(data: ExplorationData): Int = Math.max(0, Math.min(MaxSuppliesLevel, data.suppliesLevel))

  def suppliesRequiredLevel(data: ExplorationData): Int = List(2, 5, 8)(Math.min(2, suppliesRank(data)))

  def suppliesCost(data: ExplorationData): ActionCost =
    ActionCost(
      sugar = List(500L, 1500L, 4500L)(Math.min(2, suppliesRank(data))) * u,
      knowledge = List(20L, 80L, 200L)(Math.min(2, suppliesRank(data))) * u,
    )

  def canUpgradeSupplies(data: ExplorationData, resources: BasicResources): Boolean =
    suppliesRank(data) < MaxSuppliesLevel && level(data.experience) >= suppliesRequiredLevel(data) &&
      resources.hasResources(suppliesCost(data))

  def upgradeSupplies(data: AllData): AllData =
    if (!canUpgradeSupplies(data.exploration, data.basicResources)) data
    else data.spendResources(suppliesCost(data.exploration))
      .modify(_.exploration.suppliesLevel).setTo(suppliesRank(data.exploration) + 1)

  def progressText(experience: Long): String = {
    val current = level(experience)
    if (current == MaxLevel) s"Level $current · $experience XP · Maximum level"
    else s"Level $current · ${experience - threshold(current)} / ${threshold(current + 1) - threshold(current)} XP"
  }

  def nextMilestone(experience: Long): String = {
    val current = level(experience)
    if (current < 2) "Level 2: expedition supplies"
    else if (current < 3) "Level 3: Rotten Log"
    else if (current < 5) "Level 5: second expedition slot & supplies II"
    else if (current < 8) "Level 8: supplies III"
    else "Every level improves resource rewards by 25%."
  }
}
