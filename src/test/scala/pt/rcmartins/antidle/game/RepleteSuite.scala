package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.{ChamberType, RepleteChamber}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class RepleteSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(ants = AntsData(workers = 5 * u, tasks = Seq(AntTask.SugarCollector -> (5 * u)), defaultTask = Some(AntTask.SugarCollector),
      ages = ColonyAges(active = true, workers = List(AgeCohort(300L, 5L)))),
      world = initial.world.copy(currentTick = 100L),
      nestAttributes = initial.nestAttributes.copy(maxAnts = 5 * u,
        chambers = initial.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1), repleteChamber = RepleteChamber(1))),
      basicResources = initial.basicResources.copy(sugar = BasicResource(5000 * u, 10000 * u), protein = BasicResource(100 * u, 100 * u), water = BasicResource(75 * u, 100 * u)),
      upgrades = initial.upgrades.unlock(RepleteStorage, DefaultWorkers))
  }

  test("promotion converts a default-pool worker, conserves population, charges costs and grants a fresh long lifespan") {
    val initial = colony
    val promoted = RepleteProgression.promote(initial)
    assertEquals(promoted.ants.workers, 4L * u)
    assertEquals(promoted.ants.repletes, u)
    assertEquals(promoted.ants.taskWorkers(AntTask.SugarCollector), 4L * u)
    assertEquals(promoted.ants.idleWorkersCount, 0L)
    assertEquals(promoted.ants.populationCount, initial.ants.populationCount)
    assertEquals(promoted.ants.upkeepWorkersCount, initial.ants.upkeepWorkersCount)
    assertEquals(promoted.basicResources.sugar.amount, initial.basicResources.sugar.amount - 100 * u)
    assertEquals(promoted.basicResources.protein.amount, 95L * u)
    assertEquals(promoted.basicResources.water.amount, 75L * u)
    assertEquals(promoted.basicResources.water.maxAmount, 200L * u)
    assertEquals(promoted.ants.ages.workers, List(AgeCohort(300L, 4L)))
    assertEquals(promoted.ants.ages.repletes.map(_.count).sum, 1L)
    assert(promoted.ants.ages.repletes.forall(c => c.expiresAt >= 100L + 90L * PopulationAging.Minute && c.expiresAt <= 100L + 120L * PopulationAging.Minute))
    assertEquals(promoted.statistics.repletesPromoted.thisRun, 1)
    assertEquals(promoted.statistics.eggsCount.thisRun, 0)
  }

  test("promotion always removes the oldest home age cohort and renews even a near-expiry worker") {
    val initial = colony.copy(ants = colony.ants.copy(ages = ColonyAges(active = true,
      workers = List(AgeCohort(18000L, 4L), AgeCohort(101L, 1L)))))
    val promoted = RepleteProgression.promote(initial)
    assertEquals(promoted.ants.ages.workers, List(AgeCohort(18000L, 4L)))
    assert(promoted.ants.ages.repletes.head.expiresAt >= 100L + 90L * PopulationAging.Minute)
    val nourished = RepleteProgression.promote(initial.copy(upgrades = initial.upgrades.unlock(ColonyNutrition)))
    assert(nourished.ants.ages.repletes.head.expiresAt >= 100L + 108L * PopulationAging.Minute)
    assert(nourished.ants.ages.repletes.head.expiresAt <= 100L + 144L * PopulationAging.Minute)
  }

  test("housing, research, resources and available workers are required, without stealing assigned jobs") {
    val full = RepleteProgression.promote(colony)
    assertEquals(RepleteProgression.promote(full), full)
    val noHouse = colony.copy(nestAttributes = colony.nestAttributes.copy(chambers = colony.nestAttributes.chambers.copy(repleteChamber = RepleteChamber(0))))
    assertEquals(RepleteProgression.promote(noHouse), noHouse)
    val locked = colony.copy(upgrades = UpgradesData.initial)
    assertEquals(RepleteProgression.promote(locked), locked)
    val assigned = colony.copy(ants = colony.ants.copy(defaultTask = None))
    assertEquals(RepleteProgression.promote(assigned), assigned)
    val poor = colony.copy(basicResources = colony.basicResources.copy(protein = BasicResource(0L, 100 * u)))
    assertEquals(RepleteProgression.promote(poor), poor)
    val expanded = ChamberBonuses.completeUpgrade(full, ChamberType.Replete)
    assertEquals(expanded.basicResources.water.maxAmount, 200L * u)
    assertEquals(RepleteProgression.promote(expanded).basicResources.water.maxAmount, 300L * u)
    assertEquals(ChamberBonuses.forChamber(ChamberType.Replete).repleteSlots, 1)
  }

  test("aging reduces water capacity but retains every stored unit across ticks, awards and reloads") {
    val promoted = RepleteProgression.promote(colony)
    val dying = promoted.copy(ants = promoted.ants.copy(ages = promoted.ants.ages.copy(
      workers = List(AgeCohort(18000L, 4L)), repletes = List(AgeCohort(300L, 1L)))),
      world = promoted.world.copy(currentTick = 299L),
      basicResources = promoted.basicResources.copy(water = BasicResource(180 * u, 200 * u)))
    val lost = TickUpdater.updateAllData(dying, None)
    assertEquals(lost.ants.repletes, 0L)
    assertEquals(lost.basicResources.water, BasicResource(180 * u, 100 * u))
    assertEquals(lost.statistics.repletesLostToAging.thisRun, 1)
    val waiting = TickUpdater.updateAllData(lost, None).giveResources(water = 20 * u, sugar = u)
    assertEquals(waiting.basicResources.water, lost.basicResources.water)
    assertEquals(waiting.statistics.waterU.thisRun, 0L)
    val loaded = AllDataForSave.fromAllData(waiting).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.basicResources.water, lost.basicResources.water)
    val spent = loaded.spendResources(ActionCost(water = 90 * u)).giveResources(water = 20 * u)
    assertEquals(spent.basicResources.water.amount, 100L * u)
    assertEquals(spent.statistics.waterU.thisRun, 10L * u)
    val replaced = RepleteProgression.promote(loaded)
    assertEquals(replaced.basicResources.water, BasicResource(180 * u, 200 * u))
    assertEquals(replaced.giveResources(water = 50 * u).basicResources.water.amount, 200L * u)
  }

  test("generic resource additions never truncate existing over-capacity stores") {
    val stored = BasicResource(180 * u, 100 * u)
    assertEquals(stored.freeSpace, 0L)
    assertEquals(stored + 0L, stored)
    assertEquals(stored + (50 * u), stored)
    assertEquals((stored - (90 * u)) + (20 * u), BasicResource(100 * u, 100 * u))
  }

  test("repletes occupy ant slots and consume upkeep, but are unavailable for worker tasks or expeditions") {
    val promoted = RepleteProgression.promote(colony)
    assertEquals(promoted.ants.availableWorkersCount, 4L * u)
    assert(!promoted.ants.hasIdleWorkersForCost(ActionCost(idleWorkers = 5 * u)))
    val automatic = promoted.copy(upgrades = promoted.upgrades.unlock(QueensRoutine),
      unlocks = promoted.unlocks.copy(actions = promoted.unlocks.actions.copy(canLayEggs = true)))
    val configured = AutomaticBrood.configure(automatic, true, 5, 100 * u)
    assertEquals(AutomaticBrood.tick(configured).ants.eggsAndLarvae.size, 0)
    val isolated = promoted.copy(ants = AntsData(repletes = u, ages = ColonyAges(active = true, repletes = List(AgeCohort(18000L, 1L)))))
    val ticked = TickUpdater.updateAllData(isolated, None)
    assertEquals(ticked.basicResources.sugar.amount, isolated.basicResources.sugar.amount - antsSugarUpkeepTick)
  }

  test("replete research, chamber and age state survive load and stay hidden before dry-area discovery") {
    val initial = AllData.initial
    assert(!RepleteStorage.shouldShow(initial))
    val found = initial.copy(exploration = ExplorationData(surveys = Map("dry-clearing" -> 3)),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
    assert(RepleteStorage.shouldShow(found))
    val promoted = ResourceVisibility.reconcile(RepleteProgression.promote(colony))
    val loaded = AllDataForSave.fromAllData(promoted).toAllData
    assertEquals(loaded.ants.repletes, u)
    assertEquals(loaded.ants.ages.repletes, promoted.ants.ages.repletes)
    assertEquals(loaded.basicResources.water, promoted.basicResources.water)
    assert(loaded.unlocks.resources.showRepletes)
    assertEquals(loaded.nestAttributes.chambers.repleteChamber.level, 1)
  }
}
