package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.{AphidChamber, ChamberType}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class AphidChamberSuite extends FunSuite {
  def colony(level: Int = 3): AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 20 * u, tasks = Seq(AntTask.SugarCollector -> 0L, AntTask.AphidTender -> 0L, AntTask.Explorer -> 0L)),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(aphidChamber = AphidChamber(level))),
      exploration = MapFixture.explored,
      upgrades = initial.upgrades.unlock(AphidTending, DefaultWorkers),
      basicResources = initial.basicResources.copy(sugar = BasicResource(500 * u, 1000 * u), protein = BasicResource(10 * u, 100 * u)),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  test("each chamber level adds exactly one worker slot and the advertised bonus matches") {
    val empty = colony(0)
    assertEquals(AphidHusbandry.capacity(empty.nestAttributes), 0L)
    val built = ChamberBonuses.completeUpgrade(empty, ChamberType.Aphid)
    assertEquals(AphidHusbandry.capacity(built.nestAttributes), u)
    assertEquals(ChamberBonuses.forChamber(ChamberType.Aphid).aphidSlots, 1)
    assertEquals(built.basicResources, empty.basicResources)
    assertEquals(AphidHusbandry.capacity(ChamberBonuses.completeUpgrade(built, ChamberType.Aphid).nestAttributes), 2L * u)
  }

  test("manual batches stop at capacity without removing excess workers from their default task") {
    val ready = DefaultWorkerAssignment.select(colony(), Some(AntTask.SugarCollector))
    val assigned = DefaultWorkerAssignment.assign(ready, AntTask.AphidTender, 250 * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.AphidTender), 3L * u)
    assertEquals(assigned.ants.taskWorkers(AntTask.SugarCollector), 17L * u)
    assertEquals(DefaultWorkerAssignment.assign(assigned, AntTask.AphidTender, u), assigned)
    assertEquals(DefaultWorkerAssignment.assign(colony(0), AntTask.AphidTender, u).ants.taskWorkers(AntTask.AphidTender), 0L)
  }

  test("default aphid work leaves overflow idle and refills only chamber slots on returns and growth") {
    val selected = DefaultWorkerAssignment.select(colony(), Some(AntTask.AphidTender))
    assertEquals(selected.ants.taskWorkers(AntTask.AphidTender), 3L * u)
    assertEquals(selected.ants.idleWorkersCount, 17L * u)
    assertEquals(selected.ants.availableWorkersLabel, "available workers")
    val sent = ExplorationHelper.start(selected, 20 * u, ExplorationDestination.WoodlandEdge)
    assertEquals(sent.ants.taskWorkers(AntTask.AphidTender), 0L)
    val returned = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(returned.ants.taskWorkers(AntTask.AphidTender), 3L * u)
    assertEquals(returned.ants.idleWorkersCount, 17L * u)
    val growing = selected.copy(ants = selected.ants.copy(eggsAndLarvae = Seq(AntBrood.Egg(-100L))))
    val grown = ScoutProgression.growBrood(growing)
    assertEquals(grown.ants.taskWorkers(AntTask.AphidTender), 3L * u)
    assertEquals(grown.ants.idleWorkersCount, 18L * u)
    val expanded = DefaultWorkerAssignment.normalize(ChamberBonuses.completeUpgrade(selected, ChamberType.Aphid))
    assertEquals(expanded.ants.taskWorkers(AntTask.AphidTender), 4L * u)
    assertEquals(expanded.ants.idleWorkersCount, 16L * u)
  }

  test("fed tenders produce constant sugar and consume minor protein in actual ticks") {
    val initial = DefaultWorkerAssignment.assign(colony(), AntTask.AphidTender, 3 * u)
    val next = TickUpdater.updateAllData(initial, None)
    assertEquals(next.basicResources.protein.amount - initial.basicResources.protein.amount, -60L)
    assertEquals(next.basicResources.sugar.amount - initial.basicResources.sugar.amount, 4800L - 20L * antsSugarUpkeepTick)
    assertEquals(AphidHusbandry.productionPerTick(initial.ants, initial.nestAttributes, initial.upgrades, 100 * u), 4800L)
    assertEquals(AphidHusbandry.productionPerTick(initial.ants, initial.nestAttributes, UpgradesData.initial, 100 * u), 0L)
  }

  test("empty protein pauses tending and a partial final feed produces proportional sugar without debt") {
    val fed = DefaultWorkerAssignment.assign(colony(), AntTask.AphidTender, 3 * u)
    val partial = fed.copy(basicResources = fed.basicResources.copy(protein = BasicResource(10L, 100 * u)))
    val next = TickUpdater.updateAllData(partial, None)
    assertEquals(next.basicResources.protein.amount, 0L)
    assertEquals(next.basicResources.sugar.amount - partial.basicResources.sugar.amount, 800L - 20L * antsSugarUpkeepTick)
    val paused = TickUpdater.updateAllData(next, None)
    assertEquals(paused.basicResources.protein.amount, 0L)
    assertEquals(paused.basicResources.sugar.amount - next.basicResources.sugar.amount, -20L * antsSugarUpkeepTick)
  }

  test("chamber capacity also limits production from over-assigned imported state and survives saves") {
    val initial = colony().copy(ants = colony().ants.copy(tasks = Seq(AntTask.AphidTender -> (20 * u)), defaultTask = Some(AntTask.AphidTender)))
    assertEquals(AphidHusbandry.productionPerTick(initial.ants, initial.nestAttributes, initial.upgrades, 100 * u), 4800L)
    val loaded = AllDataForSave.fromAllData(initial).toAllData
    assertEquals(loaded.nestAttributes.chambers.aphidChamber.level, 3)
    assertEquals(loaded.ants.taskWorkers(AntTask.AphidTender), 3L * u)
    assertEquals(loaded.ants.idleWorkersCount, 17L * u)
  }
}
