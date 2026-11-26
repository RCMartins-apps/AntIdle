package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.StatisticsData.{StatInt, StatLong}
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class ColonyMilestonesSuite extends FunSuite {
  val stats = StatisticsData(
    sugarU = StatLong(1000 * u, 1000 * u),
    proteinU = StatLong(100 * u, 100 * u),
    workersMatured = StatInt(30, 30),
    smallWorkersMatured = StatInt(5, 5),
    explorationsCompleted = StatInt(20, 20)
  )

  test("every reached tier grants a multiplicative 1.01, exactly at its threshold") {
    ColonyMilestones.goals.foreach { goal =>
      assert(goal.targets == goal.targets.sorted.distinct)
      assert(goal.targets.forall(_ > 0))
    }
    assertEquals(ColonyMilestones.bonus(StatisticsData.initial), 0)
    assertEquals(ColonyMilestones.bonus(stats), 8)
    val sugar = ColonyMilestones.goals.head
    assertEquals(sugar.completed(StatisticsData(sugarU = StatLong(0, 100 * u - 1))), 0)
    assertEquals(sugar.completed(StatisticsData(sugarU = StatLong(0, 100 * u))), 1)
    assertEquals(sugar.next(stats), Some(10000 * u))
    assertEquals(ColonyMilestones.sugar(100 * u, stats), 1082856L)
    assertEquals(ColonyMilestones.sugar(-100L, stats), -100L)
    assertEquals(ColonyMilestones.sugar(Long.MaxValue, stats), Long.MaxValue)
  }

  test("gains receive bonus once, spending stays fixed, overflow does not earn progress") {
    val d = AllData.initial.copy(
      statistics = stats,
      basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(0, 1000 * u))
    )
    val gained = d.giveResources(sugar = 100 * u, protein = u)
    assertEquals(gained.basicResources.sugar.amount, 1082856L)
    assertEquals(gained.statistics.sugarU.thisRun - stats.sugarU.thisRun, 1082856L)
    assertEquals(gained.statistics.proteinU.thisRun - stats.proteinU.thisRun, u)
    assertEquals(gained.spendResources(ActionCost(sugar = u)).basicResources.sugar.amount, 1072856L)
    val full = gained.giveResources(sugar = Long.MaxValue)
    assertEquals(full.basicResources.sugar.amount, 1000 * u)
    assertEquals(full.giveResources(sugar = 100 * u), full)
    assertEquals(
      d.giveResources(sugar = 1082856L, sugarBonusApplied = true).basicResources.sugar.amount,
      1082856L
    )
  }

  test("production forecast and real tick include the same bonus before upkeep") {
    val d = AllData.initial.copy(
      statistics = stats,
      ants = AntsData(workers = 10 * u, tasks = Seq(AntTask.SugarCollector -> (10 * u))),
      basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(100 * u, 1000 * u))
    )
    val forecast = RoyalBroodProgression.forecastResources(
      d.ants,
      d.nestAttributes,
      d.upgrades,
      d.exploration,
      d.basicResources,
      d.statistics
    )
    val next = TickUpdater.updateAllData(d, None)
    assertEquals(next.basicResources.sugar.amount, forecast.sugar.amount)
    val gross = ColonyMilestones.sugar(Foraging.production(d.ants, d.upgrades), stats)
    assertEquals(
      next.basicResources.sugar.amount,
      d.basicResources.sugar.amount + gross - ScoutProgression.upkeepPerTick(d.ants, d.exploration)
    )
  }

  test("milestones and resource peaks survive spending, reload and founding") {
    val d =
      AllData.initial.copy(statistics = stats, ants = AllData.initial.ants.copy(daughterQueens = u))
    val tracked = ColonyMilestones.recordPeaks(d)
    val loaded =
      AllDataForSave.fromAllData(tracked).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(ColonyMilestones.bonus(loaded.statistics), 8)
    val founded = Prestige.found(loaded)
    assertEquals(ColonyMilestones.bonus(founded.statistics), 8)
    assertEquals(founded.statistics.maxSugarU.total, tracked.basicResources.sugar.amount)
    assertEquals(founded.statistics.maxSugarU.thisRun, 0L)
    assertEquals(founded.statistics.sugarU.total, stats.sugarU.total)
    val spent = tracked.spendResources(ActionCost(sugar = u))
    assertEquals(
      ColonyMilestones.recordPeaks(spent).statistics.maxSugarU.total,
      tracked.basicResources.sugar.amount
    )
    val resin = ColonyMilestones.goals.find(_.id == "resin").get
    assert(!ColonyMilestones.visible(resin, AllData.initial))
    assert(
      ColonyMilestones.visible(resin, d.copy(statistics = stats.copy(resinU = StatLong(1, 1))))
    )
  }

  test("finite sugar hauls and automated trails boost yield, refund overflow and match forecasts") {
    val d = ResourceVisibility.reconcile(
      AllData.initial.copy(
        statistics = stats,
        ants = AntsData(
          workers = 20 * u,
          scouts = 3 * u,
          lineage = Lineage(1, 0L, true),
          tasks = Seq(AntTask.SugarCollector -> (20 * u)),
          defaultTask = Some(AntTask.SugarCollector)
        ),
        upgrades = UpgradesData.initial.unlock(UpgradesData.UpgradeType.DefaultWorkers),
        nestAttributes = AllData.initial.nestAttributes.copy(maxAnts = 100 * u),
        basicResources =
          AllData.initial.basicResources.copy(sugar = BasicResource(100 * u, 10000 * u)),
        exploration = MapFixture.explored,
        unlocks = AllData.initial.unlocks
          .copy(tabs = AllData.initial.unlocks.tabs.copy(exploreTabUnlocked = true))
      )
    )
    val route = ExplorationDestination.NectarMeadow
    val sent = ExplorationHelper.start(d, 5 * u, route)
    val party = sent.exploration.explorationParties.head
    val almostFull = sent.copy(basicResources =
      sent.basicResources.copy(sugar = BasicResource(9999 * u, 10000 * u))
    )
    val returned = ExplorationHelper.exploreBonus(almostFull, party)
    assertEquals(returned.basicResources.sugar.amount, 10000 * u)
    assertEquals(
      returned.exploration.depleted(route.id) - d.exploration.depleted.getOrElse(route.id, 0L),
      ColonyMilestones.baseSugar(u, almostFull.statistics)
    )
    val trail = PheromoneTrails.adjust(d, route.id, 5 * u)
    val predicted = PheromoneTrails.forecastResources(
      trail.ants,
      trail.exploration,
      trail.upgrades,
      trail.basicResources,
      trail.statistics
    )
    val actual = PheromoneTrails.tick(trail)
    assertEquals(actual.basicResources, predicted)
    assert(actual.exploration.depleted(route.id) > d.exploration.depleted.getOrElse(route.id, 0L))
    assertEquals(
      actual.statistics.sugarU.total - trail.statistics.sugarU.total,
      actual.basicResources.sugar.amount - trail.basicResources.sugar.amount + PheromoneTrails
        .forecast(
          trail.ants,
          trail.exploration,
          trail.upgrades,
          trail.basicResources,
          trail.statistics
        )
        .map(_.sugarCost)
        .sum
    )
  }

  test("older saves default absent peak fields and seed records from current stores") {
    val d = AllData.initial.copy(statistics = stats)
    val json = AllDataForSave.fromAllData(d).toJson
    val legacy = json.replaceAll(
      "\"max(?:Sugar|TunnelingSpace|Knowledge|Protein|Water|Resin)U\":\\{[^}]*\\},?",
      ""
    )
    val loaded = legacy.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.statistics.maxSugarU.total, d.basicResources.sugar.amount)
    assertEquals(ColonyMilestones.bonus(loaded.statistics), 8)
  }

}
