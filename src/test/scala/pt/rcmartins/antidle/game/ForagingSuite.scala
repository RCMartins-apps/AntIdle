package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import pt.rcmartins.antidle.game.saves.AllDataForSave

class ForagingSuite extends FunSuite {
  val plain: UpgradesData = UpgradesData.initial
  val improved: UpgradesData = plain.unlock(ImproveSugarCollectorTask1)
  val advanced: UpgradesData = plain.unlock(ImproveSugarCollectorTask1, ImproveSugarCollectorTask2, ImproveSugarCollectorTask3)

  test("linear marginal yields produce diminishing returns and plateau without becoming negative") {
    assertEquals(Foraging.ProductiveCollectors, 80L)
    assertEquals(Foraging.basePerTick(0L), 0L)
    assertEquals(Foraging.basePerTick(1L) * TicksPerSecond, 4000L)
    assertEquals(Foraging.basePerTick(20L) * TicksPerSecond, 70500L)
    assertEquals(Foraging.basePerTick(40L) * TicksPerSecond, 121000L)
    assertEquals(Foraging.basePerTick(80L) * TicksPerSecond, 162000L)
    assertEquals(Foraging.basePerTick(Long.MaxValue), Foraging.basePerTick(80L))
    val totals = (0L to 200L).map(n => Foraging.basePerTick(n))
    assert(totals.sliding(2).forall { case Seq(a, b) => b >= a; case _ => true })
  }

  test("an all-collector colony supports about eighty workers without upgrades") {
    def sustainable(upgrades: UpgradesData): Long =
      (1L to 300L).filter(n => Foraging.productionPerTick(n, upgrades) >= n * antsSugarUpkeepTick).last
    assertEquals(sustainable(plain), 81L)
    assertEquals(sustainable(improved), 93L)
    assertEquals(sustainable(advanced), 123L)
    assert(Foraging.productionPerTick(82L, plain) < 82L * antsSugarUpkeepTick)
  }

  test("the current average and next collector rate include upgrades and retain small positive contributions") {
    assertEquals(Foraging.averagePerSecond(0L, plain), 4000L)
    assertEquals(Foraging.nextPerSecond(0L, plain), 4000L)
    assertEquals(Foraging.nextPerSecond(1L, plain), 3950L)
    assertEquals(Foraging.nextPerSecond(79L, plain), 50L)
    assertEquals(Foraging.nextPerSecond(80L, plain), 0L)
    assertEquals(Foraging.nextPerSecond(Long.MaxValue, plain), 0L)
    assertEquals(Foraging.averagePerSecond(80L, plain), 2025L)
    assertEquals(Foraging.averagePerSecond(1L, improved), 4600L)
    assertEquals(UINumbersUtils.preciseRateStr(50L), "0.005")
    assertEquals(UINumbersUtils.preciseRateStr(4000L), "0.4")
  }

