package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class TerritorySuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(world = d.world.copy(saveSeed = 123L, currentTick = 100L, targetTick = 100L),
      ants = AntsData(workers = 40 * u, scouts = 2 * u,
        tasks = Seq(AntTask.SugarCollector -> (40 * u)), defaultTask = Some(AntTask.SugarCollector),
        ages = ColonyAges(active = true, workers = List(AgeCohort(1000L, 15L), AgeCohort(2000L, 25L)), scouts = List(AgeCohort(2000L, 2L)))),
      basicResources = d.basicResources.copy(sugar = BasicResource(20000 * u, 30000 * u), protein = BasicResource(0L, 2000 * u)),
      nestAttributes = d.nestAttributes.copy(maxAnts = 100 * u, maxEggs = 5 * u,
        chambers = d.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      upgrades = d.upgrades.unlock(UpgradesData.UpgradeType.DefaultWorkers),
      exploration = MapFixture.explored.copy(revealedSites = 6, surveys = Map("rival-food-cache" -> 1)),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }
  def held: AllData = TerritoryProgression.claim(colony, 10)
  def advance(data: AllData): AllData = TerritoryProgression.tick(data.copy(world = data.world.copy(currentTick = data.currentTick + 1L)))
  def roundTrip(data: AllData): AllData = AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData
  def battle(data: AllData): AllData = TerritoryProgression.tick(data.copy(world = data.world.copy(currentTick = data.exploration.territory.nextContestTick)))

  test("a completed raid discovers the site once; holding requires discovery and its complete cost") {
    val undiscovered = colony.copy(exploration = colony.exploration.copy(surveys = Map.empty))
    assert(!TerritoryProgression.visible(undiscovered.exploration))
    assertEquals(TerritoryProgression.claim(undiscovered, 10), undiscovered)
    val sent = ExplorationMap.scout(undiscovered, "rival-food-cache", 1)
    val returned = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assert(TerritoryProgression.visible(returned.exploration))
    assertEquals(returned.messages.count(_.startsWith("Discovery: Contested Foraging Ground!")), 1)
    assertEquals(DiscoveryProgression.recordReturn(returned, ExplorationDestination.RivalFoodCache), returned)
    val poor = colony.copy(basicResources = colony.basicResources.copy(sugar = BasicResource(199 * u, 30000 * u)))
    assertEquals(TerritoryProgression.claim(poor, 10), poor)
    val busy = colony.copy(ants = colony.ants.copy(tasks = Seq(AntTask.Tunneler -> (40 * u)), defaultTask = None))
    assertEquals(TerritoryProgression.claim(busy, 10), busy)
  }
  test("holding charges once, reserves oldest workers and preserves normal upkeep and expedition slots") {
    val started = held
    assertEquals(started.basicResources.sugar.amount, colony.basicResources.sugar.amount - 200 * u)
    assertEquals(started.ants.taskWorkers(AntTask.TerritoryForager), 10 * u)
    assertEquals(started.ants.taskWorkers(AntTask.SugarCollector), 30 * u)
    assertEquals(started.exploration.territory.ages, List(AgeCohort(1000L, 10L)))
    assertEquals(started.ants.ages.workers, List(AgeCohort(1000L, 5L), AgeCohort(2000L, 25L)))
    assertEquals(started.ants.upkeepWorkersCount, colony.ants.upkeepWorkersCount)
    assertEquals(ScoutProgression.available(started.ants, started.exploration), 2 * u)
    assert(started.exploration.explorationParties.isEmpty)
    assertEquals(TerritoryProgression.claim(started, 10), started)
    assert(!started.ants.defaultTaskChoices.contains(AntTask.TerritoryForager))
    assertEquals(PopulationAging.reconcile(started).ants.ages, started.ants.ages)
  }
  test("protein is pending until the full contest interval, then retained delivery is counted once") {
    val started = held
    val almost = (1L until TerritoryProgression.ContestTicks).foldLeft(started)((d, _) => advance(d))
    assertEquals(almost.basicResources.protein.amount, 0L)
    assertEquals(almost.statistics.territoryContests.thisRun, 0)
    val complete = advance(almost)
    assertEquals(complete.basicResources.protein.amount, 180 * u)
    assertEquals(complete.statistics.proteinU.thisRun, 180 * u)
    assertEquals(complete.exploration.territory.pendingProtein, 0L)
    assertEquals(complete.statistics.territoryContests.thisRun, 1)
    assert(complete.statistics.territoryDeaths.thisRun >= 2 && complete.statistics.territoryDeaths.thisRun <= 4)
    assertEquals(complete.ants.workers / u, 40L - complete.statistics.territoryDeaths.thisRun)
    assertEquals(complete.exploration.territory.nextContestTick, complete.currentTick + TerritoryProgression.ContestTicks)
    assertEquals(TerritoryProgression.tick(complete).statistics.territoryContests.thisRun, 1)
  }
  test("withdrawal returns survivor cohorts, forfeits pending haul and resets no lifespan") {
    val collecting = advance(held)
    assert(collecting.exploration.territory.pendingProtein > 0L)
    val returned = TerritoryProgression.withdraw(collecting)
    assert(!returned.exploration.territory.held)
    assertEquals(returned.ants.workers, colony.ants.workers)
    assertEquals(returned.ants.taskWorkers(AntTask.SugarCollector), 40 * u)
    assertEquals(returned.ants.ages.workers, colony.ants.ages.workers)
    assertEquals(returned.basicResources.protein.amount, 0L)
    assertEquals(returned.exploration.territory.pendingProtein, 0L)
    assertEquals(returned.statistics.territoryDeaths.thisRun, 0)
    val again = TerritoryProgression.claim(returned, 10)
    assert(again.exploration.territory.seed != collecting.exploration.territory.seed)
  }
  test("full storage cannot suppress combat or award discarded haul statistics") {
    val full = held.copy(basicResources = held.basicResources.copy(protein = BasicResource(2000 * u, 2000 * u)),
      exploration = held.exploration.copy(territory = held.exploration.territory.copy(pendingProtein = 100 * u)))
    val result = battle(full)
    assert(result.statistics.territoryDeaths.thisRun > 0)
    assertEquals(result.statistics.proteinU.thisRun, 0L)
    assertEquals(result.basicResources.protein.amount, 2000 * u)
  }
  test("saved contest seed, round, timer and age cohorts survive reload without rerolling") {
    val prepared = advance(held)
    val loaded = roundTrip(prepared)
    assertEquals(loaded.exploration.territory, prepared.exploration.territory)
    val first = battle(prepared)
    val second = battle(loaded)
    assertEquals(first.statistics.territoryDeaths, second.statistics.territoryDeaths)
    assertEquals(first.exploration.territory.ages, second.exploration.territory.ages)
    assertEquals(battle(roundTrip(first)).statistics.territoryContests.thisRun, 2)
    assertEquals("{}".fromJson[ExplorationData].toOption.get.territory, Territory())
  }
  test("reinforcement borrows only from idle/default pool and never changes cohort expiry") {
    val hit = battle(held)
    val reinforced = TerritoryProgression.reinforce(hit)
    assertEquals(TerritoryProgression.workers(reinforced.ants), 10L)
    assertEquals(reinforced.ants.workers, hit.ants.workers)
    assert(reinforced.exploration.territory.ages.forall(_.expiresAt == 1000L))
    val protectedJobs = hit.copy(ants = hit.ants.copy(defaultTask = None,
      tasks = hit.ants.tasks.map { case (task, amount) => (if (task == AntTask.SugarCollector) AntTask.Tunneler else task) -> amount }))
    assertEquals(TerritoryProgression.reinforce(protectedJobs).ants, protectedJobs.ants)
    val maintained = advance(TerritoryProgression.setMaintain(hit, true))
    assertEquals(TerritoryProgression.workers(maintained.ants), 10L)
    assert(maintained.exploration.territory.maintain)
    assertEquals(TerritoryProgression.workers(advance(hit).ants), TerritoryProgression.workers(hit.ants))
  }
  test("overdue holders die at contest or withdrawal without immortality or double-counted combat") {
    val old = held.copy(exploration = held.exploration.copy(territory = held.exploration.territory.copy(ages = List(AgeCohort(200L, 10L)), pendingProtein = 100 * u)))
    val result = battle(old)
    assertEquals(result.ants.workers, 30 * u)
    assertEquals(result.statistics.territoryDeaths.thisRun + result.statistics.workersLostToAging.thisRun, 10)
    assert(!result.exploration.territory.held)
    assertEquals(result.basicResources.protein.amount, 0L)
    val withdrawn = TerritoryProgression.withdraw(old.copy(world = old.world.copy(currentTick = 200L)))
    assertEquals(withdrawn.ants.workers, 30 * u)
    assertEquals(withdrawn.statistics.workersLostToAging.thisRun, 10)
    assertEquals(withdrawn.statistics.territoryDeaths.thisRun, 0)
  }
  test("an understaffed crew can be wiped out, forfeiting the haul without automatic reclaim") {
    val one = held.copy(ants = held.ants.copy(workers = 31 * u,
      tasks = Seq(AntTask.SugarCollector -> (30 * u), AntTask.TerritoryForager -> u)),
      exploration = held.exploration.copy(territory = held.exploration.territory.copy(
        ages = List(AgeCohort(1000L, 1L)), pendingProtein = 100 * u, maintain = true)))
    // Protect home jobs so automatic staffing cannot borrow them.
    val locked = one.copy(ants = one.ants.copy(defaultTask = None))
    val result = battle(locked)
    assert(!result.exploration.territory.held)
    assertEquals(result.statistics.territoryDeaths.thisRun, 1)
    assertEquals(result.ants.workers, 30 * u)
    assertEquals(result.basicResources.protein.amount, 0L)
  }
  test("held workers remain vulnerable to starvation and cohort reconciliation removes the lost ant") {
    val alone = held.copy(ants = held.ants.copy(workers = u, scouts = 0L,
      tasks = Seq(AntTask.TerritoryForager -> u), defaultTask = None,
      sugarCumulativeDebt = AntDeathRiskThreshold * 100L, ages = ColonyAges(active = true)),
      basicResources = held.basicResources.copy(sugar = BasicResource(0L, 30000 * u)),
      exploration = held.exploration.copy(territory = held.exploration.territory.copy(ages = List(AgeCohort(1000L, 1L)))))
    val result = TickUpdater.updateAllData(alone, None)
    assertEquals(result.ants.workers, 0L)
    assertEquals(result.statistics.starvationDeaths.thisRun, 1)
    assert(!result.exploration.territory.held)
    assertEquals(result.exploration.territory.ages, Nil)
  }
  test("Queen's Routine responds to recurring casualties and prestige keeps lifetime contest totals") {
    val d = held.copy(ants = held.ants.copy(queenRoutine = QueenRoutine(true, 40, 0L)),
      upgrades = held.upgrades.unlock(UpgradesData.UpgradeType.QueensRoutine),
      unlocks = held.unlocks.copy(actions = held.unlocks.actions.copy(canLayEggs = true)))
    val returned = TickUpdater.updateAllData(d.copy(world = d.world.copy(currentTick = d.exploration.territory.nextContestTick - 1L)), None)
    assertEquals(returned.ants.eggsAndLarvae.size, 1)
    assert(returned.ants.layingCooldown > 0L)
    val stats = StatisticsData.newColony(returned.statistics)
    assertEquals(stats.territoryDeaths.thisRun, 0)
    assertEquals(stats.territoryDeaths.total, returned.statistics.territoryDeaths.total)
    val founded = Prestige.found(returned.copy(ants = returned.ants.copy(daughterQueens = u)))
    assertEquals(founded.exploration.territory, Territory())
    assertEquals(founded.ants.taskWorkers(AntTask.TerritoryForager), 0L)
  }
  test("home aging cannot remove territory holders and becoming age-enabled gives them valid cohorts") {
    val homeOld = held.copy(ants = held.ants.copy(ages = held.ants.ages.copy(workers = List(AgeCohort(101L, 30L)))))
    val aged = PopulationAging.tick(homeOld.copy(world = homeOld.world.copy(currentTick = 101L)))
    assertEquals(aged.ants.workers, 10 * u)
    assertEquals(aged.ants.taskWorkers(AntTask.TerritoryForager), 10 * u)
    assertEquals(aged.ants.upkeepWorkersCount, 12L)
    val missing = held.copy(exploration = held.exploration.copy(territory = held.exploration.territory.copy(ages = Nil)))
    val repaired = TerritoryProgression.reconcile(missing)
    assertEquals(repaired.exploration.territory.ages.map(_.count).sum, 10L)
    assertEquals(TerritoryProgression.reconcile(repaired).exploration.territory.ages, repaired.exploration.territory.ages)
  }

  test("older saves with a completed raid recover the earned discovery without carrying it across prestige") {
    val old = colony.copy(exploration = colony.exploration.copy(surveys = Map.empty),
      statistics = colony.statistics.copy(raidDeaths = StatisticsData.StatInt(3, 3)))
    assert(TerritoryProgression.visible(roundTrip(old).exploration))
    val newRun = old.copy(statistics = StatisticsData.newColony(old.statistics))
    assert(!TerritoryProgression.visible(roundTrip(newRun).exploration))
  }

}
