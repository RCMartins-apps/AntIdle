package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.AntTask._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class DefaultWorkersSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 20 * u, scouts = 2 * u,
        tasks = Seq(SugarCollector -> 0L, Tunneler -> 0L, Explorer -> 0L)),
      upgrades = initial.upgrades.unlock(UpgradeType.DefaultWorkers),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
      basicResources = initial.basicResources.copy(sugar = BasicResource(10000 * u, 10000 * u)),
      exploration = MapFixture.explored.copy(revealedSites = 6),
    )
  }

  def pooled: AllData = DefaultWorkerAssignment.select(colony, Some(SugarCollector))

  def assertBalanced(ants: AntsData): Unit = {
    assert(ants.idleWorkersCount >= 0)
    assert(ants.tasks.forall(_._2 >= 0))
    assertEquals(ants.tasks.map(_._2).sum + ants.idleWorkersCount, ants.workers)
  }

  test("the upgrade becomes visible once exploration is unlocked, including on old saves") {
    val hidden = colony.copy(upgrades = UpgradesData.initial,
      unlocks = colony.unlocks.copy(tabs = colony.unlocks.tabs.copy(exploreTabUnlocked = false)))
    assert(!UpgradeVisibility.reconcile(hidden).upgrades(UpgradeType.DefaultWorkers).show)
    val exploring = hidden.copy(unlocks = colony.unlocks)
    assert(UpgradeVisibility.reconcile(exploring).upgrades(UpgradeType.DefaultWorkers).show)
  }

  test("selection requires the upgrade and only permits unlocked normal tasks") {
    val locked = colony.copy(upgrades = UpgradesData.initial)
    assertEquals(DefaultWorkerAssignment.select(locked, Some(SugarCollector)), locked)
    assertEquals(DefaultWorkerAssignment.select(colony, Some(Explorer)), colony)
    assertEquals(DefaultWorkerAssignment.select(colony, Some(CarcassCarrier)), colony)
    val missing = colony.copy(ants = colony.ants.copy(tasks = Seq(SugarCollector -> 0L)))
    assertEquals(DefaultWorkerAssignment.select(missing, Some(Tunneler)), missing)
    assertEquals(pooled.ants.idleWorkersCount, 0L)
    assertEquals(pooled.ants.availableWorkersCount, 20L * u)
    assertEquals(pooled.ants.taskWorkers(SugarCollector), 20L * u)
  }

  test("manual assignment borrows from the pool and removal returns to it") {
    val assigned = pooled.ants.addToTask(Tunneler, 5 * u)
    assertEquals(assigned.taskWorkers(SugarCollector), 15L * u)
    assertEquals(assigned.taskWorkers(Tunneler), 5L * u)
    assertEquals(assigned.idleWorkersCount, 0L)
    val returned = assigned.removeFromTask(Tunneler, 3 * u)
    assertEquals(returned.taskWorkers(SugarCollector), 18L * u)
    assertEquals(returned.taskWorkers(Tunneler), 2L * u)
    assertBalanced(returned)
  }

  test("insufficient pool never steals assigned workers or overbooks") {
    val ants = pooled.ants.addToTask(Tunneler, 18 * u)
    assert(!ants.hasIdleWorkersForCost(ActionCost(idleWorkers = 5 * u)))
    val borrowed = ants.addToTask(Explorer, 5 * u)
    assertEquals(borrowed.taskWorkers(Explorer), 2L * u)
    assertEquals(borrowed.taskWorkers(Tunneler), 18L * u)
    assertEquals(borrowed.taskWorkers(SugarCollector), 0L)
    assertBalanced(borrowed)
  }

  test("exploration and retrieval share the default pool and reserve workers independently") {
    val sent = ExplorationHelper.start(pooled, 5 * u, ExplorationDestination.WoodlandEdge)
    assertEquals(sent.ants.taskWorkers(SugarCollector), 15L * u)
    val carcass = Carcass(120 * u, 120 * u, 5 * u, 5 * u, 50L)
    val discovery = sent.copy(exploration = sent.exploration.copy(discoveredCarcasses = List(carcass)))
    val retrieving = CarcassProgression.retrieve(discovery, 0)
    assertEquals(retrieving.ants.taskWorkers(SugarCollector), 10L * u)
    assertEquals(retrieving.ants.taskWorkers(Explorer), 5L * u)
    assertEquals(retrieving.ants.taskWorkers(CarcassCarrier), 5L * u)
    assertBalanced(retrieving.ants)
    val finished = CarcassProgression.returnCarcasses(retrieving.copy(world = retrieving.world.copy(currentTick = 50L)))
    assertEquals(finished.ants.taskWorkers(SugarCollector), 15L * u)
    assertEquals(finished.ants.taskWorkers(Explorer), 5L * u)
    assertBalanced(finished.ants)
  }

  test("changing defaults migrates the pool without recalling away or other assigned workers") {
    val ants = pooled.ants.addToTask(Explorer, 5 * u).addToTask(Tunneler, 3 * u)
    val switched = ants.withDefaultTask(Some(Tunneler))
    assertEquals(switched.taskWorkers(SugarCollector), 0L)
    assertEquals(switched.taskWorkers(Tunneler), 15L * u)
    assertEquals(switched.taskWorkers(Explorer), 5L * u)
    val returned = switched.removeFromTask(Explorer, 5 * u)
    assertEquals(returned.taskWorkers(Tunneler), 20L * u)
    assertBalanced(returned)
  }

  test("keep idle releases the default pool and preserves explicitly assigned jobs") {
    val ants = pooled.ants.addToTask(Tunneler, 3 * u).withDefaultTask(None)
    assertEquals(ants.idleWorkersCount, 17L * u)
    assertEquals(ants.taskWorkers(Tunneler), 3L * u)
    assertEquals(ants.taskWorkers(SugarCollector), 0L)
    assertEquals(ants.availableWorkersCount, ants.idleWorkersCount)
    assertBalanced(ants)
  }

  test("newly matured workers automatically join the default task") {
    val initial = pooled.copy(ants = pooled.ants.copy(eggsAndLarvae = Seq(AntBrood.Egg(0L))),
      world = pooled.world.copy(currentTick = AntBrood.defaultTicksToGrow))
    val grown = ScoutProgression.growBrood(initial)
    assertEquals(grown.ants.workers, 21L * u)
    assertEquals(grown.ants.taskWorkers(SugarCollector), 21L * u)
    assertEquals(grown.ants.idleWorkersCount, 0L)
    assertBalanced(grown.ants)
  }

  test("upkeep counts workers once and starvation cannot leave assignments above population") {
    assertEquals(pooled.ants.upkeepWorkersCount, 22L)
    val tunneling = DefaultWorkerAssignment.select(pooled, Some(Tunneler))
    val starving = tunneling.copy(
      ants = tunneling.ants.copy(sugarCumulativeDebt = 1000 * AntDeathRiskThreshold),
      basicResources = pooled.basicResources.copy(sugar = BasicResource(0L, 1000 * u)),
    )
    val result = TickUpdater.updateAllData(starving, None)
    assertEquals(result.ants.workers, 19L * u)
    assertBalanced(result.ants)
  }

  test("current saves preserve default choice and allocation; missing ownership clears the default") {
    assertEquals("{}".fromJson[AntsData].toOption.get.defaultTask, None)
    val saved = AllDataForSave.fromAllData(pooled)
    val loaded = saved.toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.ants.defaultTask, Some(SugarCollector))
    assertEquals(loaded.ants.taskWorkers(SugarCollector), 20L * u)
    assertBalanced(loaded.ants)
    val withoutUpgrade = saved.copy(upgrades = saved.upgrades.copy(
      upgrades = saved.upgrades.upgrades.filterNot(_.name == "DefaultWorkers")))
    val withoutOwnership = withoutUpgrade.toAllData
    assert(!withoutOwnership.upgrades(UpgradeType.DefaultWorkers).unlocked)
    assertEquals(withoutOwnership.ants.defaultTask, None)
  }
}