  test("actual tick income uses the same production curve and upgrade rounding") {
    List(0L, 1L, 20L, 40L, 60L, 80L, 81L, 100L, 10000L).foreach { count =>
      List(plain, improved, advanced, plain.unlock(EstablishedTrails), advanced.unlock(EstablishedTrails)).foreach { upgrades =>
        val initial = AllData.initial.copy(
          ants = AntsData(workers = count * u, tasks = Seq(AntTask.SugarCollector -> (count * u))),
          upgrades = upgrades,
          basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(500 * u, 10000 * u)),
        )
        val ticked = TickUpdater.updateAllData(initial, None)
        val expected = initial.basicResources.sugar.amount + Foraging.productionPerTick(count, upgrades) - count * antsSugarUpkeepTick
        assertEquals(ticked.basicResources.sugar.amount, expected, s"collectors=$count")
      }
    }
  }

  test("temporary assignment changes cannot reset diminishing returns") {
    val all = AntsData(workers = 80 * u, tasks = Seq(AntTask.SugarCollector -> (80 * u), AntTask.Tunneler -> 0L), defaultTask = Some(AntTask.SugarCollector))
    val sent = all.addToTask(AntTask.Tunneler, 5 * u)
    val restored = sent.removeFromTask(AntTask.Tunneler, 5 * u)
    assertEquals(restored.taskWorkers(AntTask.SugarCollector), 80L * u)
    assertEquals(Foraging.productionPerTick(restored.taskWorkers(AntTask.SugarCollector) / u, plain), Foraging.productionPerTick(80L, plain))
  }

  test("Established Trails expands productive collectors and raises the sustainable ceiling") {
    val trails = plain.unlock(EstablishedTrails)
    assertEquals(Foraging.productiveCollectors(trails), 100L)
    assertEquals(Foraging.productionPerTick(100L, trails) * TicksPerSecond, 202000L)
    assertEquals(Foraging.nextPerSecond(80L, trails), 800L)
    assertEquals(Foraging.nextPerSecond(99L, trails), 40L)
    assertEquals(Foraging.nextPerSecond(100L, trails), 0L)
    assertEquals(Foraging.productionPerTick(Long.MaxValue, trails), Foraging.productionPerTick(100L, trails))
    assertEquals((1L to 200L).filter(n => Foraging.productionPerTick(n, trails) >= n * antsSugarUpkeepTick).last, 101L)
    val colony = AllData.initial.copy(unlocks = AllData.initial.unlocks.copy(tabs = AllData.initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
    assert(!EstablishedTrails.shouldShow(colony.copy(exploration = MapFixture.explored.copy(revealedSites = 0))))
    assert(EstablishedTrails.shouldShow(colony.copy(exploration = MapFixture.explored.copy(revealedSites = 6))))
    assertEquals(EstablishedTrails.cost, ActionCost(sugar = 600 * u, knowledge = 60 * u))
    assert(AllDataForSave.fromAllData(colony.copy(upgrades = trails)).toAllData.upgrades(EstablishedTrails).unlocked)
  }

  test("paid mission slot requires two revealed sites and stacks with the fourth-reveal slot") {
    val initial = AllData.initial.copy(unlocks = AllData.initial.unlocks.copy(tabs = AllData.initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
    assert(!AdditionalExpeditionSlot.shouldShow(initial.copy(exploration = MapFixture.explored.copy(revealedSites = 1))))
    assert(AdditionalExpeditionSlot.shouldShow(initial.copy(exploration = MapFixture.explored.copy(revealedSites = 6))))
    val purchased = plain.unlock(AdditionalExpeditionSlot)
    assertEquals(ExplorationProgression.maxParties(ExplorationData(revealedSites = 2), plain), 1)
    assertEquals(ExplorationProgression.maxParties(ExplorationData(revealedSites = 2), purchased), 2)
    assertEquals(ExplorationProgression.maxParties(ExplorationData(revealedSites = 4), purchased), 3)
    val saved = AllDataForSave.fromAllData(initial.copy(upgrades = purchased))
    assertEquals(ExplorationProgression.maxParties(ExplorationData(revealedSites = 4), saved.toAllData.upgrades), 3)
  }

  test("the engine enforces the purchased slot limit and releases slots on return") {
    val initial = AllData.initial.copy(
      ants = AntsData(workers = 20 * u, tasks = Seq(AntTask.Explorer -> 0L)),
      upgrades = plain.unlock(AdditionalExpeditionSlot),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      basicResources = AllData.initial.basicResources.copy(sugar = BasicResource(1000 * u, 1000 * u)),
      unlocks = AllData.initial.unlocks.copy(tabs = AllData.initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
    val three = (1 to 3).foldLeft(initial)((data, _) => ExplorationHelper.start(data, u, ExplorationDestination.WoodlandEdge))
    assertEquals(three.exploration.explorationParties.size, 3)
    assertEquals(ExplorationHelper.start(three, u, ExplorationDestination.WoodlandEdge), three)
    val returned = TickUpdater.updateAllData(three.copy(world = three.world.copy(currentTick = ExplorationDestination.WoodlandEdge.duration - 1L)), None)
    assert(returned.exploration.explorationParties.isEmpty)
    assertEquals(returned.ants.idleWorkersCount, 20L * u)
    assert(ExplorationHelper.canStart(returned, u, ExplorationDestination.WoodlandEdge))
  }
}
