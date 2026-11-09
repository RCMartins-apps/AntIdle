package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class UnlockReconciliationSuite extends FunSuite {
  test("browser action and headless ticks share persistent resource and action unlocks") {
    val fresh = AllData.initial.giveResources(sugar = 5L * u)
    val action = GameTransition.afterAction(fresh)
    val tick = TickUpdater.updateAllData(fresh, None)
    assert(action.unlocks.actions.canLayEggs)
    assert(tick.unlocks.actions.canLayEggs)
    assert(GameTransition.afterAction(action.copy(basicResources = BasicResources.initial)).unlocks.actions.canLayEggs)
    assertEquals(GameTransition.afterAction(action), action)
  }
  test("research restores action gates and tasks without subscriptions or duplicate messages") {
    val initial = AllData.initial
    val researched = initial.copy(upgrades = initial.upgrades.unlock(UnlockExplorerTask).unlock(UnlockQueensChamber).unlock(UnlockFoodStorageChamber))
    val updated = UnlockUtils.reconcile(researched)
    assert(updated.unlocks.tabs.exploreTabUnlocked)
    assert(updated.unlocks.actions.canBuildQueenChamber)
    assert(updated.unlocks.actions.canBuildFoodStorageChamber)
    assert(updated.ants.tasks.exists(_._1 == AntTask.Explorer))
    assertEquals(UnlockUtils.reconcile(updated), updated)
  }
  test("population unlock includes brood and messages occur once") {
    val initial = AllData.initial
    val populated = initial.copy(ants = initial.ants.copy(smallWorkers = 4L * u,
      eggsAndLarvae = Seq(AntBrood.Developing(100L, "small"))))
    val updated = UnlockUtils.reconcile(populated)
    assert(updated.unlocks.actions.canBuildNestUpgrade)
    assertEquals(UnlockUtils.reconcile(updated), updated)
  }
}
