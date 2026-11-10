package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class ColonyManagementSuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(upgrades = initial.upgrades.unlock(ConstructionPlanning),
      basicResources = initial.basicResources.copy(tunnelingSpace = BasicResource(1000 * u, 2000 * u), protein = BasicResource(100 * u, 100 * u)))
  }

  test("planning unlocks three queue slots and repeated chamber plans use successive level costs") {
    val initial = UpgradeVisibility.reconcile(colony)
    assertEquals(initial.nestAttributes.maxBuildQueue, 3)
    val queued = (1 to 3).foldLeft(initial)((data, _) => Construction.enqueue(data, Nursery))
    assertEquals(queued.nestAttributes.buildQueue.map(_.actionCost.protein), List(3L, 6L, 9L).map(_ * u))
    assertEquals(queued.nestAttributes.buildQueue.map(_.actionCost.tunnelingSpace), List(100L, 150L, 225L).map(_ * u))
    assertEquals(Construction.enqueue(queued, Nest), queued)
    assertEquals(UpgradeVisibility.reconcile(queued), queued)
    assertEquals(Construction.nextCost(queued.nestAttributes, Nursery).protein, 12L * u)
  }

  test("cancelling a planned level reprices only subsequent plans of the same chamber") {
    val queued = List(Nursery, FoodStorage, Nursery).foldLeft(colony)((data, kind) => Construction.enqueue(data, kind))
    val foodPrice = queued.nestAttributes.buildQueue(1).actionCost
    val cancelled = Construction.cancel(queued, 0)
    assertEquals(cancelled.nestAttributes.buildQueue.head.actionCost, foodPrice)
    assertEquals(cancelled.nestAttributes.buildQueue(1).actionCost.protein, 3L * u)
    assertEquals(Construction.cancel(cancelled, -1), cancelled)
    assertEquals(Construction.cancel(cancelled, 20), cancelled)
  }

  test("completed plans charge escalating costs exactly once without repricing the next level twice") {
    val queued = (1 to 3).foldLeft(colony)((data, _) => Construction.enqueue(data, Nursery))
    val built = (1 to 3).foldLeft(queued)((data, _) => Construction.tick(data))
    assert(built.nestAttributes.buildQueue.isEmpty)
    assertEquals(built.nestAttributes.chambers.nurseryChamber.level, 3)
    assertEquals(built.basicResources.protein.amount, 82L * u)
    assertEquals(built.basicResources.tunnelingSpace.amount, 525L * u)
  }

  test("protein shortages block the first plan and save load repairs stale prices") {
    val poor = colony.copy(basicResources = colony.basicResources.copy(protein = BasicResource(0L, 100 * u)))
    val queued = Construction.enqueue(Construction.enqueue(poor, Nursery), FoodStorage)
    assertEquals(Construction.tick(queued), queued)
    val stale = queued.copy(nestAttributes = queued.nestAttributes.copy(buildQueue = queued.nestAttributes.buildQueue.map(_.copy(actionCost = ActionCost.empty))))
    val loaded = AllDataForSave.fromAllData(stale).toAllData
    assertEquals(loaded.nestAttributes.buildQueue, queued.nestAttributes.buildQueue)
    assertEquals(loaded.nestAttributes.maxBuildQueue, 3)
    assertEquals(Construction.reconcile(loaded), loaded)
  }

  test("wait estimates round up and handle protein deficits, zero income and storage limits") {
    assertEquals(ResourceWait.ticks(11L, BasicResource(10L, 100L), 5L), 1L)
    assertEquals(ResourceWait.ticks(21L, BasicResource(10L, 100L), 5L), 3L)
    assertEquals(ResourceWait.ticks(10L, BasicResource(10L, 100L), -1L), 0L)
    assertEquals(ResourceWait.ticks(3 * u, BasicResource(0L, 100 * u), 0L), -1L)
    assertEquals(ResourceWait.ticks(3 * u, BasicResource(0L, 100 * u), -20L), -1L)
    assertEquals(ResourceWait.ticks(101L, BasicResource(10L, 100L), 5L), -1L)
  }

  test("nutrition extends only new adult lifespans and preserves existing fixed expiries") {
    val initial = colony.copy(ants = AntsData(workers = 10 * u, scouts = 3 * u),
      nestAttributes = colony.nestAttributes.copy(chambers = colony.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
    val original = PopulationAging.reconcile(initial)
    val researched = original.copy(upgrades = original.upgrades.unlock(ColonyNutrition))
    assertEquals(PopulationAging.reconcile(researched).ants.ages, original.ants.ages)
    val grown = researched.copy(ants = researched.ants.copy(workers = 20 * u, scouts = 6 * u))
    val extended = PopulationAging.reconcile(grown)
    val newWorkers = extended.ants.ages.workers.map(c => c.copy(count = c.count -
      original.ants.ages.workers.find(_.expiresAt == c.expiresAt).map(_.count).getOrElse(0L))).filter(_.count > 0L)
    assertEquals(newWorkers.map(_.count).sum, 10L)
    assert(newWorkers.forall(c => c.expiresAt >= 54L * PopulationAging.Minute && c.expiresAt <= 72L * PopulationAging.Minute))
    assertEquals(PopulationAging.lifespanText(extended.upgrades), "54–72")
    assertEquals(AllDataForSave.fromAllData(extended).toAllData.ants.ages, extended.ants.ages)
  }

  test("new progression upgrades remain hidden until their exploration and aging gates") {
    val initial = AllData.initial
    val exploring = initial.copy(unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
      exploration = MapFixture.explored.copy(revealedSites = 0))
    assert(!ConstructionPlanning.shouldShow(exploring))
    assert(ConstructionPlanning.shouldShow(exploring.copy(exploration = MapFixture.explored.copy(revealedSites = 6))))
    assert(!ColonyNutrition.shouldShow(exploring.copy(exploration = MapFixture.explored.copy(revealedSites = 6))))
    val aged = exploring.copy(ants = exploring.ants.copy(ages = ColonyAges(active = true)), exploration = MapFixture.explored.copy(revealedSites = 2))
    assert(!ColonyNutrition.shouldShow(aged))
    assert(ColonyNutrition.shouldShow(aged.copy(exploration = MapFixture.explored.copy(revealedSites = 6))))
  }
}
