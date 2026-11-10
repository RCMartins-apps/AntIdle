package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.PopulationAging.Minute
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class ColonyLifecycleSuite extends FunSuite {
  def colony(workers: Long = 20L, scouts: Long = 0L): AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = workers * u, scouts = scouts * u,
        tasks = Seq(AntTask.SugarCollector -> 0L, AntTask.Explorer -> 0L)),
      basicResources = initial.basicResources.copy(sugar = BasicResource(5000 * u, 10000 * u)),
      nestAttributes = initial.nestAttributes.copy(maxAnts = 1000 * u, maxEggs = 10 * u,
        chambers = initial.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      unlocks = initial.unlocks.copy(actions = initial.unlocks.actions.copy(canLayEggs = true),
        tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  def setAge(data: AllData, workerAges: List[AgeCohort], scoutAges: List[AgeCohort] = Nil): AllData =
    data.copy(ants = data.ants.copy(ages = ColonyAges(active = true, workers = workerAges, scouts = scoutAges)))

  test("aging begins with the first Nursery, spreads lifespans, and gives existing adults a full grace period") {
    val initial = colony(10000, 20)
    val early = initial.copy(nestAttributes = NestAttributes.initial)
    assertEquals(PopulationAging.reconcile(early), early)
    val started = PopulationAging.reconcile(initial)
    assert(started.ants.ages.active)
    assertEquals(started.ants.ages.workers.map(_.count).sum, 10000L)
    assertEquals(started.ants.ages.scouts.map(_.count).sum, 20L)
    assert(started.ants.ages.workers.size <= 90)
    assert(started.ants.ages.workers.forall(c => c.expiresAt >= 45L * Minute && c.expiresAt <= 60L * Minute))
    assertEquals(PopulationAging.reconcile(started), started)
    assertEquals(started.messages.count(_.startsWith("Colony aging has begun")), 1)
  }

  test("a birth batch gets seeded jitter across distinct expiry buckets and never rerolls on reload") {
    val initial = colony(12).copy(world = colony().world.copy(currentTick = 123L, saveSeed = 42L))
    val first = PopulationAging.reconcile(initial)
    val cohorts = first.ants.ages.workers
    assertEquals(cohorts.size, 12)
    assert(cohorts.forall(_.count == 1L))
    assert(cohorts.forall(c => c.expiresAt >= 123L + 45L * Minute && c.expiresAt <= 123L + 60L * Minute))
    assert(cohorts.exists(_.expiresAt % Minute != 0L))
    assertEquals(PopulationAging.reconcile(initial).ants.ages.workers, cohorts)
    assertNotEquals(PopulationAging.reconcile(initial.copy(world = initial.world.copy(saveSeed = 43L))).ants.ages.workers, cohorts)
    val loaded = AllDataForSave.fromAllData(first).toAllData
    assertEquals(loaded.ants.ages.workers, cohorts)
    assertEquals(PopulationAging.reconcile(loaded).ants.ages.workers, cohorts)
  }

  test("aging takes idle workers first, then assigned workers, with safe scout counts and grouped reports") {
    val initial = colony(5, 2)
    val assigned = initial.copy(ants = initial.ants.copy(tasks = Seq(AntTask.SugarCollector -> (3 * u))))
    val aged = setAge(assigned, List(AgeCohort(Minute, 2), AgeCohort(2 * Minute, 3)),
      List(AgeCohort(Minute, 1), AgeCohort(2 * Minute, 1)))
    val first = PopulationAging.tick(aged.copy(world = aged.world.copy(currentTick = Minute)))
    assertEquals(first.ants.workers, 3L * u)
    assertEquals(first.ants.taskWorkers(AntTask.SugarCollector), 3L * u)
    assertEquals(first.ants.scouts, u)
    assertEquals(first.messages.count(_.startsWith("Aging:")), 1)
    assertEquals(PopulationAging.tick(first), first)
    val last = PopulationAging.tick(first.copy(world = first.world.copy(currentTick = 2 * Minute)))
    assertEquals(last.ants.workers, 0L)
    assertEquals(last.ants.taskWorkers(AntTask.SugarCollector), 0L)
    assertEquals(last.ants.scouts, 0L)
    assertEquals(last.ants.idleWorkersCount, 0L)
  }

  test("workers and scouts remain safe in transit but die immediately on return after their original expiry") {
    val initial = setAge(colony(5, 1), List(AgeCohort(Minute, 5)), List(AgeCohort(Minute, 1)))
    val sent = ExplorationHelper.start(initial, 5 * u, ExplorationDestination.NectarMeadow, 1)
    assert(sent.ants.ages.workers.isEmpty && sent.ants.ages.scouts.isEmpty)
    val during = PopulationAging.tick(sent.copy(world = sent.world.copy(currentTick = Minute)))
    assertEquals(during.ants.workers, 5L * u)
    assertEquals(during.ants.scouts, u)
    val loaded = AllDataForSave.fromAllData(during).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.exploration.explorationParties.head.ages, sent.exploration.explorationParties.head.ages)
    val returned = TickUpdater.updateAllData(loaded.copy(world = loaded.world.copy(currentTick = 449L)), None)
    assert(returned.exploration.explorationParties.isEmpty)
    assertEquals(returned.ants.workers, 0L)
    assertEquals(returned.ants.scouts, 0L)
    assert(returned.ants.ages.workers.isEmpty && returned.ants.ages.scouts.isEmpty)
    assertEquals(returned.basicResources.knowledge.amount, 0L)
    assertEquals(returned.ants.taskWorkers(AntTask.Explorer), 0L)
  }

  test("simultaneous expedition returns preserve both scout cohorts without introducing fresh lifespans") {
    val initial = setAge(colony(2, 2), List(AgeCohort(Minute, 1), AgeCohort(2 * Minute, 1)),
      List(AgeCohort(Minute, 1), AgeCohort(2 * Minute, 1)))
    val first = ExplorationHelper.start(initial, u, ExplorationDestination.WoodlandEdge)
    val second = ExplorationHelper.start(first, u, ExplorationDestination.WoodlandEdge)
    val returned = TickUpdater.updateAllData(second.copy(world = second.world.copy(currentTick = 224L)), None)
    assertEquals(returned.ants.ages.workers.map(_.expiresAt), List(Minute, 2L * Minute))
    assertEquals(returned.ants.ages.scouts.map(_.expiresAt), List(Minute, 2L * Minute))
    assertEquals(returned.ants.ages.scouts.map(_.count).sum, 2L)
  }

  test("repeated expeditions cannot extend the same ants' original expiry or let overdue ants depart again") {
    val initial = setAge(colony(1, 1), List(AgeCohort(Minute + 17L, 1)), List(AgeCohort(Minute + 17L, 1)))
    val first = ExplorationHelper.start(initial, u, ExplorationDestination.WoodlandEdge)
    val returned = TickUpdater.updateAllData(first.copy(world = first.world.copy(currentTick = 224L)), None)
    assertEquals(returned.ants.ages.workers, List(AgeCohort(Minute + 17L, 1)))
    assertEquals(returned.ants.ages.scouts, List(AgeCohort(Minute + 17L, 1)))
    val second = ExplorationHelper.start(returned, u, ExplorationDestination.WoodlandEdge)
    assertEquals(second.exploration.explorationParties.head.ages.workers, List(AgeCohort(Minute + 17L, 1)))
    val expired = TickUpdater.updateAllData(second.copy(world = second.world.copy(currentTick = 449L)), None)
    assertEquals(expired.ants.workers, 0L)
    assertEquals(expired.ants.scouts, 0L)
    assertEquals(ExplorationHelper.start(expired, u, ExplorationDestination.WoodlandEdge), expired)
  }

  test("home and return deaths in the same tick still produce only one periodic summary") {
    val initial = setAge(colony(2), List(AgeCohort(Minute, 2)))
    val sent = ExplorationHelper.start(initial, u, ExplorationDestination.WoodlandEdge)
    val due = sent.copy(world = sent.world.copy(currentTick = Minute - 1L),
      exploration = sent.exploration.copy(explorationParties = sent.exploration.explorationParties.map(_.copy(targetTick = Minute))))
    val returned = TickUpdater.updateAllData(due, None)
    assertEquals(returned.ants.workers, 0L)
    assertEquals(returned.messages.count(_.startsWith("Aging:")), 1)
    assertEquals(returned.ants.ages.pendingWorkers, 1L)
    val nextMinute = PopulationAging.tick(returned.copy(world = returned.world.copy(currentTick = 2L * Minute)))
    assertEquals(nextMinute.messages.count(_.startsWith("Aging:")), 2)
    assertEquals(nextMinute.ants.ages.pendingWorkers, 0L)
  }

  test("carcass carriers complete retrieval but overdue workers die on return") {
    val initial = setAge(colony(5), List(AgeCohort(Minute, 5)))
    val carcass = Carcass(120 * u, 120 * u, 5 * u, 5 * u, 450L)
    val sent = CarcassProgression.retrieve(initial.copy(exploration = initial.exploration.copy(discoveredCarcasses = List(carcass))), 0)
    assert(sent.ants.ages.workers.isEmpty)
    val during = PopulationAging.tick(sent.copy(world = sent.world.copy(currentTick = Minute)))
    assertEquals(during.ants.workers, 5L * u)
    val returned = TickUpdater.updateAllData(during.copy(world = during.world.copy(currentTick = 449L)), None)
    assertEquals(returned.ants.workers, 0L)
    assert(returned.ants.ages.workers.isEmpty)
    assertEquals(returned.exploration.carcasses.size, 1)
    assertEquals(returned.basicResources.protein.amount, 5L * u)
  }

  test("newly matured adults receive new lifespans and starvation never kills away workers") {
    val initial = PopulationAging.reconcile(colony(1))
    val growing = initial.copy(ants = initial.ants.copy(eggsAndLarvae = Seq(AntBrood.Egg(0L))), world = initial.world.copy(currentTick = 50L))
    val grown = ScoutProgression.growBrood(growing)
    assertEquals(grown.ants.ages.workers.map(_.count).sum, 2L)
    assert(grown.ants.ages.workers.forall(_.expiresAt >= 45L * Minute))
    val away = colony(5, 1)
    val protectedAnts = away.copy(ants = away.ants.copy(tasks = Seq(AntTask.Explorer -> (5 * u))))
    val starved = PopulationAging.starveOne(protectedAnts)
    assertEquals(starved.workers, 5L * u)
    assertEquals(starved.scouts, 0L)
  }

  test("queen automation is opt-in and locked configuration cannot lay eggs") {
    val initial = colony(0)
    assertEquals(AutomaticBrood.configure(initial, true, 10, 0), initial)
    assertEquals(AutomaticBrood.tick(initial), initial)
    val unlocked = initial.copy(upgrades = initial.upgrades.unlock(QueensRoutine))
    assertEquals(AutomaticBrood.tick(unlocked), unlocked)
  }

  test("automatic brood respects sugar reserves, target population and eggs already growing") {
    val initial = colony(1).copy(upgrades = UpgradesData.initial.unlock(QueensRoutine))
    val budget = initial.copy(basicResources = initial.basicResources.copy(sugar = BasicResource(35 * u, 1000 * u)))
    val configured = AutomaticBrood.configure(budget, true, 5, 20 * u)
    val first = AutomaticBrood.tick(configured)
    assertEquals(first.ants.eggsAndLarvae.size, 1)
    assertEquals(first.basicResources.sugar.amount, 25L * u)
    assertEquals(AutomaticBrood.tick(first), first)
    val targeted = AutomaticBrood.configure(initial, true, 2, 100 * u)
    val full = AutomaticBrood.tick(targeted)
    assertEquals(full.ants.eggsAndLarvae.size, 1)
    assertEquals(AutomaticBrood.tick(full), full)
    assertEquals(full.statistics.eggsCount, initial.statistics.eggsCount.addValue(1))
  }

  test("automatic brood shares egg and population slots with scout eggs") {
    val initial = colony(5, 2).copy(upgrades = UpgradesData.initial.unlock(QueensRoutine))
    val small = initial.copy(nestAttributes = initial.nestAttributes.copy(maxAnts = 9 * u, maxEggs = 2 * u),
      ants = initial.ants.copy(eggsAndLarvae = Seq(AntBrood.ScoutEgg(0L))))
    val result = AutomaticBrood.tick(AutomaticBrood.configure(small, true, 20, 0))
    assertEquals(BroodProgression.incoming(result.ants), 1)
    assertEquals(result.ants.populationCount, 9)
    assertEquals(AutomaticBrood.tick(result), result)
  }

  test("the tick engine automatically replaces aging losses and returns new workers to their default task") {
    val original = colony(3).copy(upgrades = UpgradesData.initial.unlock(QueensRoutine, DefaultWorkers))
    val assigned = original.copy(ants = original.ants.copy(tasks = Seq(AntTask.SugarCollector -> (3 * u)), defaultTask = Some(AntTask.SugarCollector)))
    val aged = setAge(assigned, List(AgeCohort(Minute, 1), AgeCohort(60 * Minute, 2)))
    val configured = AutomaticBrood.configure(aged.copy(basicResources = aged.basicResources.copy(protein = BasicResource(10 * u, 100 * u))), true, 3, 100 * u)
    val first = TickUpdater.updateAllData(configured.copy(world = configured.world.copy(currentTick = Minute - 1L)), None)
    assertEquals(first.ants.workers, 2L * u)
    assertEquals(first.ants.eggsAndLarvae.size, 1)
    val grown = (1 to 600).foldLeft(first)((data, _) => TickUpdater.updateAllData(data, None))
    assertEquals(grown.ants.workers, 3L * u)
    assertEquals(grown.ants.taskWorkers(AntTask.SugarCollector), 3L * u)
    assert(grown.ants.eggsAndLarvae.isEmpty)
    assertEquals(grown.ants.ages.workers.map(_.count).sum, 3L)
    val loaded = AllDataForSave.fromAllData(grown).toAllData
    assertEquals(loaded.ants.queenRoutine, grown.ants.queenRoutine)
    assertEquals(loaded.ants.ages, grown.ants.ages)
  }
}
