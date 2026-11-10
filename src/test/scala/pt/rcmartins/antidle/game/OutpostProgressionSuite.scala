package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class OutpostProgressionSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    OutpostProgression.reconcile(initial.copy(
      ants = AntsData(workers = 30 * u, tasks = Seq(AntTask.SugarCollector -> (30 * u)), defaultTask = Some(AntTask.SugarCollector),
        ages = ColonyAges(active = true, workers = List(AgeCohort(18000L, 30L)))),
      nestAttributes = initial.nestAttributes.copy(dryClearingOutpost = true, maxAnts = 100 * u),
      basicResources = initial.basicResources.copy(sugar = BasicResource(30000 * u, 40000 * u),
        protein = BasicResource(500 * u, 1000 * u), water = BasicResource(1000 * u, 1000 * u),
        tunnelingSpace = BasicResource(4000 * u, 4000 * u), knowledge = BasicResource(3000 * u, Long.MaxValue)),
      exploration = MapFixture.explored.copy(revealedSites = 6, surveys = Map("dry-clearing" -> 3)),
      upgrades = initial.upgrades.unlock(DefaultWorkers),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true))))
  }

  def levelEight(data: AllData): AllData = data.copy(exploration = data.exploration.copy(revealedSites = 6))

  test("expansions appear one at a time at their levels and stop after level three") {
    val initial = colony
    val early = initial.copy(exploration = initial.exploration.copy(revealedSites = 6))
    assert(!OutpostProgression.expansionVisible(early.nestAttributes, early.exploration))
    assertEquals(OutpostProgression.expand(early), early)
    val absent = initial.copy(nestAttributes = initial.nestAttributes.copy(dryClearingOutpost = false))
    assertEquals(OutpostProgression.expand(absent), absent)
    val second = OutpostProgression.expand(initial)
    assertEquals(OutpostProgression.level(second.nestAttributes), 2)
    assert(!OutpostProgression.expansionVisible(second.nestAttributes, second.exploration))
    assertEquals(OutpostProgression.expand(second), second)
    val third = OutpostProgression.expand(levelEight(second))
    assertEquals(OutpostProgression.level(third.nestAttributes), 3)
    assertEquals(OutpostProgression.expand(third), third)
    assertEquals(third.nestAttributes.maxAnts, initial.nestAttributes.maxAnts + 50 * u)
    assertEquals(OutpostProgression.capacity(third.nestAttributes), 15L * u)
    assertEquals(OutpostProgression.housing(third.nestAttributes), 100L * u)
    assertEquals(third.ants.populationCount, initial.ants.populationCount)
  }

  test("every expansion resource is enforced and each paid bonus is applied only once") {
    val initial = colony
    val cost = OutpostProgression.expansionCost(initial.nestAttributes)
    val base = initial.basicResources
    val shortages = Seq(
      base.copy(sugar = BasicResource(cost.sugar - 1L, base.sugar.maxAmount)),
      base.copy(protein = BasicResource(cost.protein - 1L, base.protein.maxAmount)),
      base.copy(knowledge = BasicResource(cost.knowledge - 1L, Long.MaxValue)),
      base.copy(tunnelingSpace = BasicResource(cost.tunnelingSpace - 1L, base.tunnelingSpace.maxAmount)),
      base.copy(water = BasicResource(cost.water - 1L, base.water.maxAmount)))
    shortages.foreach { resources =>
      val blocked = initial.copy(basicResources = resources)
      assertEquals(OutpostProgression.expand(blocked), blocked)
    }
    val expanded = OutpostProgression.expand(initial)
    assertEquals(expanded.basicResources, base.spendResources(cost))
    assertEquals(expanded.nestAttributes.maxAnts, initial.nestAttributes.maxAnts + 25 * u)
    assertEquals(OutpostProgression.expansionCost(expanded.nestAttributes),
      ActionCost(sugar = 8000 * u, protein = 125 * u, knowledge = 800 * u, tunnelingSpace = 1500 * u, water = 350 * u))
    assertEquals(OutpostProgression.reconcile(expanded), expanded)
  }

  test("expansion fills new default slots from existing workers and respects manual batches") {
    val pooled = DefaultWorkerAssignment.select(colony, Some(AntTask.SeedForager))
    assertEquals(pooled.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    val expanded = OutpostProgression.expand(pooled)
    assertEquals(expanded.ants.taskWorkers(AntTask.SeedForager), 10L * u)
    assertEquals(expanded.ants.idleWorkersCount, 20L * u)
    val third = OutpostProgression.expand(levelEight(expanded))
    assertEquals(third.ants.taskWorkers(AntTask.SeedForager), 15L * u)
    assertEquals(third.ants.workers, pooled.ants.workers)
    val manual = DefaultWorkerAssignment.assign(OutpostProgression.expand(colony), AntTask.SeedForager, 250 * u)
    assertEquals(manual.ants.taskWorkers(AntTask.SeedForager), 10L * u)
    assertEquals(manual.ants.taskWorkers(AntTask.SugarCollector), 20L * u)
  }

  test("new research remains hidden until an outpost and the appropriate exploration level") {
    val initial = colony
    assert(CoveredTrails.shouldShow(initial))
    assert(!SeedProcessing.shouldShow(initial))
    assert(SeedProcessing.shouldShow(levelEight(initial)))
    val absent = levelEight(initial).copy(nestAttributes = initial.nestAttributes.copy(dryClearingOutpost = false))
    assert(!CoveredTrails.shouldShow(absent))
    assert(!SeedProcessing.shouldShow(absent))
    val early = initial.copy(exploration = initial.exploration.copy(revealedSites = 6))
    assert(!CoveredTrails.shouldShow(early))
    assert(UpgradeVisibility.reconcile(initial).upgrades(CoveredTrails).show)
    assert(!UpgradeVisibility.reconcile(initial).upgrades(SeedProcessing).show)
  }

  test("research improves actual tick yields and water use, stacking with expanded staffing") {
    val expanded = OutpostProgression.expand(levelEight(OutpostProgression.expand(colony)))
    val staffed = DefaultWorkerAssignment.assign(expanded, AntTask.SeedForager, 15 * u)
    val baseline = TickUpdater.updateAllData(staffed, None)
    assertEquals(baseline.basicResources.protein.amount - staffed.basicResources.protein.amount, 1200L)
    assertEquals(staffed.basicResources.water.amount - baseline.basicResources.water.amount, 600L)
    val improved = staffed.copy(upgrades = staffed.upgrades.unlock(CoveredTrails, SeedProcessing))
    val result = TickUpdater.updateAllData(improved, None)
    assertEquals(result.basicResources.protein.amount - improved.basicResources.protein.amount, 1800L)
    assertEquals(improved.basicResources.water.amount - result.basicResources.water.amount, 450L)
    assertEquals(result.statistics.proteinU.thisRun - improved.statistics.proteinU.thisRun, 1800L)
    assertEquals(result.ants.upkeepWorkersCount, improved.ants.upkeepWorkersCount)
  }

  test("covered trails handle fractional conversion without free protein, overflow or negative water") {
    val staffed = DefaultWorkerAssignment.assign(colony, AntTask.SeedForager, 5 * u)
    val upgrades = staffed.upgrades.unlock(CoveredTrails)
    for (water <- List(0L, 1L, 2L, 3L, 29L, 30L, 149L, 150L, 200L); room <- List(0L, 1L, 2L, 3L, 399L, 400L)) {
      val resources = staffed.basicResources.copy(water = BasicResource(water, u), protein = BasicResource(1000L - room, 1000L))
      val harvest = OutpostProgression.harvestPerTick(staffed.ants, staffed.nestAttributes, upgrades, resources)
      assert(harvest.water <= water && harvest.water >= 0L)
      assert(harvest.protein <= room && harvest.protein >= 0L)
      assert(harvest.protein * 3L <= harvest.water * 8L)
      if (water == 0L || room == 0L) assertEquals(harvest, OutpostProgression.Harvest())
    }
    assertEquals(OutpostProgression.harvestPerTick(staffed.ants, staffed.nestAttributes, upgrades, staffed.basicResources),
      OutpostProgression.Harvest(protein = 400L, water = 150L))
  }

  test("expanded outposts retain housing when dry and resume without extra bonuses") {
    val expanded = DefaultWorkerAssignment.select(OutpostProgression.expand(colony), Some(AntTask.SeedForager))
    val dry = expanded.copy(basicResources = expanded.basicResources.copy(water = BasicResource(0L, 200 * u)))
    val paused = TickUpdater.updateAllData(dry, None)
    assertEquals(paused.basicResources.protein.amount, dry.basicResources.protein.amount)
    assertEquals(paused.nestAttributes.maxAnts, expanded.nestAttributes.maxAnts)
    assertEquals(paused.ants.taskWorkers(AntTask.SeedForager), 10L * u)
    val resumed = TickUpdater.updateAllData(paused.giveResources(water = u), None)
    assertEquals(resumed.basicResources.protein.amount, paused.basicResources.protein.amount + 800L)
    assertEquals(resumed.nestAttributes.maxAnts, paused.nestAttributes.maxAnts)
  }

  test("save reload preserves expansion, research and age cohorts without granting capacity again") {
    val expanded = DefaultWorkerAssignment.select(OutpostProgression.expand(colony), Some(AntTask.SeedForager))
    val upgraded = expanded.copy(upgrades = expanded.upgrades.unlock(CoveredTrails))
    val loaded = AllDataForSave.fromAllData(upgraded).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.nestAttributes.maxAnts, upgraded.nestAttributes.maxAnts)
    assertEquals(OutpostProgression.level(loaded.nestAttributes), 2)
    assertEquals(loaded.ants.taskWorkers(AntTask.SeedForager), 10L * u)
    assertEquals(loaded.ants.ages.workers, upgraded.ants.ages.workers)
    assert(loaded.upgrades(CoveredTrails).unlocked)
    val oldJson = colony.nestAttributes.toJson.replace(",\"dryClearingExtensions\":0", "")
    val oldNest = oldJson.fromJson[NestAttributes].toOption.get
    assertEquals(OutpostProgression.capacity(oldNest), 5L * u)
    assertEquals(OutpostProgression.level(oldNest), 1)
  }

  test("imported expansion values and staffing cannot bypass the final outpost limit") {
    val invalid = colony.copy(nestAttributes = colony.nestAttributes.copy(dryClearingExtensions = 99),
      ants = colony.ants.copy(tasks = Seq(AntTask.SeedForager -> (30 * u))))
    val repaired = DefaultWorkerAssignment.normalize(OutpostProgression.reconcile(invalid))
    assertEquals(repaired.nestAttributes.dryClearingExtensions, 2)
    assertEquals(repaired.ants.taskWorkers(AntTask.SeedForager), 15L * u)
    val absent = OutpostProgression.reconcile(invalid.copy(nestAttributes = invalid.nestAttributes.copy(dryClearingOutpost = false)))
    assertEquals(absent.nestAttributes.dryClearingExtensions, 0)
    assertEquals(OutpostProgression.capacity(absent.nestAttributes), 0L)
  }

  test("small resource rates remain legible and keep the correct sign") {
    assertEquals(UINumbersUtils.resourceRateStr(150L), "0.015")
    assertEquals(UINumbersUtils.resourceRateStr(-750L), "-0.075")
    assertEquals(UINumbersUtils.resourceRateStr(1000L), "0.10")
    assertEquals(UINumbersUtils.resourceRateStr(-100L), "-0.01")
  }
}
