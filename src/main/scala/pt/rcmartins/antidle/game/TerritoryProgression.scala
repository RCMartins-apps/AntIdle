package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
import scala.util.Random

object TerritoryProgression {
  val ContestTicks: Long = 60L * TicksPerSecond
  val ProteinPerWorkerSecond: Long = 3000L
  def target(requested: Int): Int = Math.max(10, Math.min(37, requested))
  def visible(exploration: ExplorationData): Boolean =
    DiscoveryProgression.ContestedGround.found(exploration) || exploration.territory.held
  def workers(ants: AntsData): Long = Math.max(0L, ants.taskWorkers(AntTask.TerritoryForager) / u)
  def cost(requested: Int): ActionCost = ActionCost(sugar = target(requested).toLong * 20L * u, idleWorkers = target(requested).toLong * u)
  def canClaim(data: AllData, requested: Int): Boolean = !data.exploration.territory.held &&
    data.unlocks.tabs.exploreTabUnlocked && DiscoveryProgression.ContestedGround.found(data.exploration) && data.hasResources(cost(requested))

  private def grouped(ages: List[AgeCohort]): List[AgeCohort] = ages.filter(_.count > 0L).groupBy(_.expiresAt)
    .toList.sortBy(_._1).map { case (expiry, list) => AgeCohort(expiry, list.map(_.count).sum) }
  private def dropOldest(ages: List[AgeCohort], count: Long): List[AgeCohort] =
    grouped(ages).foldLeft((count, List.empty[AgeCohort])) { case ((left, kept), age) =>
      val removed = Math.min(left, age.count)
      (left - removed, kept :+ age.copy(count = age.count - removed))
    }._2.filter(_.count > 0L)

  private def addWorkers(data: AllData, requested: Long): AllData = {
    val amount = Math.max(0L, Math.min(requested, data.ants.availableWorkersCount / u))
    if (amount == 0L) data
    else {
      val (ready, ages) = PopulationAging.depart(data, amount * u)
      val site = ready.exploration.territory
      ready.copy(ants = ready.ants.unlockTask(AntTask.TerritoryForager).addToTask(AntTask.TerritoryForager, amount * u),
        exploration = ready.exploration.copy(territory = site.copy(ages = grouped(site.ages ++ ages.workers))))
    }
  }
  def claim(data: AllData, requested: Int): AllData = {
    val ready = PopulationAging.tick(data)
    if (!canClaim(ready, requested)) ready
    else {
      val size = target(requested)
      val site = Territory(held = true, targetWorkers = size, nextContestTick = ready.currentTick + ContestTicks,
        seed = ready.world.saveSeed + ready.currentTick * 31L + ready.statistics.territoriesClaimed.total.toLong * 997L)
      val started = ready.spendResources(cost(size)).copy(exploration = ready.exploration.copy(territory = site),
        statistics = ready.statistics.copy(territoriesClaimed = ready.statistics.territoriesClaimed.addValue(1L)))
      DefaultWorkerAssignment.normalize(addWorkers(started, size.toLong))
    }
  }
  def reinforce(data: AllData): AllData = if (!data.exploration.territory.held) data
    else DefaultWorkerAssignment.normalize(addWorkers(data, Math.max(0L, target(data.exploration.territory.targetWorkers).toLong - workers(data.ants))))
  def setMaintain(data: AllData, enabled: Boolean): AllData =
    if (!data.exploration.territory.held) data else data.copy(exploration = data.exploration.copy(
      territory = data.exploration.territory.copy(maintain = enabled)))

