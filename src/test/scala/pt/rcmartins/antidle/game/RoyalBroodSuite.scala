package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class RoyalBroodSuite extends FunSuite {
  import RoyalBroodProgression._

  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 35 * u,
        tasks = Seq(AntTask.SugarCollector -> (30 * u), AntTask.RoyalNurse -> (5 * u)),
        defaultTask = Some(AntTask.SugarCollector),
        ages = ColonyAges(active = true, workers = List(AgeCohort(18000L, 35L)))),
      basicResources = initial.basicResources.copy(sugar = BasicResource(20000 * u, 40000 * u),
        protein = BasicResource(500 * u, 500 * u), water = BasicResource(100 * u, 100 * u)),
      nestAttributes = initial.nestAttributes.copy(maxAnts = 100 * u,
        chambers = initial.nestAttributes.chambers.copy(royalNurseryChamber = Chamber.RoyalNurseryChamber(1))),
      upgrades = initial.upgrades.unlock(DefaultWorkers, RoyalNurseryPlanning))
  }

  def rearing(progress: Long = 0L, paused: Boolean = false): AllData =
    colony.copy(ants = colony.ants.copy(royalBrood = Some(RoyalBrood(progress, paused))))

  def roundTrip(data: AllData): AllData =
    AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("raising reserves one colony and royal slot, spends once and requires real staff and supplies") {
    val initial = colony
    assert(canStart(initial))
    val started = start(initial)
    assertEquals(started.basicResources, initial.basicResources.spendResources(startCost))
    assertEquals(started.ants.populationCount, initial.ants.populationCount + 1)
    assertEquals(started.ants.workers, initial.ants.workers)
    assertEquals(started.ants.eggsAndLarvae, initial.ants.eggsAndLarvae)
    assertEquals(freeSlots(started.ants, started.nestAttributes), 0L)
    assertEquals(started.statistics.eggsCount.thisRun, 1)
    assertEquals(started.statistics.royalEggsRaised.thisRun, 1)
    assertEquals(start(started), started)
    val blocked = Seq(
      initial.copy(nestAttributes = NestAttributes()),
      initial.copy(nestAttributes = initial.nestAttributes.copy(maxAnts = 35 * u)),
      initial.copy(ants = initial.ants.copy(tasks = Seq(AntTask.SugarCollector -> (35 * u)))),
      initial.copy(basicResources = initial.basicResources.copy(protein = BasicResource(49 * u, 500 * u))),
      initial.copy(ants = initial.ants.copy(daughterQueens = u)))
    blocked.foreach { data => assert(!canStart(data)); assertEquals(start(data), data) }
  }

  test("care uses staffed nurses and stage-specific costs, independent of worker egg slots") {
    val egg = rearing().copy(nestAttributes = rearing().nestAttributes.copy(maxEggs = 0L))
    val larva = rearing(EggCare)
    val pupa = rearing(LarvaEnd)
    Seq((egg, tendingUnitCost), (larva, larvaUnitCost), (pupa, tendingUnitCost)).foreach { case (data, price) =>
      val next = tick(data)
      assertEquals(next.ants.royalBrood.get.careUnits, data.ants.royalBrood.get.careUnits + 5L)
      assertEquals(next.basicResources, data.basicResources.spendResources(scaledCost(price, 5L)))
    }
    assertEquals(remainingTicks(RoyalBrood(), 5L), 600L * TicksPerSecond)
    assertEquals(remainingTicks(RoyalBrood(), 1L), 3000L * TicksPerSecond)
    assertEquals(remainingTicks(RoyalBrood(), 0L), -1L)
  }

  test("ten minutes of full staffing conserves the complete feeding budget and matures exactly once") {
    val initial = start(colony)
    val almost = (1L until 600L * TicksPerSecond).foldLeft(initial)((data, _) => tick(data))
    assertEquals(almost.ants.daughterQueens, 0L)
    assertEquals(almost.ants.royalBrood.get.careUnits, TotalCare - 5L)
    val mature = tick(almost)
    val fullPrice = ActionCost(sugar = 1700 * u, protein = 170 * u, water = 50 * u)
    assertEquals(mature.basicResources, colony.basicResources.spendResources(fullPrice))
    assertEquals(mature.ants.daughterQueens, u)
    assertEquals(mature.ants.royalBrood, None)
    assertEquals(mature.ants.populationCount, initial.ants.populationCount)
    assertEquals(mature.ants.workers, colony.ants.workers)
    assertEquals(mature.statistics.daughterQueensMatured.thisRun, 1)
    assertEquals(mature.messages.count(_ == "A daughter queen has matured!"), 1)
    assertEquals(tick(mature), mature)
    assertEquals(start(mature), mature)
    assertEquals(mature.upgrades, colony.upgrades)
  }

  test("partial supplies buy proportional care and never spend resources across a stage boundary") {
    val data = rearing(EggCare).copy(basicResources = colony.basicResources.copy(protein = BasicResource(250L, 500 * u)))
    val partial = tick(data)
    assertEquals(partial.ants.royalBrood.get.careUnits, EggCare + 2L)
    assertEquals(partial.basicResources.protein.amount, 50L)
    assertEquals(tick(partial), partial)
    assert(status(data.ants, data.nestAttributes, data.basicResources).contains("Limited care · protein"))
    assertEquals(status(partial.ants, partial.nestAttributes, partial.basicResources), "Needs protein")
    val crossing = rearing(EggCare - 1L)
    val next = tick(crossing)
    assertEquals(next.ants.royalBrood.get.careUnits, EggCare)
    assertEquals(next.basicResources, crossing.basicResources.spendResources(tendingUnitCost))
    assertEquals(tick(next).basicResources, next.basicResources.spendResources(scaledCost(larvaUnitCost, 5L)))
  }

  test("manual pause, missing staff, chamber or water preserve progress without feeding costs") {
    val initial = rearing(EggCare)
    val paused = setPaused(initial, true)
    assertEquals(tick(paused), paused)
    assertEquals(tick(setPaused(paused, false)), tick(initial))
    val noStaff = initial.copy(ants = initial.ants.copy(tasks = Seq(AntTask.SugarCollector -> (35 * u))))
    assertEquals(tick(reconcile(noStaff)), reconcile(noStaff))
    assertEquals(status(noStaff.ants, noStaff.nestAttributes, noStaff.basicResources), "Needs Royal Nurses")
    val dry = initial.copy(basicResources = initial.basicResources.copy(water = BasicResource(0L, 100 * u)))
    assertEquals(tick(dry), dry)
    assertEquals(status(dry.ants, dry.nestAttributes, dry.basicResources), "Needs water")
    val noChamber = initial.copy(nestAttributes = NestAttributes())
    assertEquals(tick(noChamber), noChamber)
    assertEquals(status(noChamber.ants, noChamber.nestAttributes, noChamber.basicResources), "Needs Royal Nursery")
  }

  test("nurse capacity works with manual assignments, default staffing and returning workers") {
    val initial = reconcile(colony.copy(ants = colony.ants.copy(tasks = Seq(AntTask.SugarCollector -> (35 * u)))))
    assertEquals(initial.ants.taskWorkers(AntTask.RoyalNurse), 0L)
    val assigned = DefaultWorkerAssignment.assign(initial, AntTask.RoyalNurse, 30 * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.RoyalNurse), 5 * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.SugarCollector), 30 * u)
    val defaults = DefaultWorkerAssignment.select(assigned, Some(AntTask.RoyalNurse))
    assertEquals(defaults.ants.taskWorkers(AntTask.RoyalNurse), 5 * u)
    assertEquals(defaults.ants.idleWorkersCount, 30 * u)
    assertEquals(defaults.ants.availableWorkersCount, 35 * u)
    val returned = DefaultWorkerAssignment.normalize(defaults.copy(ants = defaults.ants.copy(workers = 40 * u)))
    assertEquals(returned.ants.taskWorkers(AntTask.RoyalNurse), 5 * u)
    assertEquals(returned.ants.idleWorkersCount, 35 * u)
    val unbuilt = DefaultWorkerAssignment.normalize(returned.copy(nestAttributes = NestAttributes()))
    assertEquals(unbuilt.ants.taskWorkers(AntTask.RoyalNurse), 0L)
  }

  test("nurses age normally and newly expired staff cannot start an egg") {
    val data = rearing().copy(ants = rearing().ants.copy(workers = 5 * u,
      tasks = Seq(AntTask.RoyalNurse -> (5 * u)), ages = ColonyAges(active = true, workers = List(AgeCohort(1L, 5L)))))
    val aged = TickUpdater.updateAllData(data, None)
    assertEquals(aged.ants.workers, 0L)
    assertEquals(aged.ants.royalBrood.get.careUnits, 0L)
    assertEquals(aged.statistics.workersLostToAging.thisRun, 5)
    val due = data.copy(world = data.world.copy(currentTick = 1L), ants = data.ants.copy(royalBrood = None))
    assertEquals(start(due).ants.royalBrood, None)
    assertEquals(start(due).ants.workers, 0L)
  }

  test("care runs after ordinary upkeep and before automatic eggs; forecasts match the actual feeding") {
    val initial = rearing(EggCare)
    val feeding = forecast(initial.ants, initial.nestAttributes, initial.upgrades, initial.exploration, initial.basicResources)
    val next = TickUpdater.updateAllData(initial, None)
    val production = Foraging.productionPerTick(30L, initial.upgrades)
    val upkeep = ScoutProgression.upkeepCount(initial.ants, initial.exploration) * antsSugarUpkeepTick
    assertEquals(next.basicResources.sugar.amount, initial.basicResources.sugar.amount + production - upkeep - feeding.cost.sugar)
    assertEquals(next.basicResources.protein.amount, initial.basicResources.protein.amount - feeding.cost.protein)
    assertEquals(next.basicResources.water.amount, initial.basicResources.water.amount - feeding.cost.water)
    assertEquals(next.ants.royalBrood.get.careUnits, EggCare + feeding.units)
    val routine = initial.copy(basicResources = initial.basicResources.copy(sugar = BasicResource(5 * 1000L, 40000 * u)),
      ants = initial.ants.copy(queenRoutine = QueenRoutine(enabled = true, targetWorkers = 100)),
      upgrades = initial.upgrades.unlock(QueensRoutine))
    val step = TickUpdater.updateAllData(routine, None)
    assertEquals(step.ants.royalBrood.get.careUnits, EggCare + 5L)
    assert(step.ants.eggsAndLarvae.isEmpty)
  }

  test("fresh production feeds brood at full storage without draining stock or counting discarded overflow") {
    val initial = rearing(EggCare).copy(basicResources = colony.basicResources.copy(sugar = BasicResource(40000 * u, 40000 * u)))
    val next = TickUpdater.updateAllData(initial, None)
    assertEquals(next.basicResources.sugar.amount, initial.basicResources.sugar.amount)
    assertEquals(next.statistics.sugarU.thisRun, 19000L) // 7 sugar/s adult upkeep + 2.5 sugar/s larval care.
    assertEquals(next.ants.royalBrood.get.careUnits, EggCare + 5L)
  }

  test("fresh production can fund partial care at zero sugar and the forecast shows it") {
    val initial = rearing(EggCare).copy(basicResources = colony.basicResources.copy(sugar = BasicResource(0L, 40000 * u)))
    val predicted = forecast(initial.ants, initial.nestAttributes, initial.upgrades, initial.exploration, initial.basicResources)
    assertEquals(predicted.units, 5L)
    val next = TickUpdater.updateAllData(initial, None)
    assertEquals(next.ants.royalBrood.get.careUnits, EggCare + predicted.units)
    assertEquals(next.basicResources.sugar.amount, 650L)
    val fewer = initial.copy(ants = initial.ants.copy(workers = 10 * u,
      tasks = Seq(AntTask.SugarCollector -> (5 * u), AntTask.RoyalNurse -> (5 * u)), ages = ColonyAges()))
    val limited = forecast(fewer.ants, fewer.nestAttributes, fewer.upgrades, fewer.exploration, fewer.basicResources)
    assertEquals(limited.units, 0L)
    assertEquals(TickUpdater.updateAllData(fewer, None).ants.royalBrood.get.careUnits, EggCare)
  }

  test("save/load retains paused and partial care, unlocks nurses and completion cannot be replayed") {
    val paused = setPaused(rearing(EggCare + 123L), true)
    val loaded = roundTrip(paused)
    assertEquals(loaded.ants.royalBrood, paused.ants.royalBrood)
    assertEquals(tick(loaded).basicResources, loaded.basicResources)
    val complete = roundTrip(tick(rearing(TotalCare - 2L)))
    assertEquals(complete.ants.daughterQueens, u)
    assertEquals(tick(complete).statistics.daughterQueensMatured.thisRun, 1)
    assertEquals(tick(complete).messages.count(_ == "A daughter queen has matured!"), 1)
    val malformed = roundTrip(rearing(TotalCare + 100L))
    assertEquals(malformed.ants.royalBrood.get.careUnits, TotalCare)
    assertEquals(tick(malformed).ants.daughterQueens, u)
    assertEquals("{}".fromJson[AntsData].toOption.get.royalBrood, None)
    assertEquals("{}".fromJson[AntsData].toOption.get.daughterQueens, 0L)
    assertEquals("{}".fromJson[StatisticsData].toOption.get.royalEggsRaised.thisRun, 0)
    val noTask = colony.copy(ants = colony.ants.copy(tasks = Seq(AntTask.SugarCollector -> (35 * u))))
    assert(roundTrip(noTask).ants.tasks.exists(_._1 == AntTask.RoyalNurse))
  }

  test("daughter queens have upkeep, preserve housing and visibility, and remain vulnerable to starvation") {
    val queen = tick(rearing(TotalCare - 5L))
    assertEquals(queen.ants.upkeepWorkersCount, colony.ants.upkeepWorkersCount + 1L)
    assert(queen.unlocks.resources.showDaughterQueens)
    assertEquals(queen.ants.populationCount, rearing().ants.populationCount)
    assertEquals(tick(ResourceVisibility.reconcile(queen)).ants.daughterQueens, u)
    val alone = queen.copy(ants = AntsData(daughterQueens = u))
    val starved = alone.copy(ants = PopulationAging.starveOne(alone))
    assertEquals(starved.ants.daughterQueens, 0L)
    assert(ResourceVisibility.reconcile(starved).unlocks.resources.showDaughterQueens)
    val worker = alone.copy(ants = alone.ants.copy(workers = u))
    assertEquals(PopulationAging.starveOne(worker).daughterQueens, u)
    assertEquals(PopulationAging.starveOne(worker).workers, 0L)
  }
}
