package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class EmergencyRecoverySuite extends FunSuite {
  def collapsed: AllData = {
    val d = AllData.initial
    d.copy(world = d.world.copy(currentTick = 100L, targetTick = 100L, saveSeed = 42L),
      ants = AntsData(), unlocks = d.unlocks.copy(actions = d.unlocks.actions.copy(canLayEggs = true)))
  }
  def automated: AllData = collapsed.copy(upgrades = collapsed.upgrades.unlock(QueensRoutine),
    ants = collapsed.ants.copy(queenRoutine = QueenRoutine(enabled = true, targetWorkers = 10, sugarReserve = 1000L * u)))
  def tick(d: AllData): AllData = TickUpdater.updateAllData(d, None)
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("manual recovery costs five sugar, never protein, and preserves standard research and queued orders") {
    val d = collapsed.giveResources(sugar = 5L * u).copy(ants = collapsed.ants.copy(eggOrders = 4))
    val laid = EmergencyRecovery.raise(d)
    assertEquals(laid.basicResources.sugar.amount, 0L)
    assertEquals(laid.basicResources.protein.amount, 0L)
    assertEquals(EmergencyRecovery.broodCount(laid.ants), 1)
    assert(laid.ants.standardWorkersUnlocked)
    assertEquals(laid.ants.eggOrders, 4)
    assertEquals(EmergencyRecovery.raise(laid.giveResources(sugar = 5L * u)).ants.eggsAndLarvae.size, 1)
    val adult = (1 to 80).foldLeft(laid)((d, _) => tick(d))
    assertEquals(adult.ants.smallWorkers, u)
    assertEquals(adult.ants.smallCollectors, u)
    assert(adult.basicResources.sugar.amount > 0L)
    assertEquals(adult.statistics.smallWorkersMatured.thisRun, 1)
  }

  test("recovery cannot subsidize fresh colonies, surviving workers or workers already returning") {
    val d = collapsed.giveResources(sugar = 5L * u)
    for (ants <- Seq(AntsData.initial, AntsData(workers = u), AntsData(smallWorkers = u),
      AntsData(workers = u, tasks = Seq(AntTask.Explorer -> u)),
      AntsData(eggsAndLarvae = Seq(AntBrood.Developing(0L, "small"))),
      AntsData(eggsAndLarvae = Seq(AntBrood.Egg(0L))))) {
      val current = d.copy(ants = ants)
      assertEquals(EmergencyRecovery.raise(current), current)
    }
    assertEquals(EmergencyRecovery.raise(collapsed), collapsed)
    assertEquals(EmergencyRecovery.raise(d.copy(ants = d.ants.copy(layingCooldown = 1L))).ants.eggsAndLarvae.size, 0)
  }

  test("automatic recovery gathers protected supplies once, respects the reserve and then stops") {
    val before = (1 until EmergencyRecovery.ForagingTicks.toInt).foldLeft(automated)((d, _) => tick(d))
    assertEquals(before.ants.emergencyForagingTicks, EmergencyRecovery.ForagingTicks - 1L)
    assert(before.ants.eggsAndLarvae.isEmpty)
    assertEquals(before.basicResources.sugar.amount, 0L)
    val laid = tick(before)
    assertEquals(EmergencyRecovery.broodCount(laid.ants), 1)
    assertEquals(laid.statistics.sugarU.thisRun, 5L * u)
    assertEquals(laid.basicResources.sugar.amount, 0L)
    assertEquals(laid.ants.emergencyForagingTicks, 0L)
    val recovered = (1 to 125).foldLeft(laid)((d, _) => tick(d))
    assertEquals(recovered.ants.smallWorkers, u)
    assertEquals(recovered.statistics.eggsCount.thisRun, 1)
    assertEquals(recovered.ants.queenRoutine.sugarReserve, 1000L * u)
    assertEquals(recovered.ants.emergencyForagingTicks, 0L)
    assert(recovered.basicResources.sugar.amount > 0L)
  }

  test("funded automatic recovery spends only above the reserve and records no free income") {
    val d = automated.giveResources(sugar = 25L * u).copy(ants = automated.ants.copy(
      queenRoutine = QueenRoutine(enabled = true, targetWorkers = 10, sugarReserve = 20L * u)))
    val laid = AutomaticBrood.tick(d)
    assertEquals(EmergencyRecovery.broodCount(laid.ants), 1)
    assertEquals(laid.basicResources.sugar.amount, 20L * u)
    assertEquals(laid.statistics.sugarU, d.statistics.sugarU)
    val reserved = AutomaticBrood.tick(d.copy(basicResources = d.basicResources.copy(
      sugar = d.basicResources.sugar.copy(amount = 24L * u))))
    assertEquals(reserved.ants.emergencyForagingTicks, 1L)
    assert(reserved.ants.eggsAndLarvae.isEmpty)
    assertEquals(reserved.basicResources.sugar.amount, 24L * u)
  }

  test("opt-out, zero target and locked automation never gather; configuration is not a simulation tick") {
    val partial = automated.copy(ants = automated.ants.copy(emergencyForagingTicks = 50L))
    val paused = AutomaticBrood.configure(partial, enabled = false, target = 10, reserve = 20L * u)
    assert(!paused.ants.queenRoutine.enabled)
    assertEquals(paused.ants.emergencyForagingTicks, 50L)
    for (d <- Seq(paused, automated.copy(ants = automated.ants.copy(queenRoutine = QueenRoutine(enabled = true, targetWorkers = 0))), collapsed)) {
      val next = tick(d)
      assertEquals(next.ants.emergencyForagingTicks, 0L)
      assert(next.ants.eggsAndLarvae.isEmpty)
      assertEquals(next.statistics.sugarU.thisRun, 0L)
    }
    val returned = partial.copy(ants = partial.ants.copy(workers = u))
    assertEquals(AutomaticBrood.tick(returned).ants.emergencyForagingTicks, 0L)
  }

  test("full hungry brood and housing cannot block the single queen-tended recovery place") {
    val hungry = AntBrood.Developing(100L, "worker", care = 100L * u)
    val count = BroodProgression.capacity(collapsed.nestAttributes)
    val d = automated.copy(ants = automated.ants.copy(eggsAndLarvae = Seq.fill(count)(hungry)),
      nestAttributes = automated.nestAttributes.copy(maxAnts = count.toLong * u))
    val recovered = (1 to 210).foldLeft(d)((d, _) => tick(d))
    assertEquals(recovered.ants.smallWorkers, u)
    assertEquals(recovered.ants.eggsAndLarvae, Seq.fill(count)(hungry))
    assertEquals(recovered.statistics.eggsCount.thisRun, 1)
    assert(!BroodProgression.canLay(recovered.ants, recovered.nestAttributes, 1000L * u))
    val gathering = BroodProgression.assignSmall(recovered, AntTask.Scavenger, u)
    val fed = (1 to 30).foldLeft(gathering)((d, _) => tick(d))
    assert(fed.ants.eggsAndLarvae.exists { case b: AntBrood.Developing => b.care > hungry.care; case _ => false })
  }

  test("recovery progress and emergency brood survive save reload without duplicate grants") {
    val partial = (1 to 60).foldLeft(automated)((d, _) => tick(d))
    val restored = load(partial)
    assertEquals(restored.ants.emergencyForagingTicks, partial.ants.emergencyForagingTicks)
    val laid = (1 to 65).foldLeft(restored)((d, _) => tick(d))
    assertEquals(EmergencyRecovery.broodCount(load(laid).ants), 1)
    val finalState = (1 to 100).foldLeft(load(laid))((d, _) => tick(d))
    assertEquals(finalState.statistics.eggsCount.thisRun, 1)
    assertEquals(finalState.ants.smallWorkers, u)
    assertEquals("{}".fromJson[AntsData].toOption.get.emergencyForagingTicks, 0L)
  }

  test("the founding queen remains a recovery path after starvation has killed every other ant") {
    val d = collapsed.copy(ants = collapsed.ants.copy(workers = u, scouts = u, repletes = u, daughterQueens = u))
    val empty = (1 to 4).foldLeft(d.ants)((a, _) => PopulationAging.starveOne(d.copy(ants = a)))
    assertEquals(empty.populationCount, 0)
    assert(EmergencyRecovery.needed(empty))
    assertEquals(PopulationAging.starveOne(d.copy(ants = empty)).populationCount, 0)
    assertEquals(EmergencyRecovery.broodCount(EmergencyRecovery.raise(d.copy(ants = empty).giveResources(sugar = 5L * u)).ants), 1)
  }
}
