package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.model._

class ActivityStatusSuite extends FunSuite {
  test("statuses distinguish blocked, paused, waiting and working without relying on color") {
    assertEquals(ActivityStatus.kind("Care paused"), "paused")
    assertEquals(ActivityStatus.kind("2 eggs ordered · Needs protein for larvae"), "blocked")
    assertEquals(ActivityStatus.kind("Storage full"), "waiting")
    assertEquals(ActivityStatus.kind("No workers assigned"), "idle")
    assertEquals(ActivityStatus.kind("Collecting"), "working")
  }

  test("construction names all missing materials and later plans stay queued") {
    val cost = ActionCost(protein = 1, resin = 1)
    assertEquals(ActivityStatus.construction(cost, BasicResources(), true), "Needs protein · resin")
    assertEquals(ActivityStatus.construction(cost, BasicResources(), false), "Queued")
    assertEquals(ActivityStatus.construction(ActionCost.empty, BasicResources(), true), "Ready")
  }

}
