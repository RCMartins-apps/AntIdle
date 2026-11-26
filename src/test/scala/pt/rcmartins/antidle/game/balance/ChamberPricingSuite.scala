package pt.rcmartins.antidle.game.balance

import munit.FunSuite
import zio.json._

class ChamberPricingSuite extends FunSuite {
  private def withBonuses(chamber: String, bonuses: List[UpgradeBonus]): String =
    List("Nest", "Queen", "FoodStorage", "Nursery", "Aphid", "Replete", "RoyalNursery")
      .map { id =>
        val effects = if (id == chamber) bonuses else Nil
        s""" "$id": {"costs": {}, "bonuses": ${effects.toJson}} """
      }
      .mkString("{", ",", "}")

  test("a chamber can define different capacities but cannot repeat a capacity target") {
    val sugar = UpgradeBonus.StorageCapacity("sugar", 7500000L)
    val protein = UpgradeBonus.StorageCapacity("protein", 500000L)
    assertEquals(
      ChamberPricing.validate(withBonuses("FoodStorage", List(sugar, protein))),
      Right(())
    )
    assert(ChamberPricing.validate(withBonuses("FoodStorage", List(sugar, sugar))).isLeft)
    assert(ChamberPricing.validate(withBonuses("Nest", List(protein))).isLeft)
  }

  test("slot bonuses use bounded whole counts on the supported chamber") {
    val nurses = UpgradeBonus.SlotCapacity("broodNurse", 8)
    assertEquals(ChamberPricing.validate(withBonuses("Nursery", List(nurses))), Right(()))
    assert(ChamberPricing.validate(withBonuses("Queen", List(nurses))).isLeft)
    assert(ChamberPricing.validate(withBonuses("Nursery", List(nurses.copy(count = -1)))).isLeft)
    assert(ChamberPricing.validate(withBonuses("Nursery", List(nurses.copy(count = 1001)))).isLeft)
  }

}
