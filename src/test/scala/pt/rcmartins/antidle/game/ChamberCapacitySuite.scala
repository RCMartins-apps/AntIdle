package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber._

class ChamberCapacitySuite extends FunSuite {
  test("capacity edits reprice all paid levels without dropping stock or applying effects twice") {
    val initial = AllData.initial
    val old = initial.copy(
      basicResources = initial.basicResources.copy(
        sugar = BasicResource(2900 * u, 3000 * u),
        tunnelingSpace = BasicResource(1900 * u, 2000 * u)
      ),
      nestAttributes = initial.nestAttributes.copy(chambers =
        AllChamberData(
          nestChamber = NestChamber(2),
          foodStorageChamber = FoodStorageChamber(3)
        )
      )
    )
    val edited = ChamberBonuses.restoreCapacity(old, None, 750 * u, 350 * u)
    assertEquals(edited.basicResources.sugar, BasicResource(2900 * u, 2500 * u))
    assertEquals(edited.basicResources.tunnelingSpace, BasicResource(1900 * u, 1700 * u))
    val ticked = TickUpdater.updateAllData(edited, None)
    assertEquals(ticked.basicResources.sugar, edited.basicResources.sugar)
    assertEquals(ticked.basicResources.tunnelingSpace, edited.basicResources.tunnelingSpace)
    val base = Some(ChamberBonuses.CapacityBase(1000 * u, 1000 * u))
    assertEquals(ChamberBonuses.restoreCapacity(edited, base, 750 * u, 350 * u), edited)
    val reloaded = AllDataForSave.fromAllData(edited).copy(chamberCapacityBase = base).toAllData
    assertEquals(reloaded.basicResources.sugar, old.basicResources.sugar)
    assertEquals(reloaded.basicResources.tunnelingSpace, old.basicResources.tunnelingSpace)
  }

  test("housing edits preserve starter and outpost capacity, adults, and queued eggs") {
    val initial = AllData.initial
    val old = initial.copy(
      ants = initial.ants.copy(smallWorkers = 40L * u, eggOrders = 2),
      nestAttributes = initial.nestAttributes.copy(
        maxAnts = 48 * u,
        chambers = AllChamberData(
          nestChamber = NestChamber(1),
          queenChamber = QueenChamber(2),
          nurseryChamber = NurseryChamber(1)
        )
      )
    )
    val reduced = ChamberBonuses.Housing(2, 1, 1)
    val olderStorageOnly = Some(ChamberBonuses.CapacityBase(1000 * u, 1000 * u))
    val edited = ChamberBonuses.restoreCapacity(old, olderStorageOnly, housingPerLevel = reduced)
    assertEquals(edited.nestAttributes.maxAnts, 34L * u)
    assertEquals(edited.ants, old.ants)
    assertEquals(BroodProgression.availableColonySlots(edited.ants, edited.nestAttributes), 0L)
    val saved = olderStorageOnly.map(_.copy(population = Some(30L * u)))
    assertEquals(ChamberBonuses.restoreCapacity(edited, saved, housingPerLevel = reduced), edited)
    assertEquals(
      ChamberBonuses.restoreCapacity(edited, saved).nestAttributes.maxAnts,
      old.nestAttributes.maxAnts
    )
  }

}
