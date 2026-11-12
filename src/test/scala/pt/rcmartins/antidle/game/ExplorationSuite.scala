package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination._
import pt.rcmartins.antidle.game.saves.{AllDataForSave, SaveLoad}
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import zio.json._

class ExplorationSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(world = d.world.copy(currentTick = 0L, targetTick = 0L, saveSeed = 42L),
      ants = AntsData(workers = 50L * u, scouts = 3L * u),
      basicResources = d.basicResources.copy(sugar = BasicResource(10000L * u, 30000L * u)),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)))
  }
  def finish(d: AllData): AllData = {
    val party = d.exploration.explorationParties.head
    ExplorationHelper.exploreBonus(d.copy(world = d.world.copy(currentTick = party.targetTick)), party)
  }
  def ready: AllData = colony.copy(exploration = MapFixture.explored)

  test("scouts reveal exactly six fixed neighboring sites without workers or random results") {
    assertEquals(ExplorationMap.sites.map(s => (s.q, s.r)).distinct.size, 6)
    assert(ExplorationMap.sites.forall(_.distance == 1))
    val d = colony.copy(ants = colony.ants.copy(workers = 0L))
    val mapped = (1 to 6).foldLeft(d) { (state, count) =>
      val sent = ExplorationMap.scout(state, "surroundings", 1)
      assertEquals(sent.exploration.revealedSites, count - 1)
      assertEquals(sent.exploration.explorationParties.head.amountWorkers, 0L)
      assertEquals(ScoutProgression.available(sent.ants, sent.exploration), 2L * u)
      assertEquals(ExplorationMap.scout(sent, "surroundings", 1), sent)
      val returned = finish(sent)
      assertEquals(ExplorationMap.finishScout(returned, sent.exploration.explorationParties.head), returned)
      assertEquals(returned.exploration.revealedSites, count)
      assert(ExplorationMap.revealed(returned.exploration, ExplorationMap.sites(count - 1).id))
      returned
    }
    assertEquals(ExplorationMap.scout(mapped, "surroundings", 1), mapped)
    assertEquals(mapped.ants.workers, 0L)
  }
  test("workers cannot scout; unknown sites and invalid crew counts cannot launch") {
    val noScouts = colony.copy(ants = colony.ants.copy(scouts = 0L))
    assertEquals(ExplorationMap.scout(noScouts, "surroundings", 1), noScouts)
    assertEquals(ExplorationMap.scout(colony, "fallen-pine", 1), colony)
    for (count <- List(0, -1, 4)) assertEquals(ExplorationMap.scout(colony, "surroundings", count), colony)
    assertEquals(ExplorationMap.scout(colony, "unknown", 1), colony)
  }
  test("larger scout crews cost more supplies and speed missions without multiplying finite knowledge") {
    val revealed = colony.copy(exploration = ExplorationData(revealedSites = 1))
    val one = ExplorationMap.scout(revealed, "woodland-edge", 1)
    val three = ExplorationMap.scout(revealed, "woodland-edge", 3)
    assertEquals(one.exploration.explorationParties.head.targetTick, 900L)
    assertEquals(three.exploration.explorationParties.head.targetTick, 300L)
    assertEquals(one.basicResources.sugar.amount - three.basicResources.sugar.amount, 20L * u)
    assertEquals(finish(one).basicResources.knowledge.amount, finish(three).basicResources.knowledge.amount)
    assertEquals(ScoutProgression.upkeepPerTick(three.ants, three.exploration), ScoutProgression.upkeepPerTick(revealed.ants, revealed.exploration))
    assertEquals(ExplorationMap.scout(three, "woodland-edge", 1), three)
  }
  test("each site's surveys diminish, exhaust, and provide the same deterministic total for every seed") {
    for (seed <- List(42L, 137L)) {
      var d = colony.copy(world = colony.world.copy(saveSeed = seed), exploration = ExplorationData(revealedSites = 6))
      ExplorationMap.sites.foreach { site =>
        val before = d.basicResources.knowledge.amount
        val rewards = (1 to 3).map { _ =>
          val previous = d.basicResources.knowledge.amount
          d = finish(ExplorationMap.scout(d, site.id, 1))
          d.basicResources.knowledge.amount - previous
        }
        assert(rewards.sliding(2).forall(pair => pair(0) > pair(1)))
        assertEquals(d.basicResources.knowledge.amount - before, site.knowledge)
        assertEquals(ExplorationMap.knowledgeRemaining(d.exploration, site.id), 0L)
        assertEquals(ExplorationMap.scout(d, site.id, 1), d)
      }
      assertEquals(d.basicResources.knowledge.amount, ExplorationMap.sites.map(_.knowledge).sum)
    }
  }
  test("the safe first ring funds all research, supplies and both outpost extensions with a margin") {
    val research = UpgradeType.all.map(_.cost.knowledge).sum
    val nest = NestAttributes.initial.copy(dryClearingOutpost = true)
    val extensions = (0 until OutpostProgression.MaxExtensions).map(n => OutpostProgression.expansionCost(nest.copy(dryClearingExtensions = n)).knowledge).sum
    assert(ExplorationMap.sites.map(_.knowledge).sum > research + OutpostProgression.cost.knowledge + extensions)
    assert(ExplorationMap.deposits("collect-resin") >= RoyalNurseryProgression.buildCost.resin)
    assert(ExplorationMap.deposits("scavenge-water") > OutpostProgression.cost.water + RoyalNurseryProgression.buildCost.water)
  }
  test("workers collect predictable resources but never knowledge or new surveys") {
    val sent = ExplorationHelper.start(ready, 5L * u, RottenLog)
    assertEquals(sent.exploration.explorationParties.size, 1)
    val cargo = sent.exploration.explorationParties.head.cargo
    assertEquals(cargo.protein, 25L * u)
    val returned = finish(sent)
    assertEquals(returned.basicResources.protein.amount, 25L * u)
    assertEquals(returned.basicResources.knowledge.amount, 0L)
    assertEquals(returned.exploration.surveys, ready.exploration.surveys)
    assertEquals(ExplorationHelper.exploreBonus(returned, sent.exploration.explorationParties.head), returned)
    assertEquals(returned.ants.idleWorkersCount, 50L * u)
  }
  test("workers do not require scouts for collection, but need a revealed studied site and a valid crew") {
    val d = ready.copy(ants = ready.ants.copy(scouts = 0L))
    assert(ExplorationHelper.canStart(d, 5L * u, RottenLog))
    assert(!ExplorationHelper.canStart(d, 4L * u, RottenLog))
    assert(!ExplorationHelper.canStart(d, 38L * u, RottenLog))
    assert(!ExplorationHelper.canStart(d, u + 1L, WoodlandEdge))
    val hidden = d.copy(exploration = ExplorationData())
    assertEquals(ExplorationHelper.start(hidden, 5L * u, RottenLog), hidden)
  }
  test("finite deposits reserve cargo on departure and return storage overflow to the site") {
    val remaining = 5L * u
    val d = ready.copy(exploration = ready.exploration.copy(depleted = Map("collect-resin" -> (ExplorationMap.deposits("collect-resin") - remaining))))
    val sent = ExplorationHelper.start(d, 10L * u, CollectResin)
    assertEquals(ExplorationMap.remaining(sent.exploration, "collect-resin"), 0L)
    assert(!ExplorationHelper.canStart(sent, 10L * u, CollectResin))
    val full = sent.copy(basicResources = sent.basicResources.copy(resin = BasicResource(200L * u, 200L * u)))
    val returned = finish(full)
    assertEquals(ExplorationMap.remaining(returned.exploration, "collect-resin"), remaining)
    assertEquals(returned.statistics.resinU.total, 0L)
  }
  test("concurrent hauls never overbook deposits or destination storage") {
    val d = ready.copy(basicResources = ready.basicResources.copy(protein = BasicResource(95L * u, 100L * u)))
    val first = ExplorationHelper.start(d, 5 * u, WoodlandEdge)
    assertEquals(first.exploration.explorationParties.head.cargo.protein, 5L * u)
    assert(!ExplorationHelper.canStart(first, 5L * u, RottenLog))
    assertEquals(finish(first).basicResources.protein.amount, 100L * u)
  }
  test("identical simultaneous hauls each return exactly once without stranding workers") {
    val first = ExplorationHelper.start(ready, 5 * u, WoodlandEdge)
    val sent = ExplorationHelper.start(first, 5 * u, WoodlandEdge)
    assertEquals(sent.exploration.explorationParties.size, 2)
    assertEquals(sent.exploration.explorationParties.head, sent.exploration.explorationParties(1))
    val once = finish(sent)
    assertEquals(once.exploration.explorationParties.size, 1)
    val twice = finish(once)
    assertEquals(twice.ants.idleWorkersCount, 50L * u)
    assertEquals(twice.basicResources.protein.amount, 25L * u)
    assert(twice.exploration.explorationParties.isEmpty)
  }
  test("exhausted sites persist without missions; managed discoveries and research remain available") {
    val used = ExplorationMap.sites.head
    val d = ready.copy(exploration = ready.exploration.copy(depleted = Map("woodland-edge" -> ExplorationMap.deposits("woodland-edge"))))
    assert(ExplorationMap.exhausted(d.exploration, used.id))
    assert(ExplorationMap.revealed(d.exploration, used.id))
    assert(DiscoveryProgression.WildAphids.found(d.exploration))
    assertEquals(ExplorationMap.scout(d, used.id, 1), d)
    assertEquals(ExplorationHelper.start(d, 5 * u, WoodlandEdge), d)
  }
  test("map state, finite cargo and scout reservations round-trip; obsolete saves are rejected") {
    val sent = ExplorationMap.scout(colony, "surroundings", 2)
    val loaded = SaveLoad.loadString(AllDataForSave.fromAllData(sent).toJson).get
    assertEquals(loaded.exploration.explorationParties, sent.exploration.explorationParties)
    assertEquals(loaded.exploration.revealedSites, sent.exploration.revealedSites)
    assertEquals(ScoutProgression.available(loaded.ants, loaded.exploration), u)
    assertEquals(SaveLoad.loadString(AllDataForSave.fromAllData(sent).copy(version = 6).toJson), None)
    assert(!AllDataForSave.fromAllData(sent).toJson.contains("experience"))
  }
  test("every useful nectar crew returns more sugar than its supplies cost without XP inflation") {
    for (count <- 5L to 50L by 5L) {
      val crew = count * u
      val cargo = ExplorationHelper.load(ready, crew, NectarMeadow)
      assert(cargo.sugar > ExplorationHelper.cost(crew, NectarMeadow).sugar, s"$count workers")
    }
  }
  test("collection rewards are linear from five through fifty workers") {
    val one = ExplorationProgression.partyReward(300L * u, 5L * u, 0)
    for (workers <- 5L to 50L by 5L)
      assertEquals(ExplorationProgression.partyReward(300L * u, workers * u, 0), one * workers / 5L)
  }
}
