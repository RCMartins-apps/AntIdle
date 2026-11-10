package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.RoyalNurseryPlanning

class ActionFeedbackSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(ants = AntsData(workers = 5L * u, tasks = Seq(AntTask.RoyalNurse -> u)),
      nestAttributes = d.nestAttributes.copy(maxAnts = 8L * u, chambers = d.nestAttributes.chambers.copy(
        nurseryChamber = Chamber.NurseryChamber(1), royalNurseryChamber = Chamber.RoyalNurseryChamber(1))),
      basicResources = d.basicResources.copy(sugar = BasicResource(1000L * u, 2000L * u),
        protein = BasicResource(100L * u, 100L * u), water = BasicResource(100L * u, 100L * u)))
  }

  test("all egg actions share free housing after worker and scout reservations") {
    val d = colony
    val partiallyReserved = d.ants.copy(eggOrders = 1, scoutEggOrders = Seq(1))
    assertEquals(BroodProgression.availableColonySlots(partiallyReserved, d.nestAttributes), u)
    assert(RoyalBroodProgression.canStart(partiallyReserved, d.nestAttributes, d.basicResources))
    val full = partiallyReserved.copy(eggOrders = 2)
    assertEquals(BroodProgression.availableColonySlots(full, d.nestAttributes), 0L)
    assert(!RoyalBroodProgression.canStart(full, d.nestAttributes, d.basicResources))
    assert(!ScoutProgression.canRaise(full, d.nestAttributes))
    assertEquals(BroodProgression.orderAmount(full, d.nestAttributes, 1), 0)
    val cancelled = BroodProgression.cancel(d.copy(ants = full))
    assertEquals(BroodProgression.availableColonySlots(cancelled.ants, cancelled.nestAttributes), 3L * u)
    assert(RoyalBroodProgression.canStart(cancelled))
  }

  test("reserved housing still belongs to the queued head when it is laid") {
    val d = colony.copy(ants = colony.ants.copy(scoutEggOrders = Seq(0)),
      nestAttributes = colony.nestAttributes.copy(maxAnts = 6L * u))
    assertEquals(BroodProgression.availableColonySlots(d.ants, d.nestAttributes), 0L)
    val laid = BroodProgression.layOrdered(d)
    assertEquals(laid.ants.scoutEggOrders, Seq.empty)
    assertEquals(laid.ants.populationCount, 6)
    assertEquals(BroodProgression.availableColonySlots(laid.ants, laid.nestAttributes), 0L)
  }

  test("housing-blocked orders cannot claim the queen is ready; idle cooldowns do not promise an egg") {
    val d = colony.copy(ants = colony.ants.copy(eggOrders = 1),
      nestAttributes = colony.nestAttributes.copy(maxAnts = 5L * u))
    def status(data: AllData): String = BroodProgression.status(data.ants, data.nestAttributes, data.basicResources)
    assert(status(d).contains("Colony slots full"))
    assertEquals(BroodProgression.layOrdered(d), d)
    val cooling = d.copy(ants = d.ants.copy(eggOrders = 0, layingCooldown = 10L))
    assert(status(cooling).contains("Queen ready in 2.0s"))
    assert(!status(cooling).contains("Next egg"))
    val waiting = cooling.copy(ants = cooling.ants.copy(eggOrders = 1), nestAttributes = colony.nestAttributes)
    assert(status(waiting).contains("Next egg in 2.0s"))
  }

  test("construction buttons explain full queues and recover when a plan is cancelled") {
    val d = AllData.initial.copy(upgrades = AllData.initial.upgrades.unlock(RoyalNurseryPlanning))
    val full = Construction.enqueue(d, ChamberType.Nest)
    for (kind <- Seq(ChamberType.Nest, ChamberType.Queen, ChamberType.FoodStorage, ChamberType.Nursery,
      ChamberType.Aphid, ChamberType.Replete, ChamberType.RoyalNursery)) {
      assertEquals(Construction.blockedReason(full.nestAttributes, full.upgrades, kind), Some("Build queue full"))
      assert(!Construction.canEnqueue(full.nestAttributes, full.upgrades, kind))
      val cancelled = Construction.cancel(full, 0)
      assertEquals(Construction.blockedReason(cancelled.nestAttributes, cancelled.upgrades, kind), None)
      assert(Construction.canEnqueue(cancelled.nestAttributes, cancelled.upgrades, kind))
    }
  }

  test("royal nursery feedback distinguishes an existing plan from a full queue or completed chamber") {
    val initial = AllData.initial
    val d = initial.copy(nestAttributes = initial.nestAttributes.copy(maxBuildQueue = 3),
      upgrades = initial.upgrades.unlock(RoyalNurseryPlanning))
    val queued = Construction.enqueue(d, ChamberType.RoyalNursery)
    assertEquals(Construction.blockedReason(queued.nestAttributes, queued.upgrades, ChamberType.RoyalNursery), Some("Already queued"))
    assert(!Construction.canEnqueue(queued.nestAttributes, queued.upgrades, ChamberType.RoyalNursery))
    assert(Construction.canEnqueue(queued.nestAttributes, queued.upgrades, ChamberType.Nest))
    val built = queued.nestAttributes.copy(buildQueue = Nil,
      chambers = queued.nestAttributes.chambers.copy(royalNurseryChamber = Chamber.RoyalNurseryChamber(1)))
    assertEquals(Construction.blockedReason(built, d.upgrades, ChamberType.RoyalNursery), Some("Already built"))
    assertEquals(Construction.blockedReason(initial.nestAttributes, initial.upgrades, ChamberType.RoyalNursery), Some("Requires Royal Nursery Planning"))
  }
}
