package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

import scala.util.Random

object PopulationAging {
  val Minute: Long = 60L * TicksPerSecond

  def lifespanText(upgrades: UpgradesData): String =
    if (upgrades(UpgradesData.UpgradeType.ColonyNutrition).unlocked) "54–72" else "45–60"

  private def grouped(cohorts: List[AgeCohort]): List[AgeCohort] =
    cohorts.filter(_.count > 0L).groupBy(_.expiresAt)
      .toList.sortBy(_._1).map { case (tick, entries) => AgeCohort(tick, entries.map(_.count).sum) }

  // Seeded jitter spreads each batch across ten-second buckets, without per-ant records.
  private def newborn(count: Long, now: Long, seed: Long, upgrades: UpgradesData): List[AgeCohort] = {
    val bucket = 10L * TicksPerSecond
    val improved = upgrades(UpgradesData.UpgradeType.ColonyNutrition).unlocked
    val slots = if (improved) 108 else 90
    val minimum = if (improved) 54L else 45L
    val first = (now + minimum * Minute + bucket - 1L) / bucket * bucket
    val extras = new Random(seed).shuffle((0 until slots).toList).take((count % slots).toInt).toSet
    (0 until slots).toList.map { i =>
      AgeCohort(first + i * bucket, count / slots + (if (extras.contains(i)) 1L else 0L))
    }.filter(_.count > 0L)
  }

  private def take(cohorts: List[AgeCohort], count: Long): (List[AgeCohort], List[AgeCohort]) = {
    val (_, kept, taken) = grouped(cohorts).foldLeft((Math.max(0L, count), List.empty[AgeCohort], List.empty[AgeCohort])) {
      case ((needed, kept, taken), cohort) =>
        val moved = Math.min(needed, cohort.count)
        (needed - moved, AgeCohort(cohort.expiresAt, cohort.count - moved) :: kept, AgeCohort(cohort.expiresAt, moved) :: taken)
    }
    (kept.reverse.filter(_.count > 0L), taken.reverse.filter(_.count > 0L))
  }

  def homeWorkers(ants: AntsData): Long =
    Math.max(0L, (ants.workers - ants.tasks.filterNot(_._1.hasAntUpkeep).map(_._2).sum) / u)

  def reconcile(data: AllData): AllData = {
    if (!data.ants.ages.active && data.nestAttributes.chambers.nurseryChamber.level == 0) data
    else {
      def fit(cohorts: List[AgeCohort], count: Long, seed: Long, longLived: Boolean = false): List[AgeCohort] = {
        val total = cohorts.map(_.count).sum
        if (total > count) take(cohorts, total - count)._1
        else if (total == count) cohorts
        else {
          val created = newborn(count - total, data.currentTick, seed, data.upgrades)
          grouped(cohorts ++ (if (longLived) created.map(c => c.copy(expiresAt = data.currentTick + 2L * (c.expiresAt - data.currentTick))) else created))
        }
      }
      val ages = data.ants.ages
      val updated = data.modify(_.ants.ages).setTo(ages.copy(active = true,
        workers = fit(ages.workers, homeWorkers(data.ants), data.world.saveSeed + data.currentTick),
        scouts = fit(ages.scouts, ScoutProgression.available(data.ants, data.exploration) / u, data.world.saveSeed + data.currentTick + 7L),
        repletes = fit(ages.repletes, data.ants.repletes / u, data.world.saveSeed + data.currentTick + 13L, longLived = true)))
      if (ages.active) updated
      else updated.addMessage(s"Colony aging has begun: new adults live ${lifespanText(data.upgrades)} minutes of simulated time. Aging continues while away; overdue ants die on return.")
    }
  }

  def promoteReplete(data: AllData): AllData = {
    val (workers, converted) = take(data.ants.ages.workers, 1L)
    val fresh = newborn(converted.map(_.count).sum, data.currentTick, data.world.saveSeed + data.currentTick + 13L + data.statistics.repletesPromoted.total.toLong, data.upgrades)
    val extended = fresh.map(c => c.copy(expiresAt = data.currentTick + 2L * (c.expiresAt - data.currentTick)))
    data.modify(_.ants.ages).using(a => a.copy(workers = workers, repletes = grouped(a.repletes ++ extended)))
  }

  def depart(data: AllData, workers: Long, scouts: Long = 0L): (AllData, TravelAges) = {
    val ready = reconcile(data)
    if (!ready.ants.ages.active) ready -> TravelAges()
    else {
      val (homeWorkers, awayWorkers) = take(ready.ants.ages.workers, workers / u)
      val (homeScouts, awayScouts) = take(ready.ants.ages.scouts, scouts / u)
      ready.modify(_.ants.ages).using(_.copy(workers = homeWorkers, scouts = homeScouts)) ->
        TravelAges(awayWorkers, awayScouts)
    }
  }

