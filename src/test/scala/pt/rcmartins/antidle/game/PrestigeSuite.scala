package pt.rcmartins.antidle.game
import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._
class PrestigeSuite extends FunSuite {
  def colony: AllData = AllData.initial.copy(
    ants = AntsData(workers = 20 * u, scouts = 2 * u, daughterQueens = u,
      tasks = Seq(AntTask.SugarCollector -> (20 * u)), defaultTask = Some(AntTask.SugarCollector)),
    basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(500 * u, 1000 * u)),
    upgrades = AllData.initial.upgrades.unlock(UpgradesData.UpgradeType.DefaultWorkers),
    exploration = ExplorationData(proteinDiscovered = true),
    unlocks = AllData.initial.unlocks.copy(tabs = AllData.initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
  def known: AllData = Prestige.purchase(Prestige.found(colony)).copy(
    ants = colony.ants.copy(daughterQueens = 0, lineage = Lineage(1, 0L, true)),
    basicResources = colony.basicResources, upgrades = colony.upgrades, unlocks = colony.unlocks,
    exploration = ExplorationData(sugarTrail = SugarTrail(PheromoneTrails.Supply)))
  test("founding consumes the old colony once, retains lineage and lifetime totals, and resets run totals") {
    val data = colony.copy(statistics = StatisticsData(sugarU = StatisticsData.StatLong(30L, 80L),
      maxAliveWorkers = StatisticsData.StatInt(20, 50)))
    val next = Prestige.found(data)
    assertEquals(next.ants.lineage, Lineage(1, 1L))
    assertEquals(next.ants.workers, 0L)
    assertEquals(next.ants.daughterQueens, 0L)
    assertEquals(next.nestAttributes, NestAttributes.initial)
    assertEquals(next.upgrades, UpgradesData.initial)
    assertEquals(next.basicResources, BasicResources.initial)
    assertEquals(next.statistics.sugarU, StatisticsData.StatLong(0L, 80L))
    assertEquals(next.statistics.maxAliveWorkers, StatisticsData.StatInt(0, 50))
    assertEquals(next.world.targetTick - next.currentTick, data.world.targetTick - data.currentTick)
    assert(next.exploration.proteinDiscovered)
    assertEquals(Prestige.found(next), next)
    assertEquals(Prestige.found(AllData.initial), AllData.initial)
  }
  test("DNA purchases Shared Paths once and ownership carries through later foundings") {
    val first = Prestige.found(colony)
    val bought = Prestige.purchase(first)
    assert(bought.ants.lineage.sharedPaths)
    assertEquals(bought.ants.lineage.dna, 0L)
    assertEquals(Prestige.purchase(bought), bought)
    assertEquals(Prestige.purchase(AllData.initial), AllData.initial)
    val second = Prestige.found(bought.copy(ants = bought.ants.copy(daughterQueens = u)))
    assertEquals(second.ants.lineage, Lineage(2, 1L, true))
    assertEquals(Prestige.sugar(10000L, second.ants.lineage), 12000L)
  }
  test("inherited production boosts collectors and aphids without changing consumption or expedition reward") {
    val base = colony
    val improved = base.copy(ants = base.ants.copy(lineage = Lineage(1)))
    val normal = TickUpdater.updateAllData(base, None)
    val boosted = TickUpdater.updateAllData(improved, None)
    val production = Foraging.productionPerTick(20L, base.upgrades)
    assertEquals(boosted.basicResources.sugar.amount - normal.basicResources.sugar.amount, production / 10L)
    assertEquals(boosted.ants.upkeepWorkersCount, normal.ants.upkeepWorkersCount)
    val aphids = improved.ants.copy(tasks = Seq(AntTask.AphidTender -> u))
    val nest = base.nestAttributes.copy(chambers = base.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(1)))
    val upgrades = base.upgrades.unlock(UpgradesData.UpgradeType.AphidTending)
    assertEquals(AphidHusbandry.productionPerTick(aphids, nest, upgrades, u), 1760L)
    assertEquals(AphidHusbandry.proteinPerTick(aphids, nest, upgrades, u), 20L)
  }
  test("only known Shared Paths and Nectar Meadow returns reveal a source without refilling it") {
    assertEquals(PheromoneTrails.discover(colony, ExplorationDestination.NectarMeadow), colony)
    val data = known.copy(exploration = ExplorationData())
    assertEquals(PheromoneTrails.discover(data, ExplorationDestination.WoodlandEdge), data)
    val found = PheromoneTrails.discover(data, ExplorationDestination.NectarMeadow)
    assertEquals(found.exploration.sugarTrail.remaining, PheromoneTrails.Supply)
    val depleted = found.copy(exploration = found.exploration.copy(sugarTrail = SugarTrail(10L, true)))
    assertEquals(PheromoneTrails.discover(depleted, ExplorationDestination.NectarMeadow), depleted)
  }
  test("marking charges once, reserves a scout independently and enforces sugar and ownership") {
    val data = known
    val marked = PheromoneTrails.mark(data)
    assertEquals(marked.basicResources.sugar.amount, data.basicResources.sugar.amount - 50 * u)
    assertEquals(ScoutProgression.available(marked.ants, marked.exploration), u)
    assertEquals(ScoutProgression.upkeepCount(marked.ants, marked.exploration), data.ants.upkeepWorkersCount)
    assertEquals(PheromoneTrails.mark(marked), marked)
    val noScout = data.copy(ants = data.ants.copy(scouts = 0L))
    assertEquals(PheromoneTrails.mark(noScout), noScout)
    val poor = data.copy(basicResources = data.basicResources.copy(sugar = BasicResource(0L, 1000 * u)))
    assertEquals(PheromoneTrails.mark(poor), poor)
  }
  test("trail workers borrow from default pool, cap at ten, gather retained supply and return on depletion") {
    val data = PheromoneTrails.assign(PheromoneTrails.mark(known), 50 * u)
    assertEquals(data.ants.taskWorkers(AntTask.TrailForager), 10 * u)
    assertEquals(data.ants.taskWorkers(AntTask.SugarCollector), 10 * u)
    val next = PheromoneTrails.tick(data)
    assertEquals(next.basicResources.sugar.amount - data.basicResources.sugar.amount, 17600L)
    assertEquals(data.exploration.sugarTrail.remaining - next.exploration.sugarTrail.remaining, 17600L)
    assertEquals(next.statistics.sugarU.thisRun, 17600L)
    val last = data.copy(exploration = data.exploration.copy(sugarTrail = SugarTrail(7L, true)))
    val end = PheromoneTrails.tick(last)
    assertEquals(end.ants.taskWorkers(AntTask.TrailForager), 0L)
    assertEquals(end.ants.taskWorkers(AntTask.SugarCollector), 20 * u)
    assertEquals(end.basicResources.sugar.amount - last.basicResources.sugar.amount, 7L)
    assertEquals(ScoutProgression.available(end.ants, end.exploration), 2 * u)
    assertEquals(PheromoneTrails.tick(end).statistics, end.statistics)
  }
  test("storage pauses gathering, recall releases scout/workers without refreshing source or ages") {
    val data = PheromoneTrails.assign(PheromoneTrails.mark(known), 5 * u)
    val full = data.copy(basicResources = data.basicResources.copy(sugar = BasicResource(1000 * u, 1000 * u)))
    assertEquals(PheromoneTrails.tick(full), full)
    val recalled = PheromoneTrails.recall(data)
    assert(!recalled.exploration.sugarTrail.marked)
    assertEquals(recalled.exploration.sugarTrail.remaining, data.exploration.sugarTrail.remaining)
    assertEquals(recalled.ants.taskWorkers(AntTask.TrailForager), 0L)
    assertEquals(recalled.ants.ages, data.ants.ages)
  }
  test("guiding scouts and trail workers retain age cohorts and die normally") {
    val data = PheromoneTrails.assign(PheromoneTrails.mark(known), 5 * u)
    val aging = data.copy(ants = data.ants.copy(scouts = u, ages = ColonyAges(active = true,
      workers = List(AgeCohort(999L, 20L)), scouts = List(AgeCohort(1L, 1L)))))
    assertEquals(PopulationAging.reconcile(aging).ants.ages.scouts, aging.ants.ages.scouts)
    val next = TickUpdater.updateAllData(aging, None)
    assertEquals(next.ants.scouts, 0L)
    assert(!next.exploration.sugarTrail.marked)
    assertEquals(next.ants.taskWorkers(AntTask.TrailForager), 0L)
    assertEquals(next.statistics.scoutsLostToAging.thisRun, 1)
  }
  test("lineage and partially exhausted trail roundtrip; old saves default safely") {
    val data = PheromoneTrails.tick(PheromoneTrails.assign(PheromoneTrails.mark(known), 3 * u))
    val loaded = AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.ants.lineage, data.ants.lineage)
    assertEquals(loaded.exploration.sugarTrail, data.exploration.sugarTrail)
    assertEquals(loaded.ants.taskWorkers(AntTask.TrailForager), 3 * u)
    assertEquals("{}".fromJson[AntsData].toOption.get.lineage, Lineage())
    assertEquals("{}".fromJson[ExplorationData].toOption.get.sugarTrail, SugarTrail())
  }
}
