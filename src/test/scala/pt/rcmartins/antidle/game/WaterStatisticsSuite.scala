package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.StatisticsData._
import zio.json._

class WaterStatisticsSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(ants = AntsData(workers = 30 * u, scouts = 2 * u, tasks = Seq(AntTask.Explorer -> 0L)),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      basicResources = initial.basicResources.copy(sugar = BasicResource(10000 * u, 10000 * u)),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }

  def trip(data: AllData, destination: ExplorationDestination, workers: Long = 5 * u): AllData = {
    val sent = ExplorationHelper.start(data, workers, destination)
    assert(sent.exploration.explorationParties.nonEmpty)
    ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
  }

  test("dew study unlocks renewable gatherers without giving free water") {
    val initial = colony.copy(exploration = ExplorationData(revealedSites = 4))
    val studied = (1 to 3).foldLeft(initial) { (data, _) =>
      val sent = ExplorationMap.scout(data, DryClearing.id, 1)
      ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    }
    assert(DiscoveryProgression.DewPlants.found(studied.exploration))
    assert(studied.ants.tasks.exists(_._1 == AntTask.WaterCollector))
    assert(studied.unlocks.resources.showWater)
    assertEquals(studied.basicResources.water.amount, 0L)
    val staffed = DefaultWorkerAssignment.assign(studied, AntTask.WaterCollector, u)
    assertEquals(TickUpdater.updateAllData(staffed, None).basicResources.water.amount, 300L)
  }

  test("water forecast matches rewards, actual collection obeys capacity, visibility and saves persist") {
    val initial = colony.copy(exploration = colony.exploration.copy(surveys = Map(DryClearing.id -> 3)))
    assert(ExplorationHelper.summary(initial, 5 * u, ScavengeWater).contains("40 water"))
    assertEquals(ExplorationHelper.cost(5 * u, ScavengeWater).sugar, 125L * u)
    val result = ResourceVisibility.reconcile(trip(initial, ScavengeWater))
    assertEquals(result.basicResources.water.amount, 40L * u)
    assertEquals(result.statistics.waterU.thisRun, 40L * u)
    assert(result.unlocks.resources.showWater)
    val second = trip(result, ScavengeWater)
    assertEquals(second.basicResources.water.amount, 80L * u)
    assertEquals(second.statistics.waterU.thisRun, 80L * u)
    val full = trip(second, ScavengeWater)
    assertEquals(full.statistics.waterU.thisRun, 100L * u)
    val spent = full.spendResources(ActionCost(water = 100 * u))
    val loaded = AllDataForSave.fromAllData(spent).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.basicResources.water.amount, 0L)
    assert(loaded.unlocks.resources.showWater)
    assertEquals(loaded.statistics, spent.statistics)
    assert(!loaded.hasResources(ActionCost(water = u)))
  }

  test("resource statistics track collected gains rather than capped overflow or spending") {
    val initial = AllData.initial.copy(basicResources = AllData.initial.basicResources.copy(
      sugar = BasicResource(999 * u, 1000 * u), tunnelingSpace = BasicResource(1000 * u, 1000 * u),
      protein = BasicResource(99 * u, 100 * u), water = BasicResource(95 * u, 100 * u)))
    val gained = initial.giveResources(sugar = 10 * u, tunnelingSpace = 20 * u, knowledge = 30 * u, protein = 5 * u, water = 20 * u)
    assertEquals(gained.statistics.sugarU.thisRun, u)
    assertEquals(gained.statistics.tunnelingSpaceU.thisRun, 0L)
    assertEquals(gained.statistics.knowledgeU.thisRun, 30L * u)
    assertEquals(gained.statistics.proteinU.thisRun, u)
    assertEquals(gained.statistics.waterU.thisRun, 5L * u)
    assertEquals(gained.spendResources(ActionCost(sugar = u, protein = u, water = u)).statistics, gained.statistics)
    assertEquals(gained.giveResources(sugar = -u).statistics, gained.statistics)
  }

  test("passive sugar consumed directly at full storage counts once; wasted overflow does not") {
    val initial = AllData.initial.copy(ants = AntsData(workers = u, tasks = Seq(AntTask.SugarCollector -> u)),
      basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(1000 * u, 1000 * u)))
    val result = TickUpdater.updateAllData(initial, None)
    assertEquals(result.statistics.sugarU.thisRun, antsSugarUpkeepTick)
    val reserves = initial.copy(exploration = ExplorationData(carcasses = List(Carcass(120 * u, 120 * u, 0L, u, 50L))))
    assertEquals(TickUpdater.updateAllData(reserves, None).statistics.sugarU.thisRun, 0L)
  }

  test("launched and completed missions are separate, and replayed completions cannot award progress twice") {
    val sent = ExplorationHelper.start(colony, u, WoodlandEdge)
    assertEquals(sent.statistics.explorationsCount.thisRun, 1)
    assertEquals(sent.statistics.explorationsCompleted.thisRun, 0)
    val party = sent.exploration.explorationParties.head
    val returned = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(returned.statistics.explorationsCompleted.thisRun, 1)
    assertEquals(returned.statistics.explorationsCompleted.total, 1)
    assertEquals(ExplorationHelper.exploreBonus(returned, party), returned)
    assertEquals(AllDataForSave.fromAllData(returned).toAllData.statistics.explorationsCompleted, returned.statistics.explorationsCompleted)
  }

  test("retrievals and maturation update their counters once, with actual capped protein gains") {
    val initial = AllData.initial.copy(ants = AntsData(workers = 5 * u), basicResources = AllData.initial.basicResources.copy(protein = BasicResource(99 * u, 100 * u)),
      exploration = ExplorationData(discoveredCarcasses = List(Carcass(120 * u, 120 * u, 5 * u, 5 * u, 50L))))
    val sent = CarcassProgression.retrieve(initial, 0)
    val returned = CarcassProgression.returnCarcasses(sent.copy(world = sent.world.copy(currentTick = 50L)))
    assertEquals(returned.statistics.carcassesRetrieved.thisRun, 1)
    assertEquals(returned.statistics.proteinU.thisRun, u)
    assert(returned.messages.head.contains("+1 protein"))
    assertEquals(CarcassProgression.returnCarcasses(returned).statistics, returned.statistics)
    val brood = AllData.initial.copy(ants = AntsData(eggsAndLarvae = Seq(AntBrood.Egg(0L), AntBrood.Egg(0L), AntBrood.ScoutEgg(0L))),
      world = AllData.initial.world.copy(currentTick = 150L))
    val grown = ScoutProgression.growBrood(brood)
    assertEquals(grown.statistics.workersMatured.thisRun, 2)
    assertEquals(grown.statistics.scoutsMatured.thisRun, 1)
    assertEquals(grown.statistics.maxAliveWorkers.thisRun, 2)
    assertEquals(ScoutProgression.growBrood(grown).statistics, grown.statistics)
  }

  test("aging and starvation losses have distinct statistics, without repeated counting") {
    val initial = AllData.initial.copy(ants = AntsData(workers = u, scouts = u,
      ages = ColonyAges(active = true, workers = List(AgeCohort(300L, 1L)), scouts = List(AgeCohort(300L, 1L)))),
      world = AllData.initial.world.copy(currentTick = 300L))
    val expired = PopulationAging.tick(initial)
    assertEquals(expired.statistics.workersLostToAging.thisRun, 1)
    assertEquals(expired.statistics.scoutsLostToAging.thisRun, 1)
    assertEquals(PopulationAging.tick(expired).statistics, expired.statistics)
    val starving = AllData.initial.copy(ants = AntsData(workers = u, sugarCumulativeDebt = AntDeathRiskThreshold * 100L))
    val lost = TickUpdater.updateAllData(starving, None)
    assertEquals(lost.statistics.starvationDeaths.thisRun, 1)
    assertEquals(lost.statistics.workersLostToAging.thisRun, 0)
    assertEquals(TickUpdater.updateAllData(lost, None).statistics.starvationDeaths.thisRun, 1)
  }

  test("long-lived milestone counters saturate instead of wrapping or decreasing") {
    assertEquals(StatInt(Int.MaxValue - 1, Int.MaxValue - 1).addValue(Long.MaxValue), StatInt(Int.MaxValue, Int.MaxValue))
    assertEquals(StatLong(Long.MaxValue - 1, Long.MaxValue - 1).addValue(10L), StatLong(Long.MaxValue, Long.MaxValue))
    assertEquals(StatLong(10L, 10L).addValue(-1L), StatLong(10L, 10L))
  }
}
