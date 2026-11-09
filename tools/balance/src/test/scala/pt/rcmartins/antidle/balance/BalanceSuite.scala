package pt.rcmartins.antidle.balance

import munit.FunSuite
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import zio.json._

class BalanceSuite extends FunSuite {
  val policy = Policy("test", 16, 28, 40, 5, 0, 3, 3, 2,
    List("StandardWorkers", "UnlockQueensChamber", "ImproveSugarCollectorTask1", "UnlockFoodStorageChamber", "UnlockExplorerTask"))
  def config(ticks: Long, clicks: Int = 50): Config = Config(List(42L), List(policy), ticks, 25, clicks, 2, 1, 50)
  test("seeded simulations reproduce events, resource curves and valid checkpoints") {
    var checkpoints = Vector.empty[AllData]
    val a = BalanceSimulator.run(config(1500), policy, 42L, (_, d) => checkpoints :+= d)
    val b = BalanceSimulator.run(config(1500), policy, 42L)
    assertEquals(a, b)
    assert(a.milestones.exists(_.name == "first-small-worker"))
    assertEquals(a.manualClicks, 50)
    assert(checkpoints.nonEmpty)
    checkpoints.foreach { data =>
      val loaded = AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData
      assertEquals(loaded.ants, data.ants)
      assertEquals(loaded.basicResources, data.basicResources)
      assert(data.basicResources.sugar.amount >= 0L)
    }
  }
  test("no clicks or production means no free resources or workers") {
    val run = BalanceSimulator.run(config(100, 0), policy, 42L)
    assertEquals(run.status, "budget_exhausted")
    assertEquals(run.manualClicks, 0)
    assertEquals(run.actions, 0L)
    assertEquals(run.samples.last.sugar, 0.0)
    assertEquals(run.samples.last.smallWorkers, 0)
    assertEquals(run.waits.map(w => w.end - w.start).sum, 100L)
  }
  test("opening clicks respect the configured rate on the five-tick grid") {
    val run = BalanceSimulator.run(config(10), policy, 42L)
    assertEquals(run.manualClicks, 4)
    assertEquals(run.samples.last.sugar, 4.0)
  }
  test("strategy research uses actual prices and only visible unlocks") {
    val initial = AllData.initial
    val d = UpgradeVisibility.reconcile(initial.copy(ants = initial.ants.copy(smallWorkers = 5L * u),
      basicResources = initial.basicResources.copy(sugar = initial.basicResources.sugar.copy(amount = 75L * u),
        tunnelingSpace = initial.basicResources.tunnelingSpace.copy(amount = 120L * u))))
    val choice = Strategy.decide(d, policy).choice.get
    assertEquals(choice.category, "research")
    val after = choice.apply(d)
    assertEquals(after.basicResources.sugar.amount, 0L)
    assertEquals(after.basicResources.tunnelingSpace.amount, 110L * u)
    assert(after.ants.standardWorkersUnlocked)
  }
}
