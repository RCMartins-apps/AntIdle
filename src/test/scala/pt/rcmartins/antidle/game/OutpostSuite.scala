package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class OutpostSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 20 * u, scouts = 2 * u, repletes = u,
        tasks = Seq(AntTask.SugarCollector -> (20 * u), AntTask.Explorer -> 0L), defaultTask = Some(AntTask.SugarCollector)),
      basicResources = initial.basicResources.copy(sugar = BasicResource(6000 * u, 10000 * u),
        protein = BasicResource(80 * u, 100 * u), water = BasicResource(180 * u, 200 * u),
        tunnelingSpace = BasicResource(1000 * u, 1500 * u), knowledge = BasicResource(500 * u, Long.MaxValue)),
      exploration = MapFixture.explored.copy(revealedSites = 6, surveys = Map("dry-clearing" -> 3)),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(
        nurseryChamber = Chamber.NurseryChamber(1), repleteChamber = Chamber.RepleteChamber(1))),
      upgrades = initial.upgrades.unlock(DefaultWorkers, RepleteStorage),
    )
  }

  def staffed: AllData = DefaultWorkerAssignment.assign(OutpostProgression.establish(colony), AntTask.SeedForager, 5 * u)

  test("outpost requires discovery, level and every resource in the engine") {
    assert(OutpostProgression.canEstablish(colony))
    val hidden = colony.copy(exploration = colony.exploration.copy(surveys = Map("dry-clearing" -> 2)))
    val early = colony.copy(exploration = colony.exploration.copy(surveys = Map("dry-clearing" -> 1)))
    assert(!OutpostProgression.visible(hidden.exploration))
    assert(!OutpostProgression.visible(early.exploration))
    val base = colony.basicResources
    val shortages = Seq(
      base.copy(sugar = BasicResource(2999 * u, 10000 * u)),
      base.copy(protein = BasicResource(39 * u, 100 * u)),
      base.copy(water = BasicResource(149 * u, 200 * u)),
      base.copy(knowledge = BasicResource(299 * u, Long.MaxValue)),
      base.copy(tunnelingSpace = BasicResource(749 * u, 1500 * u)))
    (Seq(hidden, early) ++ shortages.map(r => colony.copy(basicResources = r))).foreach { data =>
      assertEquals(OutpostProgression.establish(data), data)
    }
  }

  test("establishment spends once, adds housing once and unlocks five forager slots") {
    val initial = colony
    val built = OutpostProgression.establish(initial)
    assert(built.nestAttributes.dryClearingOutpost)
    assertEquals(built.basicResources, initial.basicResources.spendResources(OutpostProgression.cost))
    assertEquals(built.nestAttributes.maxAnts, initial.nestAttributes.maxAnts + 50 * u)
    assertEquals(built.ants.populationCount, initial.ants.populationCount)
    assertEquals(built.ants.upkeepWorkersCount, initial.ants.upkeepWorkersCount)
    assertEquals(OutpostProgression.capacity(built.nestAttributes), 5L * u)
    assert(built.ants.defaultTaskChoices.contains(AntTask.SeedForager))
    assertEquals(OutpostProgression.establish(built), built)
    assertEquals(OutpostProgression.reconcile(built), built)
    assertEquals(built.messages.count(_.startsWith("Dry Clearing Outpost established!")), 1)
  }

  test("manual batches and default workers obey slots without creating workers") {
    val built = OutpostProgression.establish(colony)
    val assigned = DefaultWorkerAssignment.assign(built, AntTask.SeedForager, 250 * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.SugarCollector), 15L * u)
    assertEquals(assigned.ants.idleWorkersCount, 0L)
    val pooled = DefaultWorkerAssignment.select(built, Some(AntTask.SeedForager))
    assertEquals(pooled.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    assertEquals(pooled.ants.idleWorkersCount, 15L * u)
    val sent = ExplorationHelper.start(pooled, 18 * u, ExplorationDestination.ScavengeWater)
    assertEquals(sent.ants.taskWorkers(AntTask.SeedForager), 2L * u)
    val returned = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(returned.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    assertEquals(returned.ants.workers, 20L * u)
    assertEquals(returned.ants.idleWorkersCount, 15L * u)
  }

  test("real ticks collect protein, drink water and count actual gathering") {
    val initial = staffed
    val next = TickUpdater.updateAllData(initial, None)
    assertEquals(next.basicResources.protein.amount - initial.basicResources.protein.amount, 400L)
    assertEquals(initial.basicResources.water.amount - next.basicResources.water.amount, 200L)
    assertEquals(next.statistics.proteinU.thisRun - initial.statistics.proteinU.thisRun, 400L)
    assertEquals(next.statistics.waterU.thisRun, initial.statistics.waterU.thisRun)
    assertEquals(next.ants.upkeepWorkersCount, initial.ants.upkeepWorkersCount)
    assertEquals(next.ants.taskWorkers(AntTask.SeedForager), 5L * u)
  }

  test("partial and absent water never produce debt, and replenishment resumes foraging") {
    val dry = staffed.copy(basicResources = staffed.basicResources.copy(water = BasicResource(1L, 200 * u)))
    val lastDrop = TickUpdater.updateAllData(dry, None)
    assertEquals(lastDrop.basicResources.water.amount, 0L)
    assertEquals(lastDrop.basicResources.protein.amount, dry.basicResources.protein.amount + 2L)
    val paused = TickUpdater.updateAllData(lastDrop, None)
    assertEquals(paused.basicResources.protein.amount, lastDrop.basicResources.protein.amount)
    assertEquals(paused.nestAttributes.maxAnts, dry.nestAttributes.maxAnts)
    assertEquals(paused.ants.populationCount, dry.ants.populationCount)
    assertEquals(paused.messages, lastDrop.messages)
    val resumed = TickUpdater.updateAllData(paused.giveResources(water = u), None)
    assertEquals(resumed.basicResources.protein.amount, paused.basicResources.protein.amount + 400L)
  }

  test("full protein pauses water use and last storage units charge only needed water") {
    val full = staffed.copy(basicResources = staffed.basicResources.copy(protein = BasicResource(100 * u, 100 * u)))
    val paused = TickUpdater.updateAllData(full, None)
    assertEquals(paused.basicResources.water, full.basicResources.water)
    assertEquals(paused.statistics.proteinU, full.statistics.proteinU)
    val almost = full.copy(basicResources = full.basicResources.copy(protein = BasicResource(100 * u - 3L, 100 * u)))
    val filled = TickUpdater.updateAllData(almost, None)
    assertEquals(filled.basicResources.protein.amount, 100L * u)
    assertEquals(almost.basicResources.water.amount - filled.basicResources.water.amount, 2L)
    assertEquals(filled.statistics.proteinU.thisRun - almost.statistics.proteinU.thisRun, 3L)
  }

  test("aphid feeding frees real storage room before the same tick's harvest") {
    val initial = staffed
    val feeding = initial.copy(ants = initial.ants.unlockTask(AntTask.AphidTender).addToTask(AntTask.AphidTender, u),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(1))),
      basicResources = initial.basicResources.copy(protein = BasicResource(100 * u, 100 * u)),
      upgrades = initial.upgrades.unlock(AphidTending))
    val next = TickUpdater.updateAllData(feeding, None)
    assertEquals(next.basicResources.protein.amount, 100L * u)
    assertEquals(feeding.basicResources.water.amount - next.basicResources.water.amount, 10L)
    assertEquals(next.statistics.proteinU.thisRun - feeding.statistics.proteinU.thisRun, 20L)
  }

  test("load restores outpost tasks and enforces staffing without repeating housing bonuses") {
    val initial = DefaultWorkerAssignment.select(OutpostProgression.establish(colony), Some(AntTask.SeedForager))
    val saved = AllDataForSave.fromAllData(initial)
    val loaded = saved.toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assert(loaded.nestAttributes.dryClearingOutpost)
    assertEquals(loaded.nestAttributes.maxAnts, initial.nestAttributes.maxAnts)
    assertEquals(loaded.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    val missingTask = saved.copy(ants = initial.ants.copy(tasks = Seq(AntTask.SugarCollector -> 0L)))
    assertEquals(missingTask.toAllData.ants.taskWorkers(AntTask.SeedForager), 5L * u)
    assert(!"{}".fromJson[NestAttributes].toOption.get.dryClearingOutpost)
    assertEquals(OutpostProgression.establish(loaded), loaded)
  }

  test("unestablished or over-assigned imported state cannot bypass production limits") {
    val invalid = colony.copy(ants = colony.ants.copy(tasks = Seq(AntTask.SeedForager -> (20 * u))))
    assertEquals(OutpostProgression.harvestPerTick(invalid.ants, invalid.nestAttributes, invalid.upgrades, invalid.basicResources), OutpostProgression.Harvest())
    assertEquals(DefaultWorkerAssignment.normalize(invalid).ants.taskWorkers(AntTask.SeedForager), 0L)
    val built = invalid.copy(nestAttributes = invalid.nestAttributes.copy(dryClearingOutpost = true))
    assertEquals(OutpostProgression.harvestPerTick(built.ants, built.nestAttributes, built.upgrades, built.basicResources).protein, 400L)
  }
}
