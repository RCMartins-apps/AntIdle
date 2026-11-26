package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.QueensRoutine

class MiddenSuite extends FunSuite {
  private val fresh = AllData.initial
  private val nursery = fresh.nestAttributes.copy(
    maxAnts = 50L * u,
    chambers = fresh.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(2))
  )

  private val stocked = fresh.basicResources.copy(
    sugar = BasicResource(2000L * u, 3000L * u),
    protein = BasicResource(100L * u, 100L * u),
    tunnelingSpace = BasicResource(1000L * u, 1000L * u)
  )

  private val egg = AntBrood.Developing(0L, "worker")
  private val larva = egg.copy(care = egg.eggEnd)

  test("the second Nursery level opens carrier jobs and refuse capacity before any purchase") {
    assertEquals(Midden.capacity(fresh.nestAttributes), 0L)
    val ready = BroodProgression.reconcile(fresh.copy(nestAttributes = nursery))
    assert(ready.ants.tasks.exists(_._1 == AntTask.RefuseCarrier))
    assertEquals(Midden.capacity(ready.nestAttributes), 100L * u)
  }

  test("only ordinary larval feeding adds refuse, and carriers clear it") {
    val hungry = stocked.copy(protein = BasicResource(0L, 100L * u))
    val start = fresh.copy(
      ants = AntsData(workers = u, eggsAndLarvae = Seq(larva)),
      basicResources = stocked,
      nestAttributes = nursery
    )
    val fed = BroodProgression.tick(start)
    val eaten = stocked.sugar.amount - fed.basicResources.sugar.amount
    assert(eaten > 0L)
    assertEquals(fed.nestAttributes.nurseryRefuse, eaten * 2L)
    assertEquals(
      BroodProgression.tick(start.copy(basicResources = hungry)).nestAttributes.nurseryRefuse,
      0L
    )
    val small = start.copy(ants = start.ants.copy(eggsAndLarvae = Seq(egg.copy(kind = "small"))))
    assertEquals(BroodProgression.tick(small).nestAttributes.nurseryRefuse, 0L)
    val carrier = start.ants.copy(tasks = Seq(AntTask.RefuseCarrier -> u))
    assertEquals(
      Midden.nextRefuse(carrier, nursery.copy(nurseryRefuse = 30L * u), 0L),
      30L * u - Midden.ClearPerSecond / TicksPerSecond
    )
  }

  test("refuse follows actual feeding with a brood priority selected") {
    val scout = egg.copy(kind = "scout")
    val ants = AntsData(
      workers = u,
      broodPriority = BroodPriority.ScoutsFirst,
      eggsAndLarvae =
        Seq(larva, scout.copy(care = scout.eggEnd), larva, scout.copy(care = scout.eggEnd))
    )
    val data = fresh.copy(ants = ants, basicResources = stocked, nestAttributes = nursery)
    val food =
      BroodProgression.forecastCost(ants, nursery, data.upgrades, data.exploration, stocked)
    val after = BroodProgression.tick(data)
    assert(food.sugar > 0L)
    assertEquals(after.nestAttributes.nurseryRefuse, food.sugar * 2L)
  }

  test("crowding closes places at 80 percent and can close at most one-third") {
    val places = BroodProgression.capacity(nursery)
    assertEquals(Midden.blockedBroodPlaces(nursery.copy(nurseryRefuse = 80L * u - 1L), places), 0)
    assertEquals(Midden.blockedBroodPlaces(nursery.copy(nurseryRefuse = 80L * u), places), 1)
    assertEquals(
      Midden.blockedBroodPlaces(nursery.copy(nurseryRefuse = 100L * u), places),
      places / 3
    )
    assertEquals(
      Midden.nextRefuse(AntsData(), nursery.copy(nurseryRefuse = Midden.BaseCapacity), u),
      Midden.BaseCapacity
    )
  }

  test("crowding holds worker and scout orders and Queen's Routine while existing brood develops") {
    val crowded = nursery.copy(nurseryRefuse = Midden.BaseCapacity)
    val ants = AntsData(
      workers = u,
      eggOrders = 1,
      eggsAndLarvae = Vector.fill(BroodProgression.usablePlaces(crowded))(egg)
    )
    val data = fresh.copy(ants = ants, basicResources = stocked, nestAttributes = crowded)
    assertEquals(BroodProgression.layOrdered(data), data)
    val scout = data.copy(ants = ants.copy(eggOrders = 0, scoutEggOrders = Seq(0)))
    assertEquals(BroodProgression.layOrdered(scout), scout)
    val routine = data.copy(
      ants =
        ants.copy(eggOrders = 0, queenRoutine = QueenRoutine(enabled = true, targetWorkers = 30)),
      upgrades = fresh.upgrades.unlock(QueensRoutine),
      unlocks = fresh.unlocks.copy(actions = fresh.unlocks.actions.copy(canLayEggs = true))
    )
    assertEquals(AutomaticBrood.tick(routine), routine)
    val ticked = BroodProgression.tick(data)
    assert(ticked.ants.eggsAndLarvae.exists(_.asInstanceOf[AntBrood.Developing].care > 0L))
    val clean = nursery.copy(nurseryRefuse = 79L * u)
    assertEquals(BroodProgression.layOrdered(ticked.copy(nestAttributes = clean)).ants.eggOrders, 0)
    assertEquals(
      BroodProgression.layOrdered(scout.copy(nestAttributes = clean)).ants.scoutEggOrders,
      Seq.empty[Int]
    )
    assert(AutomaticBrood.tick(routine.copy(nestAttributes = clean)).ants.layingCooldown > 0L)
  }

  test("sealed capacity preserves the backlog and is paid for once") {
    val crowded = nursery.copy(nurseryRefuse = Midden.BaseCapacity)
    val start = fresh.copy(basicResources = stocked, nestAttributes = crowded)
    val built = Midden.build(start)
    assertEquals(built.basicResources, stocked.spendResources(Midden.Cost))
    assertEquals(Midden.capacity(built.nestAttributes), 250L * u)
    assertEquals(Midden.stored(built.nestAttributes), Midden.BaseCapacity)
    assertEquals(
      BroodProgression.usablePlaces(built.nestAttributes),
      BroodProgression.capacity(nursery)
    )
    assertEquals(Midden.build(built), built)
  }

}
