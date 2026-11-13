package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.DiscoveryProgression._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class DiscoverySuite extends FunSuite {
  def colony: AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 40 * u, scouts = 2 * u, tasks = Seq(AntTask.Explorer -> 0L)),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      basicResources = initial.basicResources.copy(sugar = BasicResource(5000 * u, 10000 * u)),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  def complete(data: AllData, destination: ExplorationDestination, workers: Long = 5 * u): AllData = {
    val sent = ExplorationHelper.start(data, workers, destination)
    assert(sent.exploration.explorationParties.nonEmpty)
    val party = sent.exploration.explorationParties.head
    ExplorationHelper.exploreBonus(sent, party)
  }

  test("discoveries require scout study, not repeat worker collection") {
    val initial = colony.copy(exploration = ExplorationData(revealedSites = 1))
    val studied = (1 to 3).foldLeft(initial) { (data, _) =>
      val sent = ExplorationMap.scout(data, WoodlandEdge.id, 1)
      ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    }
    assert(WildAphids.found(studied.exploration))
    assert(AphidTending.shouldShow(studied))
    val collected = complete(studied, WoodlandEdge, 5 * u)
    assertEquals(collected.exploration.surveys, studied.exploration.surveys)
    assertEquals(collected.basicResources.knowledge.amount, studied.basicResources.knowledge.amount)
  }

  test("a second scout study cannot reserve an already active site") {
    val initial = colony.copy(exploration = ExplorationData(revealedSites = 6))
    val sent = ExplorationMap.scout(initial, WoodlandEdge.id, 1)
    assertEquals(ExplorationMap.scout(sent, WoodlandEdge.id, 1), sent)
    assert(ExplorationMap.canScout(sent, NectarMeadow.id, 1))
  }

  test("seed collection requires a studied meadow and the seed discovery") {
    val initial = colony.copy(exploration = ExplorationData(revealedSites = 2))
    assert(!SeedGathering.isUnlocked(initial.exploration, initial.ants, initial.nestAttributes))
    val found = (1 to 2).foldLeft(initial) { (data, _) =>
      val sent = ExplorationMap.scout(data, NectarMeadow.id, 1)
      ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    }
    assert(SeedCache.found(found.exploration))
    assert(ExplorationHelper.canStart(found, 10 * u, SeedGathering))
    assert(!ExplorationHelper.canStart(found, 9 * u, SeedGathering))
  }

  test("seed gathering preview matches guaranteed protein without requiring carcass slots") {
    val initial = colony.copy(exploration = MapFixture.explored.copy(revealedSites = 6, surveys = Map(NectarMeadow.id -> 3)))
    val sent = ExplorationHelper.start(initial, 10 * u, SeedGathering)
    val party = sent.exploration.explorationParties.head
    val expected = ExplorationProgression.partyReward(10 * u, 10 * u, 0)
    val result = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(result.basicResources.protein.amount, expected)
    assert(result.exploration.proteinDiscovered)
    assert(result.exploration.discoveredCarcasses.isEmpty && result.exploration.carcasses.isEmpty)
    assert(ExplorationHelper.summary(initial, 10 * u, SeedGathering).contains(s"${UINumbersUtils.prettyNumberStr(expected)} protein"))
    assert(ExplorationHelper.outcomePreview(initial.exploration, 10 * u, SeedGathering).contains("Protein guaranteed"))
    val nearlyFull = sent.copy(basicResources = sent.basicResources.copy(protein = BasicResource(99 * u, 100 * u)))
    assertEquals(ExplorationHelper.exploreBonus(nearlyFull, party).basicResources.protein.amount, 100L * u)
  }

  test("aphid task is restored immediately on research and reload, but never before research") {
    val discovered = colony.copy(exploration = colony.exploration.copy(surveys = Map(WoodlandEdge.id -> 5)))
    assert(!UpgradeVisibility.reconcile(discovered).ants.tasks.exists(_._1 == AntTask.AphidTender))
    val researched = UpgradeVisibility.reconcile(discovered.copy(upgrades = discovered.upgrades.unlock(AphidTending)))
    assertEquals(researched.ants.taskWorkers(AntTask.AphidTender), 0L)
    assert(researched.ants.defaultTaskChoices.contains(AntTask.AphidTender))
    val loaded = AllDataForSave.fromAllData(researched.copy(ants = discovered.ants)).toAllData
    assert(loaded.ants.defaultTaskChoices.contains(AntTask.AphidTender))
    assertEquals(UpgradeVisibility.reconcile(researched).ants, researched.ants)
  }

  test("aphid workers participate in the default worker pool without duplicating ants") {
    val ready = UpgradeVisibility.reconcile(colony.copy(
      nestAttributes = colony.nestAttributes.copy(chambers = colony.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(40))),
      upgrades = colony.upgrades.unlock(AphidTending, DefaultWorkers)))
    val selected = DefaultWorkerAssignment.select(ready, Some(AntTask.AphidTender))
    assertEquals(selected.ants.taskWorkers(AntTask.AphidTender), 40L * u)
    val sent = ExplorationHelper.start(selected, 5 * u, NectarMeadow)
    assertEquals(sent.ants.taskWorkers(AntTask.AphidTender), 35L * u)
    assertEquals(sent.ants.availableWorkersCount, 35L * u)
    val returned = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(returned.ants.taskWorkers(AntTask.AphidTender), 40L * u)
    assertEquals(returned.ants.idleWorkersCount, 0L)
  }

  test("survey progress, research and assignments persist; saves without surveys start undiscovered") {
    val data = UpgradeVisibility.reconcile(colony.copy(exploration = colony.exploration.copy(surveys = Map(WoodlandEdge.id -> 3, NectarMeadow.id -> 2))))
    val loaded = AllDataForSave.fromAllData(data).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.exploration, data.exploration)
    val discovered = loaded
    assert(WildAphids.found(discovered.exploration))
    assertEquals("{}".fromJson[ExplorationData].toOption.get.surveys, Map.empty[String, Int])
    val task = AntTask.AphidTender: AntTask
    assertEquals(task.toJson.fromJson[AntTask], Right(task))
  }
  test("discovery indicators identify only destinations with unfinished discoveries") {
    val empty = ExplorationData()
    assert(!hasUnfound(empty, Surroundings))
    assert(!hasUnfound(empty, CollectResin))
    DiscoveryProgression.all.foreach { discovery =>
      val destination = ExplorationDestination.all.find(_.id == discovery.destination).get
      assert(hasUnfound(empty, destination))
      assert(hasUnfound(empty.copy(surveys = Map(discovery.destination -> (discovery.trips - 1))), destination))
      assert(!hasUnfound(empty.copy(surveys = Map(discovery.destination -> discovery.trips)), destination))
      assert(!hasUnfound(empty.copy(surveys = Map(discovery.destination -> (discovery.trips + 1))), destination))
    }
  }

}
