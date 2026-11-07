package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
import scala.util.Random

object RaidProgression {
  val MinProtein: Long = 200L * u
  val MaxProtein: Long = 350L * u
  def rewardRange(workers: Long): (Long, Long) = {
    val count = Math.max(10L, Math.min(37L, workers / u))
    (MinProtein * count / 10L, MaxProtein * count / 10L)
  }
  def isRaid(destination: ExplorationDestination): Boolean = destination == ExplorationDestination.RivalFoodCache

  def lossRange(workers: Long): (Int, Int) = {
    val count = Math.max(10L, Math.min(37L, workers / u))
    val minimumPercent = 20L - (count - 10L) * 10L / 27L
    val maximumPercent = 40L - (count - 10L) * 15L / 27L
    (((count * minimumPercent + 99L) / 100L).toInt, ((count * maximumPercent + 99L) / 100L).toInt)
  }
  def risk(workers: Long): String = {
    val (low, high) = lossRange(workers)
    s"Combat losses: $low–$high of ${workers / u} workers (certain). Scouts: no combat losses."
  }
  def overdue(cohorts: List[AgeCohort], count: Long, returnTick: Long): Long =
    cohorts.sortBy(_.expiresAt).foldLeft((count, 0L)) { case ((needed, due), cohort) =>
      val sent = Math.min(needed, cohort.count)
      (needed - sent, due + (if (cohort.expiresAt <= returnTick) sent else 0L))
    }._2

  def ageWarning(ants: AntsData, tick: Long, workers: Long, extraScouts: Int): String = {
    val returning = tick + ExplorationProgression.duration(ExplorationDestination.RivalFoodCache, extraScouts)
    val dueWorkers = overdue(ants.ages.workers, workers / u, returning)
    val dueScouts = overdue(ants.ages.scouts, 1L + extraScouts, returning)
    val warnings = List(Option.when(dueWorkers > 0L)(s"$dueWorkers workers"),
      Option.when(dueScouts > 0L)(s"$dueScouts scouts")).flatten
    if (warnings.isEmpty) "" else "Age on return: " + warnings.mkString(" · ") + ". May overlap combat losses."
  }

  def roll(seed: Long, workers: Long): RaidOutcome = {
    val random = new Random(seed)
    val (low, high) = lossRange(workers)
    RaidOutcome(low + random.nextInt(high - low + 1), (200L + random.nextInt(151)) * u * Math.max(10L, Math.min(37L, workers / u)) / 10L, seed)
  }
  def seed(data: AllData, party: ExplorationParty): Long =
    data.world.saveSeed + party.startingTick * 31L + party.amountWorkers + data.statistics.explorationsCount.total.toLong * 97L

  // Draw individuals uniformly without replacement, rather than always killing
  // the oldest members. Cohorts remain bounded and survivors retain fixed expiry.
  def survivorAges(cohorts: List[AgeCohort], losses: Int, seed: Long): List[AgeCohort] = {
    val random = new Random(seed)
    (0 until losses).foldLeft(cohorts) { (remaining, _) =>
      val count = remaining.map(_.count).sum
      if (count <= 0L) remaining
      else {
        val selected = random.nextLong(count)
        var offset = 0L
        remaining.map { cohort =>
          val hit = selected >= offset && selected < offset + cohort.count
          offset += cohort.count
          if (hit) cohort.copy(count = cohort.count - 1L) else cohort
        }.filter(_.count > 0L)
      }
    }
  }
  def outcome(data: AllData, party: ExplorationParty): RaidOutcome = party.raidOutcome.getOrElse(roll(seed(data, party), party.amountWorkers))
  def resolve(data: AllData, party: ExplorationParty): (AllData, TravelAges) = {
    if (!isRaid(ExplorationDestination.forParty(party))) data -> party.ages
    else {
      val losses = outcome(data, party).workerLosses.toLong.min(party.amountWorkers / u).max(0L)
      val next = data.copy(ants = data.ants.copy(workers = Math.max(0L, data.ants.workers - losses * u)),
        statistics = data.statistics.copy(raidDeaths = data.statistics.raidDeaths.addValue(losses)))
        .addMessage(s"Raid casualties: $losses workers lost. Scouts suffered no combat losses.")
      next -> party.ages.copy(workers = survivorAges(party.ages.workers, losses.toInt, outcome(data, party).randomSeed + 17L))
    }
  }
}
