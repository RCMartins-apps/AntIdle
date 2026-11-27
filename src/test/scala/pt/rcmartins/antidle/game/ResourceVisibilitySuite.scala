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
      basicResources = initial.basicResources.copy(
        tunnelingSpace = BasicResource(u, 1000 * u),
        knowledge = BasicResource(u, Long.MaxValue),
        protein = BasicResource(u, 100 * u)
      ),
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
    val loaded = AllDataForSave
      .fromAllData(
        initial
          .copy(basicResources = initial.basicResources.copy(protein = BasicResource(u, 100 * u)))
      )
      .toAllData
    assert(loaded.exploration.proteinDiscovered)
    val spent =
      TickUpdater.updateAllData(loaded.copy(basicResources = initial.basicResources))
    assert(spent.exploration.proteinDiscovered)
  }

  test("founding resets every resource and population row while preserving lifetime milestones") {
    val d = AllData.initial
    val rich = ResourceVisibility.reconcile(
      d.copy(
        basicResources = d.basicResources.copy(
          sugar = BasicResource(u, 1000 * u),
          tunnelingSpace = BasicResource(u, 1000 * u),
          knowledge = BasicResource(u, Long.MaxValue),
          protein = BasicResource(u, 100 * u),
          water = BasicResource(u, 100 * u),
          resin = BasicResource(u, 200 * u)
        ),
        ants = AntsData(
          workers = u,
          smallWorkers = u,
          scouts = u,
          repletes = u,
          daughterQueens = u,
          eggsAndLarvae = Seq(AntBrood.Egg(0L))
        ),
        statistics = StatisticsData(sugarU = StatisticsData.StatLong(1000 * u, 1000 * u))
      )
    )
    assert(rich.unlocks.resources.productIterator.forall(_ == true))
    val next = Prestige.found(rich)
    assertEquals(next.unlocks, AllData.initial.unlocks)
    assertEquals(next.exploration, ExplorationData.initial)
    assertEquals(ColonyMilestones.bonus(next.statistics), ColonyMilestones.bonus(rich.statistics))
    val loaded = AllDataForSave.fromAllData(next).toAllData
    assertEquals(loaded.unlocks.resources, Unlocks.initial.resources)
    assert(!loaded.exploration.proteinDiscovered)
    val ticked = TickUpdater.updateAllData(loaded)
    assertEquals(ticked.unlocks.resources, Unlocks.initial.resources)
    assert(!ticked.exploration.proteinDiscovered)
    val protein = loaded.giveResources(protein = u)
    val known = ResourceVisibility.reconcile(protein)
    assert(known.exploration.proteinDiscovered)
    assert(!known.unlocks.resources.showWater)
    assert(!known.unlocks.resources.showResin)
    val empty = known.copy(basicResources = BasicResources.initial)
    assert(AllDataForSave.fromAllData(empty).toAllData.exploration.proteinDiscovered)
  }

  test("old untouched founding saves lose inherited flags, active colony history stays intact") {
    val fresh =
      Prestige.found(AllData.initial.copy(ants = AntsData.initial.copy(daughterQueens = u)))
    val stale = fresh.copy(
      basicResources = BasicResources.initial,
      unlocks = Unlocks(resources =
        Unlocks.ResourceUnlocks(
          showKnowledge = true,
          showWater = true,
          showResin = true,
          showWorkers = true
        )
      ),
      exploration = fresh.exploration.copy(proteinDiscovered = true)
    )
    val repaired = AllDataForSave.fromAllData(stale).toAllData
    assertEquals(repaired.unlocks.resources, Unlocks.initial.resources)
    assert(!repaired.exploration.proteinDiscovered)
    val active =
      stale.copy(statistics = stale.statistics.copy(sugarU = StatisticsData.StatLong(u, u)))
    val loaded = AllDataForSave.fromAllData(active).toAllData
    assert(loaded.unlocks.resources.showWater)
    assert(loaded.exploration.proteinDiscovered)
    assertEquals(loaded.statistics.sugarU.thisRun, u)
  }

}
