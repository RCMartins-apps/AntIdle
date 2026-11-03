package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import zio.json._

class ExplorationSuite extends FunSuite {
  def colony(xp: Long = 0L): AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 100 * u, scouts = 2 * u, tasks = Seq(AntTask.Explorer -> 0L)),
      basicResources = initial.basicResources.copy(
        sugar = BasicResource(10000 * u, 10000 * u), knowledge = BasicResource(0L, 10000 * u)),
      exploration = ExplorationData(experience = xp),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  test("gentle thresholds retain total XP and unlock milestones") {
    assertEquals((1 to 5).map(ExplorationProgression.threshold), Seq(0L, 100L, 300L, 600L, 1000L))
    assertEquals(ExplorationProgression.threshold(20), 19000L)
    assertEquals(ExplorationProgression.level(999L), 4)
    assertEquals(ExplorationProgression.level(1000L), 5)
    assertEquals(ExplorationProgression.level(52428700L), 20)
    assertEquals(ExplorationProgression.maxParties(999L), 1)
    assertEquals(ExplorationProgression.maxParties(1000L), 2)
  }

  test("party bonuses grow with diminishing returns and cap at 37 workers") {
    assertEquals(List(1L, 5L, 17L, 37L, 1000L).map(n => ExplorationProgression.partyPercent(n * u)),
      List(100L, 200L, 300L, 400L, 400L))
    val growth = (1L to 37L).map(n => ExplorationProgression.partyPercent(n * u))
    assert(growth.sliding(2).forall { case Seq(a, b) => b >= a; case _ => true })
  }

  test("destination requirements are enforced by the engine") {
    assert(!ExplorationHelper.canStart(colony(299), 5 * u, RottenLog))
    assert(!ExplorationHelper.canStart(colony(300), 4 * u, RottenLog))
    assert(ExplorationHelper.canStart(colony(300), 5 * u, RottenLog))
    val noScouts = colony(300).copy(ants = colony().ants.copy(scouts = 0L))
    assert(!ExplorationHelper.canStart(noScouts, 5 * u, RottenLog))
    assert(!ExplorationHelper.canStart(noScouts, u, WoodlandEdge))
    assert(ExplorationHelper.canStart(noScouts, u, Surroundings))
    assert(!ExplorationHelper.canStart(colony(), u + 1L, Surroundings))
    assert(!ExplorationHelper.canStart(colony(), 101 * u, Surroundings))
    val poor = colony().copy(basicResources = colony().basicResources.copy(sugar = BasicResource(0L, 1000 * u)))
    assertEquals(ExplorationHelper.start(poor, u, Surroundings), poor)
  }

  test("level five permits two parties, reserves scouts and blocks a third") {
    val low = ExplorationHelper.start(colony(999), u, Surroundings)
    assert(!ExplorationHelper.canStart(low, u, Surroundings))
    val first = ExplorationHelper.start(colony(1000), 5 * u, RottenLog)
    val second = ExplorationHelper.start(first, u, WoodlandEdge)
    assertEquals(second.exploration.explorationParties.size, 2)
    assertEquals(ScoutProgression.available(second.ants, second.exploration), 0L)
    assertEquals(second.ants.idleWorkersCount, 94L * u)
    assertEquals(ExplorationHelper.start(second, u, Surroundings), second)
  }

  test("supplies charge sugar, enforce level gates and never exceed rank three") {
    assertEquals(ExplorationProgression.upgradeSupplies(colony(99)).exploration.suppliesLevel, 0)
    val first = ExplorationProgression.upgradeSupplies(colony(100).copy(basicResources = colony().basicResources.copy(knowledge = BasicResource(1000 * u, Long.MaxValue))))
    assertEquals(first.exploration.suppliesLevel, 1)
    assertEquals(first.basicResources.sugar.amount, 9500L * u)
    assertEquals(ExplorationProgression.upgradeSupplies(first), first)
    val second = ExplorationProgression.upgradeSupplies(first.copy(exploration = first.exploration.copy(experience = 1000)))
    assertEquals(second.exploration.suppliesLevel, 2)
    assertEquals(second.basicResources.sugar.amount, 8000L * u)
    val third = ExplorationProgression.upgradeSupplies(second.copy(exploration = second.exploration.copy(experience = 2800)))
    assertEquals(third.exploration.suppliesLevel, 3)
    assertEquals(third.basicResources.sugar.amount, 3500L * u)
    assertEquals(ExplorationProgression.upgradeSupplies(third), third)
  }

  test("supplies are captured on departure and do not upgrade in-flight rewards") {
    val sent = ExplorationHelper.start(colony(300), 5 * u, RottenLog)
    val party = sent.exploration.explorationParties.head
    val improved = ExplorationProgression.upgradeSupplies(sent.copy(basicResources = sent.basicResources.copy(knowledge = BasicResource(1000 * u, Long.MaxValue))))
    assertEquals(improved.exploration.suppliesLevel, 1)
    assertEquals(ExplorationProgression.earned(party), 120L)
    val next = ExplorationHelper.start(colony(1000).copy(exploration = ExplorationData(experience = 1000, suppliesLevel = 1)), 5 * u, RottenLog)
    assertEquals(ExplorationProgression.earned(next.exploration.explorationParties.head), 144L)
  }

  test("rotten log grants knowledge and XP, returns workers, and never requires carcass slots") {
    val sent = ExplorationHelper.start(colony(300), 5 * u, RottenLog)
    val party = sent.exploration.explorationParties.head
    val result = ExplorationHelper.exploreBonus(sent, party)
    assertEquals(result.exploration.experience, 420L)
    assertEquals(result.basicResources.knowledge.amount, 60L * u)
    assertEquals(result.ants.idleWorkersCount, 100L * u)
    assert(result.exploration.discoveredCarcasses.isEmpty)
  }

  test("large woodland parties improve protein and reserves without raising retrieval costs") {
    val sent = ExplorationHelper.start(colony(), 5 * u, WoodlandEdge)
    val result = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    val carcass = result.exploration.discoveredCarcasses.head
    assertEquals(result.exploration.experience, 60L)
    assertEquals(carcass.proteinReward, 20L * u)
    assertEquals(carcass.totalFood, 480L * u)
    assertEquals(carcass.requiredWorkers, 10L * u)
    assertEquals(carcass.retrievalTicks, 100L)
  }

  test("the trip crossing a level receives its new resource bonus") {
    val sent = ExplorationHelper.start(colony(90), u, Surroundings)
    val result = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(result.exploration.experience, 100L)
    assertEquals(result.exploration.discoveredCarcasses.head.proteinReward, 62500L)
  }

  test("previews account for the level reached on return and match the reward") {
    assert(ExplorationHelper.preview(ExplorationData(experience = 90), u, Surroundings).contains("6.25 protein"))
    assert(ExplorationHelper.preview(ExplorationData(experience = 300), 5 * u, RottenLog).contains("60 knowledge"))
  }

  test("destination and supplies round-trip in current saves") {
    val party = ExplorationParty(5 * u, 0L, 300L, u, RottenLog.id, 2)
    assertEquals(party.toJson.fromJson[ExplorationParty], Right(party))
    val data = ExplorationData(explorationParties = List(party), experience = 300L, suppliesLevel = 2)
    assertEquals(data.toJson.fromJson[ExplorationData], Right(data))
  }
}
