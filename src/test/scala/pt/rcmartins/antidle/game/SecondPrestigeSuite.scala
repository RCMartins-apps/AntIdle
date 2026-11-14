package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class SecondPrestigeSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(ants = AntsData(workers = 30 * u, scouts = 3 * u, daughterQueens = u,
      lineage = Lineage(1, 0L, true), tasks = Seq(AntTask.SugarCollector -> (30 * u)), defaultTask = Some(AntTask.SugarCollector)),
      nestAttributes = d.nestAttributes.copy(aphids = 10L, chambers = d.nestAttributes.chambers.copy(aphidChamber = Chamber.AphidChamber(1))),
      exploration = MapFixture.explored,
      upgrades = d.upgrades.unlock(AphidTending, DefaultWorkers),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)),
      basicResources = d.basicResources.copy(sugar = BasicResource(10000 * u, 100000 * u),
        resin = BasicResource(100 * u, 1000 * u), knowledge = BasicResource(5000 * u, Long.MaxValue)))
  }
  def finish(d: AllData): AllData = ExplorationHelper.exploreBonus(d, d.exploration.explorationParties.head)
  test("founding preview spends earned DNA atomically and permanently inherits the next range") {
    val d = colony
    val plan = Prestige.plan(d, Seq(Prestige.BeyondWoodland.id)).get
    assertEquals(plan, Lineage(2, 0L, true, true))
    assertEquals(d.ants.lineage.dna, 0L)
    val next = Prestige.found(d, Seq(Prestige.BeyondWoodland.id))
    assertEquals(next.ants.lineage, plan)
    assertEquals(next.upgrades, UpgradesData.initial)
    assertEquals(next.nestAttributes, NestAttributes.initial)
    assertEquals(next.exploration.revealedSites, 0)
    assertEquals(ExplorationMap.availableSites(next.ants).size, 9)
    val loaded = AllDataForSave.fromAllData(next).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assert(loaded.ants.lineage.beyondWoodland)
    assertEquals(Prestige.found(loaded), loaded)
    val again = Prestige.found(next.copy(ants = next.ants.copy(daughterQueens = u)))
    assert(again.ants.lineage.beyondWoodland)
    assertEquals(again.ants.lineage.dna, 1L)
  }
  test("selection rejects unknown, duplicate, unaffordable, owned and premature Understandings without reset") {
    val first = colony.copy(ants = colony.ants.copy(lineage = Lineage()))
    assertEquals(Prestige.found(first, Seq(Prestige.BeyondWoodland.id)), first)
    val bad = List(Seq("unknown"), Seq("shared-paths"), Seq("beyond-woodland", "beyond-woodland"))
    bad.foreach(ids => assertEquals(Prestige.found(colony, ids), colony))
    val noQueen = colony.copy(ants = colony.ants.copy(daughterQueens = 0L))
    assertEquals(Prestige.found(noQueen, Seq("beyond-woodland")), noQueen)
    val savedDna = colony.copy(ants = colony.ants.copy(lineage = Lineage(1, 1L)))
    assertEquals(Prestige.plan(savedDna, Seq("beyond-woodland", "shared-paths")).get, Lineage(2, 0L, true, true))
    assert(Prestige.plan(savedDna.copy(ants = savedDna.ants.copy(lineage = Lineage(1))), Seq("shared-paths", "beyond-woodland")).isEmpty)
  }
  test("old lineage saves default to the original range and the first six reveals are unchanged") {
    val old = "{\"foundings\":1,\"dna\":0,\"sharedPaths\":true}".fromJson[Lineage].toOption.get
    assert(!old.beyondWoodland)
    assertEquals(ExplorationMap.availableSites(AntsData(lineage = old)), ExplorationMap.sites)
    assertEquals(ExplorationMap.scout(colony, "surroundings", 1), colony)
    val forged = colony.copy(exploration = colony.exploration.copy(revealedSites = 9))
    assert(!ExplorationMap.canScout(forged, "aphid-thicket", 1))
    assert(!UpgradeResearch.isVisible(forged.copy(upgrades = forged.upgrades.modify(AphidShelters)(_.copy(show = true))), AphidShelters))
  }
  test("new range reveals exactly three deterministic outer sites without restarting the inner map") {
    var d = colony.copy(ants = colony.ants.copy(lineage = Lineage(2, 0L, true, true)))
    for ((site, i) <- ExplorationMap.outerSites.zipWithIndex) {
      d = finish(ExplorationMap.scout(d, "surroundings", 1))
      assertEquals(d.exploration.revealedSites, 7 + i)
      assert(ExplorationMap.revealed(d.exploration, site.id))
      assertEquals(site.distance, 2)
    }
    assertEquals(ExplorationMap.scout(d, "surroundings", 1), d)
    assert(ExplorationMap.sites.forall(s => ExplorationMap.studies(d.exploration, s.id) == 3))
  }
  test("outer studies are finite, unlock research at three studies and fund all three upgrades") {
    var d = colony.copy(ants = colony.ants.copy(lineage = Lineage(2, 0L, true, true)),
      exploration = colony.exploration.copy(revealedSites = 9),
      basicResources = colony.basicResources.copy(knowledge = BasicResource(0L, Long.MaxValue)))
    val research = List(AphidShelters, TrailLogistics, CoordinatedExpeditions)
    for ((site, upgrade) <- ExplorationMap.outerSites.zip(research)) {
      assert(!upgrade.shouldShow(d))
      for (study <- 1 to 3) {
        d = finish(ExplorationMap.scout(d, site.id, 1))
        assertEquals(upgrade.shouldShow(d), study == 3)
      }
      assertEquals(ExplorationMap.knowledgeRemaining(d.exploration, site.id), 0L)
      assertEquals(ExplorationMap.scout(d, site.id, 1), d)
      assert(ExplorationMap.exhausted(d.exploration, site.id))
    }
    assertEquals(d.basicResources.knowledge.amount, ExplorationMap.outerSites.map(_.knowledge).sum)
    val before = d
    research.foreach { upgrade =>
      assert(UpgradeResearch.canPurchase(d, upgrade))
      d = UpgradeResearch.purchase(d, upgrade)
      assertEquals(UpgradeResearch.purchase(d, upgrade), d)
    }
    assertEquals(AphidHusbandry.housing(d.nestAttributes), 20L)
    assertEquals(d.nestAttributes.aphids, 10L)
    assertEquals(AphidHusbandry.capacity(d.nestAttributes), u)
    assertEquals(ExplorationProgression.maxParties(d.exploration, d.upgrades), ExplorationProgression.maxParties(before.exploration, before.upgrades) + 1)
    assertEquals(PheromoneTrails.efficiency(d.upgrades), 75L)
    val loaded = AllDataForSave.fromAllData(d).toAllData
    assertEquals(loaded.nestAttributes.aphidPlacesPerLevel, 20)
    assertEquals(loaded.nestAttributes.aphids, 10L)
  }
  test("Trail Logistics changes actual harvesting and charges proportionate supplies") {
    val base = colony.copy(upgrades = colony.upgrades.unlock(TrailLogistics))
    val five = PheromoneTrails.adjust(base, ExplorationDestination.NectarMeadow.id, 5 * u)
    val assigned = PheromoneTrails.adjust(five, ExplorationDestination.NectarMeadow.id, 5 * u)
    val (gain, cost) = PheromoneTrails.nominal(ExplorationDestination.NectarMeadow, 10 * u, assigned.upgrades)
    val (oldGain, oldCost) = PheromoneTrails.nominal(ExplorationDestination.NectarMeadow, 10 * u, colony.upgrades)
    assertEquals(gain * 4L, oldGain * 5L)
    assert(Math.abs(cost * 4L - oldCost * 5L) <= 4L)
    val next = PheromoneTrails.tick(assigned)
    assertEquals(next.basicResources.sugar.amount - assigned.basicResources.sugar.amount, gain - cost)
    assertEquals(next.exploration.depleted(ExplorationDestination.NectarMeadow.id), gain)
    assertEquals(PheromoneTrails.guides(assigned.ants, assigned.exploration), u)
  }
  test("Coordinated Expeditions permits one more real departure without relaxing crew costs") {
    def launch(d: AllData): AllData = ExplorationHelper.start(d, 5 * u, ExplorationDestination.NectarMeadow)
    val oldFull = launch(launch(colony))
    assertEquals(launch(oldFull), oldFull)
    val expanded = oldFull.copy(upgrades = oldFull.upgrades.unlock(CoordinatedExpeditions))
    val sent = launch(expanded)
    assertEquals(sent.exploration.explorationParties.size, 3)
    assertEquals(sent.basicResources.sugar.amount, expanded.basicResources.sugar.amount - 50L * u)
    assertEquals(launch(sent), sent)
  }

}
