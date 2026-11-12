package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class CollectionTrailsSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(ants = AntsData(workers = 100 * u, scouts = 10 * u, lineage = Lineage(1, 0L, true),
      tasks = Seq(AntTask.SugarCollector -> (100 * u)), defaultTask = Some(AntTask.SugarCollector)),
      upgrades = d.upgrades.unlock(DefaultWorkers),
      nestAttributes = d.nestAttributes.copy(maxAnts = 1000 * u),
      basicResources = d.basicResources.copy(sugar = BasicResource(10000 * u, 100000 * u),
        protein = BasicResource(0L, 100000 * u), water = BasicResource(0L, 100000 * u), resin = BasicResource(0L, 100000 * u)),
      exploration = MapFixture.explored,
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }
  def assign(d: AllData, route: ExplorationDestination, count: Int): AllData =
    (0 until count / 5).foldLeft(d)((data, _) => PheromoneTrails.adjust(data, route.id, 5 * u))
  def plans(d: AllData): List[PheromoneTrails.Harvest] =
    PheromoneTrails.forecast(d.ants, d.exploration, d.upgrades, d.basicResources)
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("guides round up per trail and assignments are groups of five up to fifty") {
    assertEquals(List(5L, 10L, 15L, 25L, 50L).map(n => PheromoneTrails.requiredScouts(n * u)), List(1L, 1L, 2L, 3L, 5L).map(_ * u))
    val d = colony
    assertEquals(PheromoneTrails.adjust(d, NectarMeadow.id, u), d)
    val full = assign(d, NectarMeadow, 50)
    assertEquals(PheromoneTrails.target(full.exploration, NectarMeadow.id), 50 * u)
    assertEquals(PheromoneTrails.workers(full.ants), 50 * u)
    assertEquals(PheromoneTrails.guides(full.ants, full.exploration), 5 * u)
    assertEquals(PheromoneTrails.adjust(full, NectarMeadow.id, 5 * u), full)
    val short = colony.copy(ants = colony.ants.copy(scouts = 2 * u))
    val twenty = assign(short, NectarMeadow, 20)
    assertEquals(PheromoneTrails.adjust(twenty, NectarMeadow.id, 5 * u), twenty)
    val recalled = PheromoneTrails.recall(full, NectarMeadow.id)
    assertEquals(recalled.ants.taskWorkers(AntTask.SugarCollector), 100 * u)
    assertEquals(recalled.basicResources, d.basicResources)
    assertEquals(ScoutProgression.available(recalled.ants, recalled.exploration), 10 * u)
  }
  test("only safe studied routes automate; late Understanding purchase works immediately") {
    val d = colony.copy(ants = colony.ants.copy(lineage = Lineage(1, 1L, false)))
    assertEquals(assign(d, NectarMeadow, 5), d)
    val purchased = Prestige.purchase(d)
    assert(PheromoneTrails.canAdjust(purchased, NectarMeadow.id, 5 * u))
    assertEquals(purchased.exploration, d.exploration)
    assert(!PheromoneTrails.canAdjust(purchased, RivalFoodCache.id, 5 * u))
    val unstudied = purchased.copy(exploration = purchased.exploration.copy(surveys = Map.empty))
    assert(!PheromoneTrails.canAdjust(unstudied, NectarMeadow.id, 5 * u))
    val noResin = purchased.copy(exploration = purchased.exploration.copy(surveys = purchased.exploration.surveys.updated("fallen-pine", 1)))
    assert(!PheromoneTrails.canAdjust(noResin, CollectResin.id, 5 * u))
  }
  test("manual collection scales linearly, accepts 5-50 groups, and rejects non-groups") {
    for (n <- 5L to 50L by 5L) {
      assert(ExplorationHelper.canStart(colony, n * u, NectarMeadow), n.toString)
      assertEquals(ExplorationHelper.load(colony, n * u, NectarMeadow).sugar, 75L * n * u)
    }
    for (n <- List(0L, 1L, 4L, 6L, 49L, 51L, 55L)) {
      assert(!ExplorationHelper.canStart(colony, n * u, NectarMeadow), n.toString)
      assertEquals(ExplorationHelper.start(colony, n * u, NectarMeadow), colony)
    }
  }
  test("automatic gross output is at most 80% of manual throughput and supply cost is not discounted") {
    for (route <- List(WoodlandEdge, RottenLog, NectarMeadow, SeedGathering, ScavengeWater, CollectResin);
         n <- 5L to 50L by 5L; rank <- 0 to 3) {
      val upgrades = colony.upgrades.unlock(UpgradesData.UpgradeType.expeditionSupplies.take(rank): _*)
      val (gain, cost) = PheromoneTrails.nominal(route, n * u, upgrades)
      val reward = ExplorationProgression.partyReward(List(route.sugarReward, route.proteinReward, route.waterReward, route.resinReward).max, n * u, rank)
      assert(gain * route.duration * 100L <= reward * 80L)
      assert(cost * route.duration * 100L >= ExplorationHelper.cost(n * u, route).sugar * 80L)
      assert(gain > 0L && cost > 0L)
    }
  }
  test("automatic collection pays ongoing sugar and consumes the exact shared finite deposit") {
    val d = assign(colony, NectarMeadow, 10)
    val expected = plans(d).head
    val next = PheromoneTrails.tick(d)
    assertEquals(next.basicResources.sugar.amount, d.basicResources.sugar.amount + expected.cargo.sugar - expected.sugarCost)
    assertEquals(ExplorationMap.remaining(d.exploration, NectarMeadow.id) - ExplorationMap.remaining(next.exploration, NectarMeadow.id), expected.cargo.sugar)
    assertEquals(next.statistics.sugarU.thisRun, expected.cargo.sugar)
    assertEquals(next.exploration.explorationParties, Nil)
    assertEquals(next.ants.ages, d.ants.ages)
  }
  test("manual cargo reservations and trail gathering cannot duplicate resources or storage") {
    val sent = ExplorationHelper.start(colony, 5 * u, NectarMeadow)
    val d = assign(sent, NectarMeadow, 10)
    val before = ExplorationMap.remaining(d.exploration, NectarMeadow.id)
    val next = PheromoneTrails.tick(d)
    val gained = next.statistics.sugarU.thisRun
    assertEquals(ExplorationMap.remaining(next.exploration, NectarMeadow.id), before - gained)
    assertEquals(next.exploration.explorationParties, sent.exploration.explorationParties)
    val reserved = d.exploration.explorationParties.head.cargo.sugar
    val full = d.copy(basicResources = d.basicResources.copy(sugar = BasicResource(100000 * u - reserved, 100000 * u)))
    assertEquals(plans(full).head.status, "Storage full")
    assertEquals(PheromoneTrails.tick(full).basicResources, full.basicResources)
  }
  test("storage and supply shortages pause without spending or depleting, then resume") {
    val d = assign(colony, WoodlandEdge, 10)
    val full = d.copy(basicResources = d.basicResources.copy(protein = BasicResource(100000 * u, 100000 * u)))
    assertEquals(plans(full).head.status, "Storage full")
    assertEquals(PheromoneTrails.tick(full), full)
    val poor = d.copy(basicResources = d.basicResources.copy(sugar = BasicResource(0L, 100000 * u)))
    assertEquals(plans(poor).head.status, "Needs sugar")
    assertEquals(PheromoneTrails.tick(poor), poor)
    assert(PheromoneTrails.tick(d).basicResources.protein.amount > 0L)
  }
  test("last crumbs are exact and exhaustion releases guides and workers") {
    val d = assign(colony, NectarMeadow, 10)
    val last = d.copy(exploration = d.exploration.copy(depleted = Map(NectarMeadow.id -> (ExplorationMap.deposits(NectarMeadow.id) - 5L))))
    val next = PheromoneTrails.tick(last)
    assertEquals(next.statistics.sugarU.thisRun, 5L)
    assertEquals(ExplorationMap.remaining(next.exploration, NectarMeadow.id), 0L)
    assertEquals(next.exploration.collectionTrails, Nil)
    assertEquals(next.ants.taskWorkers(AntTask.TrailForager), 0L)
    assertEquals(next.ants.taskWorkers(AntTask.SugarCollector), 100 * u)
    assertEquals(PheromoneTrails.tick(next).statistics, next.statistics)
  }
  test("scouts reserve independently for each trail and cannot also guide manual missions") {
    val d = assign(assign(colony, NectarMeadow, 5), WoodlandEdge, 15)
    assertEquals(PheromoneTrails.guides(d.ants, d.exploration), 3 * u)
    assertEquals(ScoutProgression.available(d.ants, d.exploration), 7 * u)
    val all = d.copy(ants = d.ants.copy(scouts = 3 * u))
    assert(!ExplorationMap.canScout(all, "surroundings", 1))
    assert(!ExplorationHelper.canStart(all, 5 * u, WoodlandEdge, 1))
    val recalled = PheromoneTrails.recall(all, WoodlandEdge.id)
    assertEquals(PheromoneTrails.guides(recalled.ants, recalled.exploration), u)
    assertEquals(PheromoneTrails.workers(recalled.ants), 5 * u)
  }
  test("losing guides pauses assigned routes; new scouts resume without granting resources") {
    val d = assign(colony, NectarMeadow, 50)
    val short = d.copy(ants = d.ants.copy(scouts = 4 * u))
    assertEquals(plans(short).head.status, "Needs scouts")
    assertEquals(PheromoneTrails.tick(short).basicResources, short.basicResources)
    val restored = short.copy(ants = short.ants.copy(scouts = 5 * u))
    assert(PheromoneTrails.tick(restored).statistics.sugarU.thisRun > 0L)
  }
  test("recalling an understaffed later route does not take workers from an earlier route") {
    val d = assign(assign(colony, NectarMeadow, 5), WoodlandEdge, 10)
    val short = d.copy(ants = d.ants.copy(tasks = Seq(AntTask.TrailForager -> (7 * u), AntTask.SugarCollector -> (93 * u))))
    val recalled = PheromoneTrails.recall(short, WoodlandEdge.id)
    assertEquals(PheromoneTrails.workers(recalled.ants), 5 * u)
    assertEquals(PheromoneTrails.staffed(recalled.ants, recalled.exploration, NectarMeadow.id), 5 * u)
  }
  test("staffing/deposits roundtrip and legacy unused nectar returns without duplication") {
    val d = PheromoneTrails.tick(assign(colony, NectarMeadow, 15))
    val loaded = load(d)
    assertEquals(loaded.exploration.collectionTrails, d.exploration.collectionTrails)
    assertEquals(loaded.exploration.depleted, d.exploration.depleted)
    assertEquals(PheromoneTrails.workers(loaded.ants), 15 * u)
    val old = colony.copy(ants = colony.ants.copy(tasks = Seq(AntTask.TrailForager -> (10 * u), AntTask.SugarCollector -> (90 * u))),
      exploration = colony.exploration.copy(sugarTrail = SugarTrail(1200 * u, true), depleted = Map(NectarMeadow.id -> (1800 * u))))
    val migrated = load(old)
    assertEquals(migrated.exploration.sugarTrail, SugarTrail())
    assertEquals(migrated.exploration.depleted(NectarMeadow.id), 600 * u)
    assertEquals(List(migrated.basicResources.sugar.amount, migrated.basicResources.protein.amount, migrated.basicResources.water.amount, migrated.basicResources.resin.amount),
      List(old.basicResources.sugar.amount, old.basicResources.protein.amount, old.basicResources.water.amount, old.basicResources.resin.amount))
    assertEquals(PheromoneTrails.workers(migrated.ants), 0L)
    assertEquals(load(migrated).exploration.depleted, migrated.exploration.depleted)
    assertEquals("{}".fromJson[ExplorationData].toOption.get.collectionTrails, Nil)
  }
  test("trail workers and scouts retain home aging; routine replaces worker vacancies") {
    val assigned = assign(colony, NectarMeadow, 50)
    val d = assigned.copy(ants = assigned.ants.copy(workers = 50 * u,
      tasks = Seq(AntTask.TrailForager -> (50 * u)),
      ages = ColonyAges(active = true, workers = List(AgeCohort(1L, 1L), AgeCohort(10000L, 49L)), scouts = List(AgeCohort(10000L, 10L)))),
      upgrades = assigned.upgrades.unlock(QueensRoutine))
    val aged = PopulationAging.tick(d.copy(world = d.world.copy(currentTick = 1L)))
    assertEquals(PheromoneTrails.workers(aged.ants), 49 * u)
    assertEquals(aged.ants.agingJobVacancies.find(_._1 == AntTask.TrailForager).map(_._2), Some(u))
    val replaced = DefaultWorkerAssignment.normalize(aged.copy(ants = aged.ants.copy(workers = 50 * u)))
    assertEquals(PheromoneTrails.workers(replaced.ants), 50 * u)
    assertEquals(replaced.exploration.collectionTrails, d.exploration.collectionTrails)
    assertEquals(PheromoneTrails.adjust(assigned, NectarMeadow.id, -5 * u).ants.ages, assigned.ants.ages)
  }
  test("royal forecast includes trail supply costs and automatic water before care") {
    val assigned = assign(colony, ScavengeWater, 10)
    val d = assigned.copy(ants = assigned.ants.copy(royalBrood = Some(RoyalBrood()),
      tasks = assigned.ants.tasks :+ (AntTask.RoyalNurse -> (5 * u))),
      nestAttributes = assigned.nestAttributes.copy(chambers = assigned.nestAttributes.chambers.copy(royalNurseryChamber = Chamber.RoyalNurseryChamber(1))))
    val predicted = RoyalBroodProgression.forecast(d.ants, d.nestAttributes, d.upgrades, d.exploration, d.basicResources)
    val next = TickUpdater.updateAllData(d, None)
    assertEquals(next.ants.royalBrood.get.careUnits, predicted.units)
    assert(predicted.units > 0L)
  }
}
