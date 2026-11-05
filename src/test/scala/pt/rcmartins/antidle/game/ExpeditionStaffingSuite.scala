package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import zio.json._

class ExpeditionStaffingSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(ants = AntsData(workers = 100 * u, scouts = 5 * u, tasks = Seq(AntTask.Explorer -> 0L),
      ages = ColonyAges(active = true, workers = List(AgeCohort(300L, 100L)), scouts = List(AgeCohort(300L, 5L)))),
      basicResources = initial.basicResources.copy(sugar = BasicResource(10000 * u, 10000 * u)),
      exploration = ExplorationData(experience = 1500L, surveys = Map(RottenLog.id -> 4)),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }

  test("new expeditions cannot waste more than the useful 37-worker reward limit") {
    assert(ExplorationHelper.canStart(colony, 37 * u, Surroundings))
    assert(!ExplorationHelper.canStart(colony, 38 * u, Surroundings))
    assertEquals(ExplorationHelper.start(colony, 38 * u, Surroundings), colony)
    assertEquals(ExplorationProgression.selectedWorkers(100 * u, 100 * u), 37L * u)
    assertEquals(ExplorationProgression.selectedWorkers(37 * u, 5 * u), 5L * u)
    assertEquals(ExplorationProgression.selectedWorkers(5 * u, 0L), u)
  }

  test("extra scouts improve travel speed with clear sugar costs and unchanged per-trip rewards") {
    assertEquals((0 to 3).map(ExplorationProgression.duration(WoodlandEdge, _)), Seq(225L, 180L, 150L, 129L))
    val original = ExplorationHelper.start(colony, 5 * u, WoodlandEdge)
    val boosted = ExplorationHelper.start(colony, 5 * u, WoodlandEdge, 3)
    val party = boosted.exploration.explorationParties.head
    assertEquals(party.scouts, 4L * u)
    assertEquals(party.extraScouts, 3)
    assertEquals(party.targetTick, 129L)
    assertEquals(boosted.basicResources.sugar.amount, 9870L * u)
    assertEquals(ExplorationProgression.earned(party), ExplorationProgression.earned(original.exploration.explorationParties.head))
    assert(ExplorationHelper.summary(colony.exploration, 5 * u, WoodlandEdge, 3).startsWith("25.8s"))
  }

  test("scout reservations include the route minimum plus extras and prevent overbooking") {
    val boosted = ExplorationHelper.start(colony, 5 * u, WoodlandEdge, 3)
    assertEquals(ScoutProgression.available(boosted.ants, boosted.exploration), u)
    assert(!ExplorationHelper.canStart(boosted, 5 * u, WoodlandEdge, 1))
    assert(ExplorationHelper.canStart(boosted, 5 * u, WoodlandEdge, 0))
    assert(ExplorationHelper.canStart(colony, 10 * u, DryClearing, 3))
    val fewer = colony.copy(ants = colony.ants.copy(scouts = 4 * u, ages = ColonyAges()))
    assert(!ExplorationHelper.canStart(fewer, 10 * u, DryClearing, 3))
    assertEquals(ExplorationHelper.start(colony, u, Surroundings, -1), colony)
    assertEquals(ExplorationHelper.start(colony, u, Surroundings, 4), colony)
  }

  test("saved parties retain their speed and reservations, return on time, and preserve fixed age expiry") {
    val sent = ExplorationHelper.start(colony, 5 * u, WoodlandEdge, 3)
    val loaded = AllDataForSave.fromAllData(sent).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.exploration.explorationParties.head.extraScouts, 3)
    assertEquals(loaded.exploration.explorationParties.head.targetTick, 129L)
    val waiting = TickUpdater.updateAllData(loaded.copy(world = loaded.world.copy(currentTick = 127L)), None)
    assertEquals(waiting.exploration.explorationParties.size, 1)
    val returned = TickUpdater.updateAllData(waiting, None)
    assert(returned.exploration.explorationParties.isEmpty)
    assertEquals(returned.ants.idleWorkersCount, 100L * u)
    assertEquals(ScoutProgression.available(returned.ants, returned.exploration), 5L * u)
    assertEquals(returned.ants.ages.scouts, List(AgeCohort(300L, 5L)))
    assertEquals(returned.statistics.explorationsCompleted.thisRun, 1)
  }

  test("extra scouts are optional even on the first route, and supplies remain an independent bonus") {
    val party = ExplorationHelper.start(colony, u, Surroundings, 2).exploration.explorationParties.head
    assertEquals(party.scouts, 2L * u)
    assertEquals(party.targetTick, 50L)
    val supplied = colony.copy(exploration = colony.exploration.copy(suppliesLevel = 1))
    val next = ExplorationHelper.start(supplied, u, Surroundings, 2).exploration.explorationParties.head
    assertEquals(next.targetTick, party.targetTick)
    assertEquals(ExplorationProgression.earned(next), 12L)
  }
}