  def withdraw(data: AllData): AllData = {
    val site = data.exploration.territory
    val released = data.copy(ants = data.ants.removeFromTask(AntTask.TerritoryForager, data.ants.taskWorkers(AntTask.TerritoryForager)),
      exploration = data.exploration.copy(territory = Territory()))
    DefaultWorkerAssignment.normalize(PopulationAging.returnHome(released, TravelAges(workers = site.ages)))
  }
  def reconcile(initial: AllData): AllData = {
    // Earlier raid saves did not record this newly added survey. Current-run
    // combat deaths prove the player already completed a raid; retain that unlock.
    val data = if (initial.statistics.raidDeaths.thisRun > 0 && !DiscoveryProgression.ContestedGround.found(initial.exploration))
      initial.copy(exploration = initial.exploration.copy(surveys = initial.exploration.surveys.updated("rival-food-cache", 1)))
    else initial
    val site = data.exploration.territory
    val count = workers(data.ants)
    if (!site.held && count == 0L) data
    else {
      val listed = site.ages.map(_.count).sum
      val fitted = if (listed > count) dropOldest(site.ages, listed - count)
        else if (listed < count && data.ants.ages.active) grouped(site.ages ++ PopulationAging.freshWorkerCohorts(data, count - listed))
        else grouped(site.ages)
      val ready = data.copy(exploration = data.exploration.copy(territory = site.copy(ages = fitted, targetWorkers = target(site.targetWorkers))))
      if (site.held && count == 0L) withdraw(ready).addMessage("Contested ground abandoned: no workers remain. The unfinished haul was left behind.")
      else if (!site.held || count > 37L) withdraw(ready)
      else ready
    }
  }
  def lossRange(count: Long): (Int, Int) = {
    if (count <= 0L) (0, 0)
    else if (count < 10L) (((count + 4L) / 5L).toInt, ((count * 2L + 4L) / 5L).toInt)
    else RaidProgression.lossRange(Math.min(37L, count) * u)
  }
  def risk(count: Long): String = {
    val (low, high) = lossRange(count)
    s"Every 60s: $low–$high of $count workers lost (certain)."
  }
  def rate(ants: AntsData): Long = workers(ants) * ProteinPerWorkerSecond

  private def contest(data: AllData): AllData = {
    val site = data.exploration.territory
    val count = workers(data.ants)
    val (low, high) = lossRange(count)
    val seed = site.seed + site.round * 104729L
    val lost = low + new Random(seed).nextInt(high - low + 1)
    val survivors = RaidProgression.survivorAges(site.ages, lost, seed + 17L)
    val (overdue, living) = survivors.partition(_.expiresAt <= data.currentTick)
    val aging = overdue.map(_.count).sum
    val total = lost.toLong + aging
    val remaining = count - total
    val ants = data.ants.copy(workers = data.ants.workers - total * u,
      tasks = data.ants.tasks.map { case (task, amount) => task -> (if (task == AntTask.TerritoryForager) Math.max(0L, amount - total * u) else amount) })
    val resolved = data.copy(ants = ants, exploration = data.exploration.copy(territory = site.copy(
      ages = living, pendingProtein = 0L, round = site.round + 1L, nextContestTick = data.currentTick + ContestTicks)),
      statistics = data.statistics.copy(territoryDeaths = data.statistics.territoryDeaths.addValue(lost),
        workersLostToAging = data.statistics.workersLostToAging.addValue(aging), territoryContests = data.statistics.territoryContests.addValue(1L)))
    val paid = if (remaining > 0L) resolved.giveResources(protein = site.pendingProtein) else resolved
    val delivered = paid.basicResources.protein.amount - data.basicResources.protein.amount
    val report = s"Territory contest: $lost combat losses" + (if (aging > 0L) s", $aging aging losses" else "") +
      s"; ${UINumbersUtils.prettyNumberStr(delivered)} protein delivered."
    if (remaining <= 0L) withdraw(paid).addMessage(report + " The ground was lost; unfinished haul abandoned.")
    else DefaultWorkerAssignment.normalize(paid).addMessage(report)
  }
  def tick(data: AllData): AllData = {
    val ready = reconcile(data)
    if (!ready.exploration.territory.held) ready
    else {
      val staffed = if (ready.exploration.territory.maintain) reinforce(ready) else ready
      val site = staffed.exploration.territory
      val harvesting = staffed.copy(exploration = staffed.exploration.copy(territory = site.copy(
        pendingProtein = site.pendingProtein + rate(staffed.ants) / TicksPerSecond)))
      if (staffed.currentTick >= site.nextContestTick) contest(harvesting) else harvesting
    }
  }
}
