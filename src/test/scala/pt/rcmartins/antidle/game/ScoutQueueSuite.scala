package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.QueensRoutine
import zio.json._

class ScoutQueueSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(world = d.world.copy(currentTick = 0L, targetTick = 0L, saveSeed = 42L),
      ants = AntsData(workers = 5L * u),
      basicResources = d.basicResources.copy(sugar = BasicResource(1000L * u, 2000L * u), protein = BasicResource(100L * u, 100L * u)),
      nestAttributes = d.nestAttributes.copy(maxAnts = 50L * u, maxEggs = 10L * u,
        chambers = d.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      unlocks = d.unlocks.copy(actions = d.unlocks.actions.copy(canLayEggs = true)))
  }
  def ready(d: AllData): AllData = BroodProgression.layOrdered(d.copy(ants = d.ants.copy(layingCooldown = 0L)))
  def kind(d: AllData): String = d.ants.eggsAndLarvae.last.asInstanceOf[AntBrood.Developing].kind
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("a scout ordered during worker laying waits and is charged exactly once when the queen is ready") {
    val worker = BroodProgression.lay(colony)
    assert(worker.ants.layingCooldown > 0L)
    val queued = ScoutProgression.raise(worker)
    assertEquals(queued.ants.scoutEggOrders, Seq(0))
    assertEquals(queued.ants.eggsAndLarvae, worker.ants.eggsAndLarvae)
    assertEquals(queued.basicResources, worker.basicResources)
    assertEquals(queued.statistics, worker.statistics)
    val before = (1 until worker.ants.layingCooldown.toInt).foldLeft(queued) { (d, _) =>
      BroodProgression.layOrdered(BroodProgression.tick(d.copy(world = d.world.copy(currentTick = d.currentTick + 1L))))
    }
    assertEquals(before.ants.eggsAndLarvae.size, 1)
    val laid = BroodProgression.layOrdered(BroodProgression.tick(before))
    assertEquals(kind(laid), "scout")
    assert(laid.ants.scoutEggOrders.isEmpty)
    assertEquals(queued.basicResources.sugar.amount - laid.basicResources.sugar.amount, 50L * u)
    assertEquals(queued.basicResources.protein.amount - laid.basicResources.protein.amount, 2L * u)
    assertEquals(laid.statistics.eggsCount.thisRun, 2)
    assertEquals(BroodProgression.layOrdered(laid), laid)
  }

  test("interleaved worker batches and scouts keep manual FIFO order") {
    val workers = BroodProgression.order(colony, 2)
    val scout = ScoutProgression.raise(workers)
    val moreWorkers = BroodProgression.order(scout, 1)
    val queued = ScoutProgression.raise(moreWorkers)
    assertEquals(queued.ants.scoutEggOrders, Seq(2, 3))
    var d = queued
    val kinds = (1 to 5).map { _ => d = ready(d); kind(d) }
    assertEquals(kinds, Seq("worker", "worker", "scout", "worker", "scout"))
    assertEquals(d.ants.queuedEggs, 0)
    assertEquals(d.basicResources.sugar.amount, 870L * u)
    assertEquals(d.basicResources.protein.amount, 96L * u)
    assertEquals(d.statistics.eggsCount.thisRun, 5)
  }

  test("consecutive scouts share cooldown instead of being laid as a batch") {
    val busy = colony.copy(ants = colony.ants.copy(layingCooldown = 1L))
    val queued = ScoutProgression.raise(ScoutProgression.raise(busy))
    assertEquals(queued.ants.scoutEggOrders, Seq(0, 0))
    val first = ready(queued)
    assertEquals(first.ants.eggsAndLarvae.size, 1)
    assertEquals(first.ants.scoutEggOrders, Seq(0))
    assertEquals(BroodProgression.layOrdered(first), first)
    assertEquals(ready(first).ants.eggsAndLarvae.size, 2)
  }

  test("full brood beds accept scout orders while both castes reserve future colony housing") {
    val full = colony.copy(ants = colony.ants.copy(eggsAndLarvae = Seq.fill(13)(AntBrood.Developing(0L))),
      nestAttributes = colony.nestAttributes.copy(maxAnts = 20L * u))
    assert(ScoutProgression.canRaise(full))
    val scout = ScoutProgression.raise(full)
    assertEquals(scout.ants.scoutEggOrders, Seq(0))
    assertEquals(scout.ants.eggsAndLarvae, full.ants.eggsAndLarvae)
    val both = BroodProgression.order(scout, 10)
    assertEquals(both.ants.eggOrders, 1)
    assertEquals(both.ants.populationCount + both.ants.queuedEggs, 20)
    assertEquals(ScoutProgression.raise(both), both)
    assertEquals(BroodProgression.order(both, 1), both)
    val space = both.copy(ants = both.ants.copy(eggsAndLarvae = both.ants.eggsAndLarvae.tail))
    assertEquals(kind(ready(space)), "scout")
  }

  test("a scout waiting for supplies blocks later manual and automatic workers without losing its order") {
    val busy = colony.copy(ants = colony.ants.copy(layingCooldown = 1L))
    val queued = BroodProgression.order(ScoutProgression.raise(busy), 1)
    val hungry = queued.copy(ants = queued.ants.copy(layingCooldown = 0L,
      queenRoutine = QueenRoutine(enabled = true, targetWorkers = 20)), upgrades = queued.upgrades.unlock(QueensRoutine),
      basicResources = queued.basicResources.copy(protein = BasicResource(0L, 100L * u)))
    assertEquals(BroodProgression.layOrdered(hungry), hungry)
    assertEquals(AutomaticBrood.tick(hungry), hungry)
    assert(BroodProgression.status(hungry.ants, hungry.nestAttributes, hungry.basicResources).contains("Needs protein for scout egg"))
    assertEquals(kind(ready(hungry.giveResources(protein = 2L * u))), "scout")
    assertEquals(hungry.ants.eggOrders, 1)
  }

  test("cancelling orders clears both castes without refunding unpaid eggs or removing laid brood") {
    val worker = BroodProgression.lay(colony)
    val queued = BroodProgression.order(ScoutProgression.raise(worker), 2)
    val cancelled = BroodProgression.cancel(queued)
    assertEquals(cancelled.ants.queuedEggs, 0)
    assertEquals(cancelled.ants.eggsAndLarvae, worker.ants.eggsAndLarvae)
    assertEquals(cancelled.basicResources, queued.basicResources)
    assertEquals(cancelled.statistics, queued.statistics)
  }

  test("save reload preserves mixed queue positions and old worker-only saves remain valid") {
    val queued = ScoutProgression.raise(BroodProgression.order(colony, 2))
    val more = ScoutProgression.raise(BroodProgression.order(queued, 1))
    val restored = load(more)
    assertEquals(restored.ants.scoutEggOrders, Seq(2, 3))
    val first = load(ready(restored))
    assertEquals(first.ants.scoutEggOrders, Seq(1, 2))
    assertEquals(kind(ready(load(ready(first)))), "scout")
    assertEquals("{}".fromJson[AntsData].toOption.get.scoutEggOrders, Seq.empty)
    val old = colony.copy(ants = colony.ants.copy(eggOrders = 2))
    assertEquals(load(old).ants.eggOrders, 2)
  }

  test("nursery, standard workers and supplies are still required; queued scouts are not worker targets") {
    assert(!ScoutProgression.canRaise(colony.copy(ants = colony.ants.copy(standardWorkersUnlocked = false))))
    assert(!ScoutProgression.canRaise(colony.copy(nestAttributes = NestAttributes.initial)))
    assert(!ScoutProgression.canRaise(colony.copy(basicResources = BasicResources.initial)))
    val busy = colony.copy(ants = colony.ants.copy(layingCooldown = 1L))
    val scout = ScoutProgression.raise(busy)
    val automated = scout.copy(ants = scout.ants.copy(queenRoutine = QueenRoutine(enabled = true, targetWorkers = 6)),
      upgrades = scout.upgrades.unlock(QueensRoutine))
    assertEquals(AutomaticBrood.status(automated.ants, automated.nestAttributes, automated.basicResources.sugar.amount), "Waiting for manual egg orders")
    val laid = ready(automated)
    val next = AutomaticBrood.tick(laid.copy(ants = laid.ants.copy(layingCooldown = 0L)))
    assertEquals(kind(next), "worker")
  }
}
