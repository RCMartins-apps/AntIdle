package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class RoyalNurserySuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 30 * u, scouts = 2 * u, repletes = 3 * u,
        tasks = Seq(AntTask.SugarCollector -> (30 * u), AntTask.Explorer -> 0L), defaultTask = Some(AntTask.SugarCollector),
        ages = ColonyAges(active = true, workers = List(AgeCohort(18000L, 30L)),
          scouts = List(AgeCohort(18000L, 2L)), repletes = List(AgeCohort(36000L, 3L)))),
      basicResources = initial.basicResources.copy(sugar = BasicResource(30000 * u, 40000 * u),
        protein = BasicResource(300 * u, 500 * u), water = BasicResource(400 * u, 400 * u),
        knowledge = BasicResource(3000 * u, Long.MaxValue), tunnelingSpace = BasicResource(5000 * u, 5000 * u)),
      nestAttributes = initial.nestAttributes.copy(maxBuildQueue = 3,
        chambers = initial.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1),
          repleteChamber = Chamber.RepleteChamber(3))),
      exploration = MapFixture.explored.copy(revealedSites = 5, surveys = Map("fallen-pine" -> 0)),
      upgrades = initial.upgrades.unlock(DefaultWorkers, ConstructionPlanning, RepleteStorage),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }

  def discovered: AllData = colony.copy(exploration = colony.exploration.copy(
    revealedSites = 6, surveys = Map("fallen-pine" -> 3)))

  def researched: AllData = discovered.copy(upgrades = discovered.upgrades.unlock(RoyalNurseryPlanning))

  def trip(data: AllData, destination: ExplorationDestination): AllData = {
    val sent = ExplorationHelper.start(data, 10 * u, destination)
    assert(sent.exploration.explorationParties.nonEmpty)
    ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
  }

  test("scout study finds resin before workers can collect it") {
    val d = colony.copy(exploration = ExplorationData(revealedSites = 5))
    assertEquals(ExplorationHelper.start(d, 10 * u, CollectResin), d)
    val studied = (1 to 3).foldLeft(d) { (data, _) =>
      val sent = ExplorationMap.scout(data, "fallen-pine", 1)
      ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    }
    assert(DiscoveryProgression.ResinSeep.found(studied.exploration))
    assert(ExplorationHelper.canStart(studied, 10 * u, CollectResin))
    assertEquals(studied.basicResources.resin.amount, 0L)
  }

  test("resin trips enforce sugar, worker and scout reservations without carcass storage") {
    val initial = discovered
    assert(!ExplorationHelper.canStart(initial, 9 * u, CollectResin))
    assert(!ExplorationHelper.canStart(initial, 38 * u, CollectResin))
    val noScouts = initial.copy(ants = initial.ants.copy(scouts = u))
    assert(ExplorationHelper.canStart(noScouts, 10 * u, CollectResin))
    val noSugar = initial.copy(basicResources = initial.basicResources.copy(sugar = BasicResource(349 * u, 40000 * u)))
    assert(!ExplorationHelper.canStart(noSugar, 10 * u, CollectResin))
    val sent = ExplorationHelper.start(initial, 10 * u, CollectResin)
    assertEquals(sent.ants.taskWorkers(AntTask.SugarCollector), 20L * u)
    assertEquals(ScoutProgression.available(sent.ants, sent.exploration), 2L * u)
    assertEquals(sent.basicResources.sugar.amount, initial.basicResources.sugar.amount - 350 * u)
    assert(sent.exploration.retrievals.isEmpty && sent.exploration.carcasses.isEmpty)
  }

  test("resin forecasts, actual retained rewards and gathered statistics agree and cannot replay") {
    val initial = discovered.copy(exploration = discovered.exploration.copy(suppliesLevel = 2))
    val sent = ExplorationHelper.start(initial, 10 * u, CollectResin)
    val party = sent.exploration.explorationParties.head
    val expected = ExplorationProgression.partyReward(CollectResin.resinReward, 10 * u, 2)
    assert(ExplorationHelper.summary(initial, 10 * u, CollectResin).contains(s"${UINumbersUtils.prettyNumberStr(expected)} resin"))
    assert(ExplorationHelper.outcomePreview(initial.exploration, 10 * u, CollectResin).contains("Resin guaranteed"))
    val almostFull = sent.copy(basicResources = sent.basicResources.copy(resin = BasicResource(199 * u, 200 * u)))
    val result = ExplorationHelper.exploreBonus(almostFull, party)
    assertEquals(result.basicResources.resin.amount, 200L * u)
    assertEquals(result.statistics.resinU.thisRun, u)
    assertEquals(ExplorationHelper.exploreBonus(result, party), result)
    assertEquals(result.ants.taskWorkers(AntTask.SugarCollector), 30L * u)
    assertEquals(result.exploration.discoveredCarcasses.size, 0)
  }

  test("nursery research requires resin study, five revealed sites and an ordinary nursery") {
    assert(!RoyalNurseryPlanning.shouldShow(colony))
    assert(RoyalNurseryPlanning.shouldShow(discovered))
    val noNursery = discovered.copy(nestAttributes = discovered.nestAttributes.copy(
      chambers = discovered.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(0))))
    assert(!RoyalNurseryPlanning.shouldShow(noNursery))
    val early = discovered.copy(exploration = discovered.exploration.copy(revealedSites = 4))
    assert(!RoyalNurseryPlanning.shouldShow(early))
    assert(UpgradeVisibility.reconcile(discovered).upgrades(RoyalNurseryPlanning).show)
  }

  test("royal construction waits for resin then spends all costs once without creating brood") {
    val queued = Construction.enqueue(researched, ChamberType.RoyalNursery)
    assertEquals(queued.nestAttributes.buildQueue.head.actionCost.resin, 80L * u)
    assertEquals(Construction.tick(queued), queued)
    val funded = queued.giveResources(resin = 80 * u)
    val built = Construction.tick(funded)
    assertEquals(built.basicResources, funded.basicResources.spendResources(RoyalNurseryProgression.buildCost))
    assertEquals(built.nestAttributes.royalBroodSlots, 1)
    assertEquals(built.nestAttributes.maxAnts, funded.nestAttributes.maxAnts)
    assertEquals(built.nestAttributes.maxEggs, funded.nestAttributes.maxEggs)
    assertEquals(built.ants, funded.ants)
    assertEquals(built.statistics.resinU.thisRun, 80L * u)
    assert(built.nestAttributes.buildQueue.isEmpty)
    assertEquals(Construction.tick(built), built)
    assertEquals(Construction.enqueue(built, ChamberType.RoyalNursery), built)
    assertEquals(ChamberBonuses.forChamber(ChamberType.RoyalNursery).royalBroodSlots, 1)
  }

  test("locked, duplicate and full queue plans are rejected; cancellation spends nothing") {
    assertEquals(Construction.enqueue(colony, ChamberType.RoyalNursery), colony)
    val queued = Construction.enqueue(researched, ChamberType.RoyalNursery)
    assertEquals(Construction.enqueue(queued, ChamberType.RoyalNursery), queued)
    val cancelled = Construction.cancel(queued, 0)
    assertEquals(cancelled.basicResources, researched.basicResources)
    assert(RoyalNurseryProgression.canPlan(cancelled.nestAttributes, cancelled.upgrades))
    val full = researched.copy(nestAttributes = researched.nestAttributes.copy(buildQueue = List.fill(3)(BuildTask(ChamberType.Nest, ActionCost.empty))))
    assert(!Construction.enqueue(full, ChamberType.RoyalNursery).nestAttributes.buildQueue.exists(_.chamberType == ChamberType.RoyalNursery))
  }

  test("loading repairs resin costs and removes invalid or duplicate royal plans") {
    val imported = researched.copy(nestAttributes = researched.nestAttributes.copy(buildQueue = List(
      BuildTask(ChamberType.RoyalNursery, ActionCost.empty), BuildTask(ChamberType.RoyalNursery, ActionCost.empty),
      BuildTask(ChamberType.FoodStorage, ActionCost.empty))))
    val repaired = Construction.reconcile(imported)
    assertEquals(repaired.nestAttributes.buildQueue.size, 2)
    assertEquals(repaired.nestAttributes.buildQueue.head.actionCost, RoyalNurseryProgression.buildCost)
    assertEquals(Construction.reconcile(repaired), repaired)
    val locked = imported.copy(upgrades = colony.upgrades)
    assert(Construction.reconcile(locked).nestAttributes.buildQueue.forall(_.chamberType != ChamberType.RoyalNursery))
    val loaded = AllDataForSave.fromAllData(imported).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.nestAttributes.buildQueue, repaired.nestAttributes.buildQueue)
  }

  test("resin visibility persists at zero and old saves get safe resource and chamber defaults") {
    assert(!ResourceVisibility.reconcile(colony).unlocks.resources.showResin)
    val known = ResourceVisibility.reconcile(discovered)
    assert(known.unlocks.resources.showResin)
    val spent = known.copy(exploration = known.exploration.copy(surveys = Map.empty))
    assert(ResourceVisibility.reconcile(spent).unlocks.resources.showResin)
    assertEquals("{}".fromJson[BasicResources].toOption.get.resin, BasicResource(0L, 200 * u))
    assertEquals("{}".fromJson[ActionCost].toOption.get.resin, 0L)
    assertEquals("{}".fromJson[AllChamberData].toOption.get.royalNurseryChamber.level, 0)
    val built = ResourceVisibility.reconcile(Construction.tick(Construction.enqueue(researched.giveResources(resin = 80 * u), ChamberType.RoyalNursery)))
    val loaded = AllDataForSave.fromAllData(built).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.nestAttributes.royalBroodSlots, 1)
    assertEquals(loaded.basicResources.resin.amount, 0L)
    assert(loaded.unlocks.resources.showResin)
    assertEquals(loaded.nestAttributes.maxAnts, built.nestAttributes.maxAnts)
  }

  test("a returning material expedition funds the waiting nursery on the following tick") {
    val initial = researched.giveResources(resin = 70 * u)
    val queued = Construction.enqueue(initial, ChamberType.RoyalNursery)
    val sent = ExplorationHelper.start(queued, 10 * u, CollectResin)
    val party = sent.exploration.explorationParties.head
    val returning = TickUpdater.updateAllData(sent.copy(world = sent.world.copy(currentTick = party.targetTick - 1L)), None)
    assertEquals(returning.nestAttributes.royalBroodSlots, 0)
    assert(returning.basicResources.resin.amount >= 80 * u)
    val built = TickUpdater.updateAllData(returning, None)
    assertEquals(built.nestAttributes.royalBroodSlots, 1)
    assertEquals(built.basicResources.resin.amount, returning.basicResources.resin.amount - 80 * u)
    assertEquals(TickUpdater.updateAllData(built, None).basicResources.resin.amount, built.basicResources.resin.amount)
  }

  test("existing early chambers do not acquire material requirements") {
    Seq(ChamberType.Nest, ChamberType.Queen, ChamberType.FoodStorage, ChamberType.Nursery,
      ChamberType.Aphid, ChamberType.Replete).foreach(kind => assertEquals(Construction.cost(kind, 0).resin, 0L))
    assert(!colony.basicResources.hasResources(ActionCost(resin = u)))
    assert(colony.giveResources(resin = u).basicResources.hasResources(ActionCost(resin = u)))
  }
}
