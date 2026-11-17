package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.model.{ActionCost, BasicResources}
import pt.rcmartins.antidle.model.BasicResources.BasicResource

class ConstructionWaitSuite extends FunSuite {
  private val resources = BasicResources.initial.copy(sugar = BasicResource(0, 100))
  test("earlier costs count without treating cumulative cost as a storage requirement") {
    assertEquals(ConstructionWait.ticks(Seq.fill(2)(ActionCost(sugar = 80)), resources, ActionCost(sugar = 10)), Seq(8L, 16L))
  }
  test("ready entries are zero; no income and individual capacity blockers propagate") {
    assertEquals(ConstructionWait.ticks(Seq(ActionCost(), ActionCost(sugar = 1)), resources, ActionCost()), Seq(0L, -1L))
    assertEquals(ConstructionWait.ticks(Seq(ActionCost(sugar = 101), ActionCost()), resources, ActionCost(sugar = 10)), Seq(-1L, -1L))
  }
  test("water and resin production contribute to the slowest resource") {
    assertEquals(ConstructionWait.ticks(Seq(ActionCost(water = 20, resin = 30)), resources,
      ActionCost(water = 5, resin = 3)), Seq(10L))
  }
  test("consumption can make a previously sufficient resource impossible") {
    val r = resources.copy(protein = BasicResource(5, 100))
    assertEquals(ConstructionWait.ticks(Seq(ActionCost(sugar = 80, protein = 1)), r,
      ActionCost(sugar = 10, protein = -1)), Seq(-1L))
  }
}
