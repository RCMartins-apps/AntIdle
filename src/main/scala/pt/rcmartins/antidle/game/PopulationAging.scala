package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

import scala.util.Random

object PopulationAging {
  val Minute: Long = 60L * TicksPerSecond

  private def grouped(cohorts: List[AgeCohort]): List[AgeCohort] =
    cohorts.filter(_.count > 0L).groupBy(_.expiresAt)
      .toList.sortBy(_._1).map { case (tick, entries) => AgeCohort(tick, entries.map(_.count).sum) }

  // Seeded jitter spreads each batch across ten-second buckets, without per-ant records.
  private def newborn(count: Long, now: Long, seed: Long): List[AgeCohort] = {
    val bucket = 10L * TicksPerSecond
    val slots = 90
    val first = (now + 45L * Minute + bucket - 1L) / bucket * bucket
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
      def fit(cohorts: List[AgeCohort], count: Long, seed: Long): List[AgeCohort] = {
        val total = cohorts.map(_.count).sum
        if (total > count) take(cohorts, total - count)._1
        else if (total == count) cohorts
        else grouped(cohorts ++ newborn(count - total, data.currentTick, seed))
      }
      val ages = data.ants.ages
      val updated = data.modify(_.ants.ages).setTo(ages.copy(active = true,
        workers = fit(ages.workers, homeWorkers(data.ants), data.world.saveSeed + data.currentTick),
        scouts = fit(ages.scouts, ScoutProgression.available(data.ants, data.exploration) / u, data.world.saveSeed + data.currentTick + 7L)))
      if (ages.active) updated
      else updated.addMessage("Colony aging has begun: ants live 45–60 minutes of simulated time. Aging continues while away; overdue ants die on return.")
    }
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
      val workerLosses = deadWorkers.map(_.count).sum
      val scoutLosses = deadScouts.map(_.count).sum
      val nextAges = ages.copy(workers = workers, scouts = scouts,
        pendingWorkers = ages.pendingWorkers + workerLosses, pendingScouts = ages.pendingScouts + scoutLosses)
      val updated = ready.modify(_.ants).using(a => loseWorkers(a, workerLosses)
        .copy(scouts = a.scouts - scoutLosses * u, ages = nextAges))
      if (summarize && ready.currentTick % Minute == 0L && (nextAges.pendingWorkers > 0L || nextAges.pendingScouts > 0L)) {
        val losses = List(Option.when(nextAges.pendingWorkers > 0L)(s"${nextAges.pendingWorkers} workers"),
          Option.when(nextAges.pendingScouts > 0L)(s"${nextAges.pendingScouts} scouts")).flatten.mkString(" and ")
        updated.addMessage(s"Aging: $losses lost in the last minute.")
          .modify(_.ants.ages).using(_.copy(pendingWorkers = 0L, pendingScouts = 0L))
      } else updated
    }
  }

  def forecast(data: AntsData, tick: Long): String = {
    def count(cohorts: List[AgeCohort]): Long = cohorts.filter(_.expiresAt <= tick + 5L * Minute).map(_.count).sum
    s"Aging · Next 5m: ${count(data.ages.workers)} workers · ${count(data.ages.scouts)} scouts"
  }
}
