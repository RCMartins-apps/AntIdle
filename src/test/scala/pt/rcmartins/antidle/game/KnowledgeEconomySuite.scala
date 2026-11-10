package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class KnowledgeEconomySuite extends FunSuite {
  test("knowledge has no storage cap and no full indicator") {
    val earned = AllData.initial.giveResources(knowledge = 100000 * u)
    assertEquals(earned.basicResources.knowledge.amount, 100000L * u)
    assertEquals(earned.basicResources.knowledge.maxAmount, Long.MaxValue)
    assert(!earned.basicResources.knowledge.isLimited)
    assert(!earned.basicResources.knowledge.atCapacity)
  }

  test("all pre-exploration unlocks cost sugar only and advanced upgrades use knowledge") {
    List(UnlockQueensChamber, ImproveSugarCollectorTask1, UnlockFoodStorageChamber, UnlockExplorerTask).foreach { upgrade =>
      assert(upgrade.cost.sugar > 0)
      assertEquals(upgrade.cost.knowledge, 0L)
      assertEquals(upgrade.cost.protein, 0L)
    }
    List(ImproveSugarCollectorTask2, ImproveSugarCollectorTask3, DefaultWorkers).foreach { upgrade =>
      assert(upgrade.cost.knowledge > 0)
      assert(upgrade.cost.sugar > 0)
    }
  }

  test("a zero-knowledge colony can reveal and purchase the full exploration prerequisite chain") {
    var colony = AllData.initial.copy(
      nestAttributes = ChamberBonuses.completeUpgrade(AllData.initial, ChamberType.Nest).nestAttributes,
      basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(5000 * u, 10000 * u)),
    )
    List(UnlockQueensChamber, ImproveSugarCollectorTask1, UnlockFoodStorageChamber, UnlockExplorerTask).foreach { upgrade =>
      colony = UpgradeVisibility.reconcile(colony)
      assert(colony.upgrades(upgrade).show, upgrade.name)
      assert(colony.hasResources(upgrade.cost), upgrade.name)
      colony = colony.spendResources(upgrade.cost).copy(upgrades = colony.upgrades.unlock(upgrade))
    }
    assertEquals(colony.basicResources.knowledge.amount, 0L)
    assert(colony.upgrades(UnlockExplorerTask).unlocked)
  }

  test("Nest levels add 500 tunneling capacity and never add knowledge or a knowledge cap") {
    val initial = AllData.initial
    val upgraded = ChamberBonuses.completeUpgrade(initial, ChamberType.Nest)
    assertEquals(upgraded.basicResources.tunnelingSpace.maxAmount, initial.basicResources.tunnelingSpace.maxAmount + 500L * u)
    assertEquals(upgraded.basicResources.knowledge, initial.basicResources.knowledge)
    assertEquals(ChamberBonuses.forChamber(ChamberType.Nest).maxTunnelingSpace, 500L * u)
    val ticked = TickUpdater.updateAllData(upgraded, None)
    assertEquals(ticked.basicResources.knowledge.amount, 0L)
  }

  test("Nest expansion costs stay within the tunneling capacity of the existing Nest") {
    ((0 to 100) :+ 1000).foreach { level =>
      val capacity = 1000L * u + level.toLong * Constants.NestUpgradeBonusMaxTunnelingSpace
      assert(Constants.getChamberTunnelingSpaceNeeded(ChamberType.Nest, level) <= capacity, s"level $level")
    }
  }

  test("knowledge belongs to finite scout surveys rather than worker destinations") {
    assertEquals(ExplorationMap.sites.size, 6)
    assert(ExplorationMap.sites.forall(_.knowledge > 0L))
    assert(ExplorationMap.sites.forall(site => ExplorationMap.knowledgeRemaining(ExplorationData(), site.id) == site.knowledge))
  }

  test("version six saves round-trip, and earlier versions are rejected without migration") {
    val game = AllData.initial.copy(basicResources = AllData.initial.basicResources.copy(
      knowledge = BasicResource(5000 * u, Long.MaxValue)))
    val saved = AllDataForSave.fromAllData(game)
    val loaded = pt.rcmartins.antidle.game.saves.SaveLoad.loadString(saved.toJson).get
    assertEquals(saved.version, AllData.CurrentVersion)
    assertEquals(loaded.basicResources, game.basicResources)
    assertEquals(pt.rcmartins.antidle.game.saves.SaveLoad.loadString(saved.copy(version = 6).toJson), None)
  }
}
