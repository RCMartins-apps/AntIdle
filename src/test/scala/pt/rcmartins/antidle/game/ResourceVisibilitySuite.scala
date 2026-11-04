package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource

class ResourceVisibilitySuite extends FunSuite {
  test("fresh colonies do not reveal undiscovered resources") {
    val initial = AllData.initial
    val reconciled = ResourceVisibility.reconcile(initial)
    assertEquals(reconciled.unlocks.resources, initial.unlocks.resources)
    assert(!reconciled.exploration.proteinDiscovered)
  }

  test("all resource unlocks survive zero amounts, population loss and save reload") {
    val initial = AllData.initial
    val rich = initial.copy(
      basicResources = initial.basicResources.copy(tunnelingSpace = BasicResource(u, 1000 * u),
        knowledge = BasicResource(u, Long.MaxValue), protein = BasicResource(u, 100 * u)),
      ants = AntsData(workers = u, scouts = u, eggsAndLarvae = Seq(AntBrood.Egg(0L))),
    )
    val known = ResourceVisibility.reconcile(rich)
    val empty = known.copy(basicResources = BasicResources.initial, ants = AntsData.initial)
    val reconciled = ResourceVisibility.reconcile(empty)
    assert(reconciled.unlocks.resources.showTunnelingSpace)
    assert(reconciled.unlocks.resources.showKnowledge)
    assert(reconciled.unlocks.resources.showWorkers)
    assert(reconciled.unlocks.resources.showScouts)
    assert(reconciled.unlocks.resources.showEggs)
    assert(reconciled.exploration.proteinDiscovered)
    val loaded = AllDataForSave.fromAllData(reconciled).toAllData
    assertEquals(loaded.unlocks.resources, reconciled.unlocks.resources)
    assert(loaded.exploration.proteinDiscovered)
    assertEquals(ResourceVisibility.reconcile(loaded), loaded)
  }

  test("loading previously visible protein latches its unlock before it is spent") {
    val initial = AllData.initial
    val loaded = AllDataForSave.fromAllData(initial.copy(basicResources = initial.basicResources.copy(protein = BasicResource(u, 100 * u)))).toAllData
    assert(loaded.exploration.proteinDiscovered)
    val spent = TickUpdater.updateAllData(loaded.copy(basicResources = initial.basicResources), None)
    assert(spent.exploration.proteinDiscovered)
  }
}
