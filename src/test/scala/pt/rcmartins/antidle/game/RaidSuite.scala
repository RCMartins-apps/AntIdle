package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class RaidSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(ants = AntsData(workers = 40 * u, scouts = 3 * u,
      tasks = Seq(AntTask.SugarCollector -> (40 * u)), defaultTask = Some(AntTask.SugarCollector),
      ages = ColonyAges(active = true, workers = List(AgeCohort(5000L, 40L)), scouts = List(AgeCohort(5000L, 3L)))),
      world = d.world.copy(saveSeed = 123L, currentTick = 100L),
      nestAttributes = d.nestAttributes.copy(chambers = d.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      upgrades = d.upgrades.unlock(UpgradesData.UpgradeType.DefaultWorkers),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      basicResources = d.basicResources.copy(sugar = BasicResource(20000 * u, 30000 * u), protein = BasicResource(0L, 2000 * u)),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }
  def start(data: AllData = colony, workers: Long = 10 * u): AllData = ExplorationHelper.start(data, workers, RivalFoodCache)
  def loaded(data: AllData): AllData = AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("raid requires a studied cache, resources, ten workers, a scout guide and a slot") {
    val early = colony.copy(exploration = ExplorationData())
    assert(!RivalFoodCache.isUnlocked(early.exploration, early.ants, early.nestAttributes))
    assertEquals(start(early), early)
    assertEquals(start(colony, 9 * u), colony)
    val noScouts = colony.copy(ants = colony.ants.copy(scouts = 0L))
    assertEquals(start(noScouts), noScouts)
    val poor = colony.copy(basicResources = colony.basicResources.copy(sugar = BasicResource(299 * u, 30000 * u)))
    assertEquals(start(poor), poor)
    val sent = start()
    assertEquals(sent.basicResources.sugar.amount, colony.basicResources.sugar.amount - 300 * u)
    assertEquals(sent.ants.taskWorkers(AntTask.SugarCollector), 30 * u)
    assertEquals(ScoutProgression.available(sent.ants, sent.exploration), 2 * u)
    assertEquals(start(start(sent, 10 * u), 10 * u).exploration.explorationParties.size, 2)
  }
  test("party size changes disclosed loss and reward ranges; outcomes stay within them") {
    assertEquals(RaidProgression.lossRange(10 * u), (2, 4))
    assertEquals(RaidProgression.lossRange(37 * u), (4, 10))
    assertEquals(RaidProgression.rewardRange(10 * u), (200 * u, 350 * u))
    assertEquals(RaidProgression.rewardRange(37 * u), (740 * u, 1295 * u))
    (10 to 37).foreach { workers =>
      val (lo, hi) = RaidProgression.lossRange(workers * u)
      val (min, max) = RaidProgression.rewardRange(workers * u)
      (0L until 100L).foreach { seed =>
        val result = RaidProgression.roll(seed, workers * u)
        assert(result.workerLosses >= lo && result.workerLosses <= hi)
        assert(result.protein >= min && result.protein <= max)
        assert(result.workerLosses < workers)
      }
    }
    assert(ExplorationHelper.summary(colony, 10 * u, RivalFoodCache).contains("200–350 protein"))
    assert(RaidProgression.risk(37 * u).contains("4–10 of 37"))
  }
  test("departure prioritizes oldest home workers, leaving younger cohorts at home") {
    val d = colony.copy(ants = colony.ants.copy(ages = colony.ants.ages.copy(workers = List(
      AgeCohort(6000L, 30L), AgeCohort(2000L, 6L), AgeCohort(1000L, 4L)))))
    val sent = start(d)
    assertEquals(sent.exploration.explorationParties.head.ages.workers, List(AgeCohort(1000L, 4L), AgeCohort(2000L, 6L)))
    assertEquals(sent.ants.ages.workers, List(AgeCohort(6000L, 30L)))
  }
  test("casualties sample individuals from the party rather than always killing the oldest") {
    val cohorts = List(AgeCohort(1000L, 5L), AgeCohort(2000L, 5L))
    val results = (0L until 500L).map(seed => RaidProgression.survivorAges(cohorts, 2, seed))
    assert(results.exists(_.head.count == 5L)) // Both casualties can be from the younger cohort.
    assert(results.exists(_.last.count == 5L)) // Both can also be from the older cohort.
    assert(results.exists(_.forall(_.count == 4L)))
    results.foreach { alive => assertEquals(alive.map(_.count).sum, 8L); assertEquals(alive.map(_.expiresAt), List(1000L, 2000L)) }
  }
  test("return removes casualties and their cohorts once, preserves scouts, and grants retained rewards") {
    val sent = start()
    val party = sent.exploration.explorationParties.head
    val result = party.raidOutcome.get
    val returned = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(returned.ants.workers, (40L - result.workerLosses) * u)
    assertEquals(returned.ants.taskWorkers(AntTask.SugarCollector), returned.ants.workers)
    assertEquals(returned.ants.taskWorkers(AntTask.Explorer), 0L)
    assertEquals(returned.ants.ages.workers.map(_.count).sum, 40L - result.workerLosses)
    assert(returned.ants.ages.workers.forall(_.expiresAt == 5000L))
    assertEquals(returned.ants.scouts, 3 * u)
    assertEquals(returned.ants.ages.scouts.map(_.count).sum, 3L)
    assertEquals(returned.statistics.raidDeaths.thisRun, result.workerLosses)
    assertEquals(returned.statistics.starvationDeaths.thisRun, 0)
    assertEquals(returned.basicResources.protein.amount, result.protein)
    assertEquals(returned.statistics.proteinU.thisRun, result.protein)
    assertEquals(returned.statistics.explorationsCompleted.thisRun, 1)
    assertEquals(ExplorationHelper.exploreBonus(returned, party), returned)
  }
  test("overdue survivors die of age separately without counting combat casualties twice") {
    val d = colony.copy(ants = colony.ants.copy(ages = colony.ants.ages.copy(workers = List(
      AgeCohort(200L, 10L), AgeCohort(5000L, 30L)))))
    val sent = start(d)
    val party = sent.exploration.explorationParties.head
    val returned = ExplorationHelper.exploreBonus(sent.copy(world = sent.world.copy(currentTick = party.targetTick)), party)
    assertEquals(returned.ants.workers, 30 * u)
    assertEquals(returned.statistics.workersLostToAging.thisRun + returned.statistics.raidDeaths.thisRun, 10)
    assertEquals(returned.statistics.workersLostToAging.thisRun, 10 - party.raidOutcome.get.workerLosses)
  }
  test("saved outcomes and survivor selection survive reload, delayed returns and unrelated launches") {
    val sent = start()
    val party = sent.exploration.explorationParties.head
    val restored = loaded(sent)
    assertEquals(restored.exploration.explorationParties.head, party)
    val normal = ExplorationHelper.exploreBonus(sent, party)
    val afterReload = ExplorationHelper.exploreBonus(restored, party)
    assertEquals(afterReload.ants.workers, normal.ants.workers)
    assertEquals(afterReload.basicResources.protein.amount, normal.basicResources.protein.amount)
    val delayed = ExplorationHelper.exploreBonus(sent.copy(world = sent.world.copy(currentTick = 1000L)), party)
    assertEquals(delayed.ants.ages.workers, normal.ants.ages.workers)
    val twice = start(sent)
    assert(twice.exploration.explorationParties.map(_.raidOutcome.get.randomSeed).distinct.size == 2)
  }
  test("storage caps actual protein without reducing casualties; extra scouts and supplies do not alter raid outcome") {
    val sent = start()
    val party = sent.exploration.explorationParties.head
    val full = sent.copy(basicResources = sent.basicResources.copy(protein = BasicResource(1999 * u, 2000 * u)))
    val returned = ExplorationHelper.exploreBonus(full, party)
    assertEquals(returned.basicResources.protein.amount, 2000 * u)
    assertEquals(returned.statistics.proteinU.thisRun, u)
    assertEquals(returned.statistics.raidDeaths.thisRun, party.raidOutcome.get.workerLosses)
    val boosted = colony.copy(exploration = colony.exploration.copy(suppliesLevel = 3))
    val faster = ExplorationHelper.start(boosted, 10 * u, RivalFoodCache, 1)
    assertEquals(faster.exploration.explorationParties.head.raidOutcome, party.raidOutcome)
    assert(faster.exploration.explorationParties.head.targetTick < party.targetTick)
  }
  test("safe routes retain no combat outcome and do not add raid losses; prestige retains lifetime deaths") {
    val sent = ExplorationHelper.start(colony, 10 * u, RottenLog)
    val party = sent.exploration.explorationParties.head
    assertEquals(party.raidOutcome, None)
    val returned = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(returned.ants.workers, colony.ants.workers)
    assertEquals(returned.statistics.raidDeaths.thisRun, 0)
    val stats = StatisticsData(raidDeaths = StatisticsData.StatInt(4, 9))
    assertEquals(StatisticsData.newColony(stats).raidDeaths, StatisticsData.StatInt(0, 9))
    assertEquals("{}".fromJson[StatisticsData].toOption.get.raidDeaths.thisRun, 0)
  }
  test("age warning counts only the oldest selected cohort and includes scouts separately") {
    val ants = colony.ants.copy(ages = colony.ants.ages.copy(
      workers = List(AgeCohort(200L, 5L), AgeCohort(1000L, 35L)),
      scouts = List(AgeCohort(200L, 1L), AgeCohort(1000L, 2L))))
    assertEquals(RaidProgression.overdue(ants.ages.workers, 10L, 700L), 5L)
    val warning = RaidProgression.ageWarning(ants, 100L, 10 * u, 0)
    assert(warning.contains("5 workers"))
    assert(warning.contains("1 scout"))
    assertEquals(RaidProgression.ageWarning(colony.ants, 100L, 10 * u, 0), "")
  }

  test("actual return tick resolves casualties once and Queen's Routine replaces lost workers") {
    val prepared = colony.copy(ants = colony.ants.copy(queenRoutine = QueenRoutine(true, 40, 0L)),
      nestAttributes = colony.nestAttributes.copy(maxEggs = 5 * u, maxAnts = 100 * u),
      upgrades = colony.upgrades.unlock(UpgradesData.UpgradeType.QueensRoutine),
      unlocks = colony.unlocks.copy(actions = colony.unlocks.actions.copy(canLayEggs = true)))
    val sent = start(prepared)
    val party = sent.exploration.explorationParties.head
    val returned = TickUpdater.updateAllData(sent.copy(world = sent.world.copy(currentTick = party.targetTick - 1L)), None)
    val losses = party.raidOutcome.get.workerLosses
    assertEquals(returned.ants.workers, (40L - losses) * u)
    assertEquals(returned.ants.eggsAndLarvae.size, 1)
    assert(returned.ants.layingCooldown > 0L)
    assertEquals(returned.statistics.raidDeaths.thisRun, losses)
    assertEquals(TickUpdater.updateAllData(returned, None).statistics.raidDeaths.thisRun, losses)
    assertEquals(TickUpdater.updateAllData(loaded(returned), None).statistics.raidDeaths.thisRun, losses)
  }

}
