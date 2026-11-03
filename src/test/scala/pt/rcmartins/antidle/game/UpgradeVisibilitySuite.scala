package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class UpgradeVisibilitySuite extends FunSuite {
  def initial: AllData = AllData.initial.copy(upgrades = UpgradesData.initial)

  def shown(data: AllData): Set[UpgradesData.UpgradeType] =
    data.upgrades.upgrades.collect { case (upgrade, status) if status.show => upgrade }.toSet

  test("fresh colonies reveal nothing, and discovering knowledge reveals both starting upgrades") {
    assertEquals(shown(UpgradeVisibility.reconcile(initial)), Set.empty[UpgradesData.UpgradeType])
    val points = initial.copy(basicResources = initial.basicResources.copy(knowledge = BasicResource(u, 2000 * u)))
    assertEquals(shown(UpgradeVisibility.reconcile(points)), Set[UpgradesData.UpgradeType](UnlockQueensChamber, ImproveSugarCollectorTask1))
    val spentPoints = initial.copy(unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(upgradesTabUnlocked = true)))
    assertEquals(shown(UpgradeVisibility.reconcile(spentPoints)), Set[UpgradesData.UpgradeType](UnlockQueensChamber, ImproveSugarCollectorTask1))
  }

  test("all upgrade prerequisites are evaluated independently of saved show flags") {
    val cases = List(
      (initial.upgrades.unlock(ImproveSugarCollectorTask1), ImproveSugarCollectorTask2),
      (initial.upgrades.unlock(ImproveSugarCollectorTask2), ImproveSugarCollectorTask3),
      (initial.upgrades.unlock(UnlockQueensChamber, ImproveSugarCollectorTask1), UnlockFoodStorageChamber),
      (initial.upgrades.unlock(UnlockFoodStorageChamber), UnlockExplorerTask),
      (initial.upgrades.unlock(UnlockExplorerTask), DefaultWorkers),
    )
    cases.foreach { case (upgrades, expected) =>
      assert(UpgradeVisibility.reconcile(initial.copy(upgrades = upgrades)).upgrades(expected).show, expected.name)
    }
  }

  test("food storage requires both prerequisites, and revealing upgrades does not purchase their successors") {
    List(UnlockQueensChamber, ImproveSugarCollectorTask1).foreach { only =>
      assert(!UpgradeVisibility.reconcile(initial.copy(upgrades = initial.upgrades.unlock(only))).upgrades(UnlockFoodStorageChamber).show)
    }
    val revealed = UpgradeVisibility.reconcile(initial.copy(upgrades = initial.upgrades.unlock(ImproveSugarCollectorTask1)))
    assert(revealed.upgrades(ImproveSugarCollectorTask2).show)
    assert(!revealed.upgrades(ImproveSugarCollectorTask2).unlocked)
    assert(!revealed.upgrades(ImproveSugarCollectorTask3).show)
  }

  test("previously revealed or purchased upgrades remain visible and the check is idempotent") {
    val existing = initial.copy(upgrades = initial.upgrades.show(DefaultWorkers).unlock(UnlockFoodStorageChamber))
    val first = UpgradeVisibility.reconcile(existing)
    assert(first.upgrades(DefaultWorkers).show)
    assert(first.upgrades(UnlockFoodStorageChamber).show)
    assertEquals(UpgradeVisibility.reconcile(first), first)
    assertEquals(first.copy(upgrades = existing.upgrades), existing)
  }

  test("loading stale visibility and missing new upgrade entries repairs eligibility without changing progress") {
    val savedGame = initial.copy(upgrades = initial.upgrades.unlock(UnlockExplorerTask), messages = Seq("Existing message"))
    val saved = AllDataForSave.fromAllData(savedGame)
    val old = saved.copy(upgrades = saved.upgrades.copy(upgrades = saved.upgrades.upgrades.filterNot(_.name == "DefaultWorkers")))
    val loaded = old.toAllData
    assert(loaded.upgrades(DefaultWorkers).show)
    assert(!loaded.upgrades(DefaultWorkers).unlocked)
    assert(loaded.upgrades(UnlockExplorerTask).unlocked)
    assertEquals(loaded.messages, savedGame.messages)
    assertEquals(loaded.world, savedGame.world)
    assertEquals(loaded.basicResources, savedGame.basicResources)
    assertEquals(loaded.statistics, savedGame.statistics)
  }

  test("normal gameplay checks the same rules after nest expansion") {
    val colony = initial.copy(
      ants = AntsData(workers = u),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(
        nestChamber = initial.nestAttributes.chambers.nestChamber.copy(level = 1))),
      basicResources = initial.basicResources.copy(sugar = BasicResource(100 * u, 1000 * u)),
    )
    val ticked = TickUpdater.updateAllData(colony, None)
    assertEquals(ticked.basicResources.knowledge.amount, 0L)
    assert(ticked.upgrades(UnlockQueensChamber).show)
    assert(ticked.upgrades(ImproveSugarCollectorTask1).show)
  }
}
