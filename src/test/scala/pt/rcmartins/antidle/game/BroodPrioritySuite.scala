package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import zio.json._

class BroodPrioritySuite extends FunSuite {
  private val worker = AntBrood.Developing(0L, "worker")
  private val scout = AntBrood.Developing(0L, "scout")

  private def colony(brood: AntBrood.Developing*): AllData = {
    val fresh = AllData.initial
    fresh
      .copy(
        ants = fresh.ants.copy(
          standardWorkersUnlocked = true,
          workers = u,
          eggsAndLarvae = brood,
          tasks = Seq(AntTask.BroodNurse -> u)
        ),
        nestAttributes = fresh.nestAttributes.copy(
          chambers = fresh.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))
        )
      )
      .giveResources(sugar = 100L * u, protein = 100L * u)
  }

  private def advances(data: AllData, priority: BroodPriority): (Long, Long) = {
    val before = data.ants.eggsAndLarvae.collect { case b: AntBrood.Developing => b }
    val after = BroodProgression
      .tick(BroodProgression.setPriority(data, priority))
      .ants
      .eggsAndLarvae
      .collect { case b: AntBrood.Developing => b }
    val (scouts, workers) = after.zip(before).partition(_._1.kind == "scout")
    def total(brood: Seq[(AntBrood.Developing, AntBrood.Developing)]): Long =
      brood.map { case (newBrood, oldBrood) => newBrood.care - oldBrood.care }.sum
    total(workers) -> total(scouts)
  }

  test(
    "priority shifts the same care budget in both directions and preserves care for the other caste"
  ) {
    val data = colony(worker, scout, worker, scout)
    val (workerCare, scoutCare) = advances(data, BroodPriority.Balanced)
    val (prioritizedWorkers, otherScouts) = advances(data, BroodPriority.WorkersFirst)
    val (otherWorkers, prioritizedScouts) = advances(data, BroodPriority.ScoutsFirst)

    assertEquals(workerCare + scoutCare, 5L * u)
    assertEquals(prioritizedWorkers + otherScouts, workerCare + scoutCare)
    assertEquals(otherWorkers + prioritizedScouts, workerCare + scoutCare)
    assert(prioritizedWorkers > workerCare && otherScouts > 0)
    assert(prioritizedScouts > scoutCare && otherWorkers > 0)
  }

  test("the priority share accounts for how many brood are in each caste") {
    val data = colony((Seq.fill(7)(worker) :+ scout): _*)
    val (workerCare, scoutCare) = advances(data, BroodPriority.WorkersFirst)
    assertEquals(workerCare, 5L * u * 14L / 15L)
    assertEquals(scoutCare, 5L * u - workerCare)
  }

  test("unused reserved care returns to the preferred caste without crossing a brood stage") {
    val nearlyHatched = scout.copy(care = scout.eggEnd - u / 10)
    val data = colony(worker, worker, worker, nearlyHatched)
    val queenOnly = data.copy(ants = data.ants.copy(workers = 0L, tasks = Nil))
    val (workerCare, scoutCare) = advances(queenOnly, BroodPriority.WorkersFirst)

    assertEquals(scoutCare, u / 10)
    assertEquals(workerCare, 3L * u - u / 10)
  }

  test("recovery is still first and a laying egg receives no development care") {
    val rescue = AntBrood.Developing(0L, EmergencyRecovery.Kind)
    val data = colony(worker, worker, scout.copy(laying = true), scout, rescue)
    val laying = data.copy(ants = data.ants.copy(layingCooldown = 50L))
    val priority = BroodProgression.setPriority(laying, BroodPriority.ScoutsFirst)
    val result = BroodProgression.tick(priority)

    assertEquals(result.ants.eggsAndLarvae.last, rescue.copy(care = 2L * u))
    assertEquals(result.ants.layingBrood, laying.ants.layingBrood)
    assertEquals(result.ants.layingCooldown, 49L)
  }

  test("larval feeding previews match the selected priority and actual feeding") {
    val food = colony(
      worker.copy(care = worker.eggEnd),
      scout.copy(care = scout.eggEnd),
      worker.copy(care = worker.eggEnd),
      scout.copy(care = scout.eggEnd)
    )
    val priority = BroodProgression.setPriority(food, BroodPriority.ScoutsFirst)
    val cost = BroodProgression.forecastCost(
      priority.ants,
      priority.nestAttributes,
      priority.upgrades,
      priority.exploration,
      priority.basicResources
    )
    val after = BroodProgression.tick(priority).basicResources

    assert(cost.sugar > 0L)
    assert(cost.protein > 0L)
    assertEquals(food.basicResources.sugar.amount - after.sugar.amount, cost.sugar)
    assertEquals(food.basicResources.protein.amount - after.protein.amount, cost.protein)
  }

  test("priority needs a nursery, persists on reload and resets with the next colony") {
    val fresh = AllData.initial
    assertEquals(BroodProgression.setPriority(fresh, BroodPriority.ScoutsFirst), fresh)
    assertEquals(
      "{}".fromJson[AntsData].toOption.get.broodPriority,
      BroodPriority.Balanced: BroodPriority
    )
    val priority = BroodProgression.setPriority(colony(worker, scout), BroodPriority.ScoutsFirst)
    val restored =
      AllDataForSave.fromAllData(priority).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(restored.ants.broodPriority, BroodPriority.ScoutsFirst: BroodPriority)
    val ready = restored.copy(ants = restored.ants.copy(daughterQueens = u))
    val next = Prestige.found(ready)
    assertEquals(next.ants.broodPriority, BroodPriority.Balanced: BroodPriority)
    assert(!BroodProgression.priorityAvailable(next.nestAttributes))
  }

}
