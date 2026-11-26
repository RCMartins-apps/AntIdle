package pt.rcmartins.antidle.game.balance

import munit.FunSuite
import pt.rcmartins.antidle.model.{ActionCost, UpgradesData}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class UpgradeTuningSuite extends FunSuite {
  private val catalog = UpgradeBalance.definitions
  private def encode(entries: Map[UpgradeType, UpgradeDefinition]): String =
    entries.map { case (id, definition) => id.name -> definition }.toJson

  private def change(id: UpgradeType)(f: UpgradeDefinition => UpgradeDefinition) =
    UpgradeTuning.load(encode(catalog.updated(id, f(catalog(id)))))

  test("the complete catalog round trips with all stable upgrade IDs") {
    assertEquals(UpgradeTuning.load(encode(catalog)), Right(catalog))
    assert(UpgradeTuning.load(encode(catalog - StandardWorkers)).isLeft)
    assert(UpgradeTuning.load("{}").isLeft)
  }

  test("a catalog edit redirects an owned upgrade from sugar to protein") {
    val owned = UpgradesData.initial.unlock(ImproveSugarCollectorTask1)
    val edited = change(ImproveSugarCollectorTask1)(
      _.copy(
        bonuses = List(UpgradeBonus.ProductionPercent("scrapsGatherers", 5))
      )
    ).fold(error => fail(error), identity)
    assertEquals(UpgradeBalance.production(100L, "sugar", owned, edited), 100L)
    assertEquals(UpgradeBalance.production(100L, "protein", owned, edited), 105L)
    assertEquals(UpgradeBalance.production(100L, "protein", UpgradesData.initial, edited), 100L)
  }

  test("the editor catalog provides an actual game icon for every cost field") {
    val editor = UpgradeTuning.catalog(encode(catalog)).fold(error => fail(error), identity)
    assertEquals(editor.icons.keySet, ActionCost.empty.productElementNames.toSet)
    assert(editor.icons.values.forall(_.path.nonEmpty))
  }

  test("one upgrade can grant multiple effects in catalog order") {
    val owned = UpgradesData.initial.unlock(ImproveSugarCollectorTask1)
    val edited = change(ImproveSugarCollectorTask1)(
      _.copy(bonuses =
        List(
          UpgradeBonus.ProductionPercent("scrapsGatherers", 5),
          UpgradeBonus.ProductionPercent("sugarCollectors", 20),
          UpgradeBonus.ProductionPercent("scrapsGatherers", 10)
        )
      )
    ).fold(error => fail(error), identity)
    assertEquals(UpgradeBalance.production(100L, "sugar", owned, edited), 120L)
    assertEquals(UpgradeBalance.production(100L, "protein", owned, edited), 115L)
    assert(UpgradeTuning.load(encode(edited).replace("productionPercent", "unknownEffect")).isLeft)
  }

  test("the schema exposes expedition slots and owned upgrades stack their edited counts") {
    val editor = UpgradeTuning.catalog(encode(catalog)).fold(error => fail(error), identity)
    assertEquals(editor.bonusKinds.map(_.id).toSet, Set("productionPercent", "slotCapacity"))
    val edited = change(AdditionalExpeditionSlot)(
      _.copy(bonuses = List(UpgradeBonus.SlotCapacity("expedition", 3)))
    ).fold(error => fail(error), identity)
    val owned = UpgradesData.initial.unlock(AdditionalExpeditionSlot, CoordinatedExpeditions)
    assertEquals(UpgradeBalance.addedSlots("expedition", owned, edited), 4)
    assertEquals(UpgradeBalance.addedSlots("expedition", UpgradesData.initial, edited), 0)
    assert(
      change(AdditionalExpeditionSlot)(
        _.copy(bonuses = List(UpgradeBonus.SlotCapacity("brood", 3)))
      ).isLeft
    )
    assert(
      change(AdditionalExpeditionSlot)(
        _.copy(bonuses = List(UpgradeBonus.SlotCapacity("expedition", 101)))
      ).isLeft
    )
  }

  test("invalid fields, gates, and production bonuses are rejected") {
    assert(UpgradeTuning.load(encode(catalog).replaceFirst(""""sugar"""", """"suggar"""")).isLeft)
    assert(change(StandardWorkers)(d => d.copy(cost = d.cost.copy(sugar = -1))).isLeft)
    assert(
      change(StandardWorkers)(d =>
        d.copy(requirements = d.requirements.copy(minimumSmallWorkers = -2))
      ).isLeft
    )
    assert(
      change(StandardWorkers)(d =>
        d.copy(requirements = d.requirements.copy(discovery = Some("missing")))
      ).isLeft
    )
    assert(
      change(StandardWorkers)(
        _.copy(bonuses = List(UpgradeBonus.ProductionPercent("water", 5)))
      ).isLeft
    )
    assert(
      change(StandardWorkers)(
        _.copy(bonuses = List(UpgradeBonus.ProductionPercent("scrapsGatherers", 101)))
      ).isLeft
    )
  }

  test("purchase dependency cycles are rejected") {
    val result = change(UnlockQueensChamber)(d =>
      d.copy(
        requirements = d.requirements.copy(upgrades = List(UnlockFoodStorageChamber))
      )
    )
    assert(result.left.toOption.exists(_.contains("Dependency cycle")))
  }

}
