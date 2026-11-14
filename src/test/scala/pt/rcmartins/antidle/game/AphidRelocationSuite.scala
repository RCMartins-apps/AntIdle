package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class AphidRelocationSuite extends FunSuite {
  import AphidRelocation._
  def colony: AllData = {
    val d = AllData.initial
    DefaultWorkerAssignment.normalize(d.copy(
      ants = AntsData(workers = 30 * u, scouts = 2 * u,
        tasks = Seq(AntTask.SugarCollector -> (30 * u), AntTask.AphidTender -> 0L), defaultTask = Some(AntTask.SugarCollector)),
      nestAttributes = d.nestAttributes.copy(chambers = d.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(3))),
      exploration = MapFixture.explored,
      upgrades = d.upgrades.unlock(AphidTending, DefaultWorkers),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)),
      basicResources = d.basicResources.copy(sugar = BasicResource(500 * u, 1000 * u), protein = BasicResource(100 * u, 1000 * u))))
  }
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("the first group is free once, and later empty chambers do not generate aphids") {
    val d = colony
    assertEquals(d.nestAttributes.aphids, 10L)
    assertEquals(AphidHusbandry.capacity(d.nestAttributes), u)
    assertEquals(load(d).nestAttributes.aphids, 10L)
    val expanded = DefaultWorkerAssignment.normalize(ChamberBonuses.completeUpgrade(d, Chamber.ChamberType.Aphid))
    assertEquals(expanded.nestAttributes.aphids, 10L)
    assertEquals(AphidHusbandry.housing(expanded.nestAttributes), 40L)
    assertEquals(AphidHusbandry.reconcile(expanded), expanded)
  }
  test("relocation reserves workers, scout, supplies and ten housing places without collecting resources") {
    val d = colony
    val sent = start(d)
    val party = sent.exploration.explorationParties.head
    assertEquals(party.aphids, 10L)
    assertEquals(party.targetTick - party.startingTick, Duration)
    assertEquals(sent.ants.taskWorkers(AntTask.SugarCollector), 25 * u)
    assertEquals(ScoutProgression.available(sent.ants, sent.exploration), u)
    assertEquals(free(sent.nestAttributes, sent.exploration), 10L)
    assertEquals(sent.basicResources.sugar.amount, d.basicResources.sugar.amount - Cost.sugar)
    assertEquals(sent.exploration.depleted, d.exploration.depleted)
    assertEquals(sent.nestAttributes.aphids, 10L)
    val returned = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(returned.nestAttributes.aphids, 20L)
    assertEquals(returned.ants.taskWorkers(AntTask.SugarCollector), 30 * u)
    assertEquals(ScoutProgression.available(returned.ants, returned.exploration), 2 * u)
    assertEquals(returned.basicResources, sent.basicResources)
    assertEquals(returned.statistics.explorationsCompleted.thisRun, 1)
    assertEquals(ExplorationHelper.exploreBonus(returned, party), returned)
  }
  test("concurrent relocations reserve housing and survive reload without duplicating aphids") {
    val two = start(start(colony))
    assertEquals(reserved(two.exploration), 20L)
    assert(!canStart(two))
    val restored = load(two)
    val arrived = restored.exploration.explorationParties.foldLeft(restored)(ExplorationHelper.exploreBonus)
    assertEquals(arrived.nestAttributes.aphids, 30L)
    assertEquals(arrived.exploration.explorationParties, Nil)
    assertEquals(load(arrived).nestAttributes.aphids, 30L)
    assert(!canStart(arrived))
  }
  test("relocation validates research, discovery, housing, workers, scouts, supplies and mission slots") {
    val d = colony
    val blocked = List(
      d.copy(upgrades = UpgradesData.initial),
      d.copy(exploration = d.exploration.copy(surveys = Map.empty)),
      d.copy(nestAttributes = d.nestAttributes.copy(aphids = 30L)),
      d.copy(ants = d.ants.copy(workers = 4 * u, tasks = Seq(AntTask.SugarCollector -> (4 * u)))),
      d.copy(ants = d.ants.copy(scouts = 0L)),
      d.copy(basicResources = d.basicResources.copy(sugar = BasicResource(0L, 1000 * u))),
      start(start(d)))
    blocked.foreach(x => { assert(!canStart(x)); assertEquals(start(x), x) })
  }
  test("wild aphids stay accessible after insects and knowledge are exhausted") {
    val d = colony.copy(exploration = colony.exploration.copy(depleted = ExplorationMap.deposits))
    assertEquals(ExplorationMap.knowledgeRemaining(d.exploration, "woodland-edge"), 0L)
    assert(!ExplorationMap.exhausted(d.exploration, "woodland-edge"))
    assert(canStart(d))
    val sent = start(d)
    val returned = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(returned.basicResources.knowledge, d.basicResources.knowledge)
    assertEquals(returned.exploration.depleted, d.exploration.depleted)
  }
  test("populating housing increases usable tenders, not the output or upkeep of each tender") {
    val d = DefaultWorkerAssignment.select(colony, Some(AntTask.AphidTender))
    assertEquals(d.ants.taskWorkers(AntTask.AphidTender), u)
    val sent = start(d)
    val returned = finish(sent, sent.exploration.explorationParties.head)
    assertEquals(returned.ants.taskWorkers(AntTask.AphidTender), 2 * u)
    assertEquals(AphidHusbandry.productionPerTick(returned.ants, returned.nestAttributes, returned.upgrades, 100 * u), 3200L)
    assertEquals(AphidHusbandry.proteinPerTick(returned.ants, returned.nestAttributes, returned.upgrades, 100 * u), 40L)
  }
  test("legacy saves populate existing chambers once; new empty housing remains empty after reload") {
    val d = colony.copy(nestAttributes = colony.nestAttributes.copy(aphids = 30L))
    val old = AllDataForSave.fromAllData(d).toJson.replace(",\"aphids\":30", "").replace(",\"aphidsInitialized\":true", "")
    val migrated = old.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(migrated.nestAttributes.aphids, 30L)
    assert(migrated.nestAttributes.aphidsInitialized)
    val expanded = DefaultWorkerAssignment.normalize(ChamberBonuses.completeUpgrade(migrated, Chamber.ChamberType.Aphid))
    assertEquals(load(expanded).nestAttributes.aphids, 30L)
    assertEquals(AphidHusbandry.housing(load(expanded).nestAttributes), 40L)
  }
}
