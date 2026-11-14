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
    val nest = base.nestAttributes.copy(aphids = 10L, chambers = base.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(1)))
    val upgrades = base.upgrades.unlock(UpgradesData.UpgradeType.AphidTending)
    assertEquals(AphidHusbandry.productionPerTick(aphids, nest, upgrades, u), 1760L)
    assertEquals(AphidHusbandry.proteinPerTick(aphids, nest, upgrades, u), 20L)
  }
  test("remembered protein does not reveal Nursery before standard workers in a new founding") {
    val next = Prestige.found(colony)
    assert(next.exploration.proteinDiscovered)
    assert(!BroodProgression.nurseryAvailable(next.ants, next.nestAttributes, next.exploration))
    val developed = BroodProgression.reconcile(next.copy(upgrades = next.upgrades.unlock(UpgradesData.UpgradeType.StandardWorkers)))
    assert(BroodProgression.nurseryAvailable(developed.ants, developed.nestAttributes, developed.exploration))
    val legacy = next.nestAttributes.copy(chambers = next.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1)))
    assert(BroodProgression.nurseryAvailable(next.ants, legacy, next.exploration))
    assert(!BroodProgression.nurseryAvailable(AntsData(), NestAttributes.initial, ExplorationData()))
  }

}
