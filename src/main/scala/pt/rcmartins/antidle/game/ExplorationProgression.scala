package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object ExplorationProgression {
  val MaxLevel: Int = 20
  case class SuppliesUpgrade(requiredLevel: Int, cost: ActionCost)
  // One row per upgrade: unlock level and actual displayed/charged price.
  val suppliesUpgrades: Vector[SuppliesUpgrade] = Vector(
    SuppliesUpgrade(requiredLevel = 2, cost = ActionCost(sugar = 500L * u, knowledge = 20L * u)),
    SuppliesUpgrade(requiredLevel = 5, cost = ActionCost(sugar = 1500L * u, knowledge = 80L * u)),
    SuppliesUpgrade(requiredLevel = 8, cost = ActionCost(sugar = 4500L * u, knowledge = 200L * u)),
  )
  val MaxSuppliesLevel: Int = suppliesUpgrades.size
  val SecondExpeditionSlotLevel: Int = 5
  val RewardPercentPerLevel: Int = 25
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
    (if (level(experience) >= SecondExpeditionSlotLevel) 2 else 1) +
      (if (upgrades(UpgradesData.UpgradeType.AdditionalExpeditionSlot).unlocked) 1 else 0)

  // 1 worker: 100%, 5: 200%, 17: 300%, 37+: 400%.
  def partyPercent(workers: Long): Long =
    100L + Math.min(300L, (50.0 * Math.sqrt(Math.max(0L, workers / u - 1L).toDouble)).toLong)

  def suppliesPercent(rank: Int): Long = 100L + 20L * Math.max(0, Math.min(MaxSuppliesLevel, rank))

  def partyReward(amount: Long, workers: Long, supplies: Int): Long =
    amount * partyPercent(workers) / 100L * suppliesPercent(supplies) / 100L

  def earned(party: ExplorationParty): Long =
    partyReward(ExplorationDestination.forParty(party).baseXP, party.amountWorkers, party.suppliesLevel)

  def bonusPercent(experience: Long): Int = (level(experience) - 1) * RewardPercentPerLevel

  def reward(amount: Long, experience: Long): Long =
    amount + amount * bonusPercent(experience) / 100L

  def expeditionReward(amount: Long, experience: Long, workers: Long, supplies: Int): Long =
    reward(partyReward(amount, workers, supplies), experience)

  def addExperience(experience: Long, earned: Long): Long =
    Math.min(Math.max(0L, experience), Long.MaxValue - earned) + earned

  def suppliesRank(data: ExplorationData): Int = Math.max(0, Math.min(MaxSuppliesLevel, data.suppliesLevel))

  private def nextSuppliesUpgrade(data: ExplorationData): SuppliesUpgrade =
    suppliesUpgrades(Math.min(MaxSuppliesLevel - 1, suppliesRank(data)))

  def suppliesRequiredLevel(data: ExplorationData): Int = nextSuppliesUpgrade(data).requiredLevel

  def suppliesCost(data: ExplorationData): ActionCost = nextSuppliesUpgrade(data).cost

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
    // Derive labels from the same tuning values; moving a level moves its hint.
    val supplies = suppliesUpgrades.zipWithIndex.map { case (tier, index) =>
      tier.requiredLevel -> (if (index == 0) "expedition supplies" else s"supplies ${"I" * (index + 1)}")
    }
    val milestones = List(ExplorationDestination.RottenLog.requiredLevel -> "Rotten Log",
      SecondExpeditionSlotLevel -> "second expedition slot") ++ supplies
    milestones.filter(_._1 > current).map(_._1).minOption match {
      case Some(next) => s"Level $next: ${milestones.filter(_._1 == next).map(_._2).mkString(" & ")}"
      case None => s"Every level improves resource rewards by $RewardPercentPerLevel%."
    }
  }
}