  def returnHome(data: AllData, ages: TravelAges): AllData =
    applyDeaths(data.modify(_.ants.ages).using(a => a.copy(
      workers = grouped(a.workers ++ ages.workers), scouts = grouped(a.scouts ++ ages.scouts))), summarize = false)

  private def loseWorkers(ants: AntsData, requested: Long): AntsData = {
    val deaths = Math.min(requested, homeWorkers(ants))
    val deficit = Math.max(0L, deaths * u - Math.max(0L, ants.idleWorkersCount))
    val (_, tasks) = ants.tasks.reverse.foldLeft((deficit, List.empty[(AntTask, Long)])) {
      case ((needed, tasks), (task, amount)) =>
        val removed = if (task.hasAntUpkeep) Math.min(needed, amount) else 0L
        (needed - removed, (task -> (amount - removed)) :: tasks)
    }
    ants.copy(workers = ants.workers - deaths * u, tasks = tasks)
  }

  def starveOne(data: AllData): AntsData = {
    val ants = if (homeWorkers(data.ants) > 0L) loseWorkers(data.ants, 1L)
    else if (ScoutProgression.available(data.ants, data.exploration) >= u) data.ants.copy(scouts = data.ants.scouts - u)
    else if (data.ants.repletes >= u) data.ants.copy(repletes = data.ants.repletes - u)
    else data.ants
    ants.copy(sugarCumulativeDebt = Math.max(0L, ants.sugarCumulativeDebt - AntDeathRiskThreshold))
  }

  def tick(data: AllData): AllData = applyDeaths(data, summarize = true)

  private def applyDeaths(data: AllData, summarize: Boolean): AllData = {
    val ready = reconcile(data)
    if (!ready.ants.ages.active) ready
    else {
      val ages = ready.ants.ages
      val (deadWorkers, workers) = ages.workers.partition(_.expiresAt <= ready.currentTick)
      val (deadScouts, scouts) = ages.scouts.partition(_.expiresAt <= ready.currentTick)
      val (deadRepletes, repletes) = ages.repletes.partition(_.expiresAt <= ready.currentTick)
      val repleteLosses = deadRepletes.map(_.count).sum
      val workerLosses = deadWorkers.map(_.count).sum
      val scoutLosses = deadScouts.map(_.count).sum
      val nextAges = ages.copy(workers = workers, scouts = scouts, repletes = repletes, pendingRepletes = ages.pendingRepletes + repleteLosses,
        pendingWorkers = ages.pendingWorkers + workerLosses, pendingScouts = ages.pendingScouts + scoutLosses)
      val updated = RepleteProgression.reconcile(ready.modify(_.ants).using(a => loseWorkers(a, workerLosses)
        .copy(scouts = a.scouts - scoutLosses * u, repletes = a.repletes - repleteLosses * u, ages = nextAges))
        .modify(_.statistics.workersLostToAging).using(_.addValue(workerLosses))
        .modify(_.statistics.scoutsLostToAging).using(_.addValue(scoutLosses))
        .modify(_.statistics.repletesLostToAging).using(_.addValue(repleteLosses)))
      if (summarize && ready.currentTick % Minute == 0L && (nextAges.pendingWorkers > 0L || nextAges.pendingScouts > 0L || nextAges.pendingRepletes > 0L)) {
        val losses = List(Option.when(nextAges.pendingWorkers > 0L)(s"${nextAges.pendingWorkers} workers"),
          Option.when(nextAges.pendingScouts > 0L)(s"${nextAges.pendingScouts} scouts"),
          Option.when(nextAges.pendingRepletes > 0L)(s"${nextAges.pendingRepletes} repletes")).flatten.mkString(" and ")
        updated.addMessage(s"Aging: $losses lost in the last minute.")
          .modify(_.ants.ages).using(_.copy(pendingWorkers = 0L, pendingScouts = 0L, pendingRepletes = 0L))
      } else updated
    }
  }

  def forecast(data: AntsData, tick: Long): String = {
    def count(cohorts: List[AgeCohort]): Long = cohorts.filter(_.expiresAt <= tick + 5L * Minute).map(_.count).sum
    val storage = if (data.repletes > 0L) s" · ${count(data.ages.repletes)} repletes" else ""
    s"Aging · Next 5m: ${count(data.ages.workers)} workers · ${count(data.ages.scouts)} scouts$storage"
  }
}
