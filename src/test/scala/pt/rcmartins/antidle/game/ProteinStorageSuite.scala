package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.{ChamberType, FoodStorageChamber}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class ProteinStorageSuite extends FunSuite {
  def colony(level: Int): AllData = {
    val initial = AllData.initial
    initial.copy(nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(foodStorageChamber = FoodStorageChamber(level))))
  }

  test("protein capacity is unchanged until research, then includes every existing chamber level") {
    Seq(0, 1, 3, 10).foreach { level =>
      val original = colony(level)
      assertEquals(ChamberBonuses.reconcileProteinCapacity(original), original)
      val researched = UpgradeVisibility.reconcile(original.copy(upgrades = original.upgrades.unlock(ProteinStorage)))
      assertEquals(researched.basicResources.protein.maxAmount, (100L + level * 100L) * u)
      assertEquals(researched.basicResources.protein.amount, original.basicResources.protein.amount)
      assertEquals(UpgradeVisibility.reconcile(researched), researched)
    }
  }

  test("future food storage levels add both advertised capacities without duplicating existing bonuses") {
    val original = colony(3)
    val researched = UpgradeVisibility.reconcile(original.copy(upgrades = original.upgrades.unlock(ProteinStorage)))
    val bonus = ChamberBonuses.forChamber(ChamberType.FoodStorage, researched.upgrades)
    assertEquals(bonus.maxProtein, 100L * u)
    assertEquals(ChamberBonuses.forChamber(ChamberType.FoodStorage).maxProtein, 0L)
    val built = ChamberBonuses.completeUpgrade(researched, ChamberType.FoodStorage)
    assertEquals(built.basicResources.protein.maxAmount - researched.basicResources.protein.maxAmount, bonus.maxProtein)
    assertEquals(built.basicResources.sugar.maxAmount - researched.basicResources.sugar.maxAmount, bonus.maxSugar)
    val loaded = AllDataForSave.fromAllData(built).toAllData
    assertEquals(loaded.basicResources.protein, built.basicResources.protein)
    assertEquals(UpgradeVisibility.reconcile(loaded).basicResources.protein, built.basicResources.protein)
  }

  test("research is hidden until exploration, protein discovery and food storage unlock") {
    val initial = colony(1)
    val ready = initial.copy(upgrades = initial.upgrades.unlock(UnlockFoodStorageChamber),
      exploration = initial.exploration.copy(proteinDiscovered = true),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
    assert(ProteinStorage.shouldShow(ready))
    val loaded = AllDataForSave.fromAllData(ready.copy(exploration = ExplorationData(),
      basicResources = ready.basicResources.copy(protein = BasicResources.BasicResource(30 * u, 100 * u)))).toAllData
    assert(loaded.upgrades(ProteinStorage).show)
    assert(!ProteinStorage.shouldShow(initial))
    assert(!ProteinStorage.shouldShow(ready.copy(exploration = ExplorationData())))
    assert(!ProteinStorage.shouldShow(ready.copy(upgrades = UpgradesData.initial)))
    assert(!ProteinStorage.shouldShow(ready.copy(unlocks = Unlocks.initial)))
  }
}
