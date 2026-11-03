package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.ExplorationParty

object ExplorationProgression {
  val MaxLevel: Int = 20
  val NearbyExperience: Long = 10L
  val DistantExperience: Long = 30L

  // Total XP thresholds: 0, 100, 300, 700... Each level costs twice the last.
  def threshold(level: Int): Long = 100L * ((1L << (level - 1)) - 1L)

  def level(experience: Long): Int =
    (1 to MaxLevel).reverse.find(threshold(_) <= experience).getOrElse(1)

  def earned(party: ExplorationParty): Long =
    if (party.scouts > 0) DistantExperience else NearbyExperience

  def bonusPercent(experience: Long): Int = (level(experience) - 1) * 25

  def reward(amount: Long, experience: Long): Long =
    amount + amount * bonusPercent(experience) / 100L

  def addExperience(experience: Long, earned: Long): Long =
    Math.min(Math.max(0L, experience), Long.MaxValue - earned) + earned

  def progressText(experience: Long): String = {
    val current = level(experience)
    if (current == MaxLevel) s"Level $current · $experience XP · Maximum level"
    else s"Level $current · ${experience - threshold(current)} / ${threshold(current + 1) - threshold(current)} XP"
  }
}
