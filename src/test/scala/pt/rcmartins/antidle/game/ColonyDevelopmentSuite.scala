package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination.NectarMeadow
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class ColonyDevelopmentSuite extends FunSuite {
  def colony(): AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 20 * u, scouts = u, tasks = Seq(AntTask.Explorer -> 0L)),
      exploration = MapFixture.explored.copy(revealedSites = 6),
      basicResources = initial.basicResources.copy(sugar = BasicResource(3000 * u, 10000 * u)),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(
        nurseryChamber = initial.nestAttributes.chambers.nurseryChamber.copy(level = 1))),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  test("meadow collection requires a revealed studied site and five workers, not scouts") {
    val ready = colony()
    val hidden = ready.copy(exploration = ExplorationData())
    assert(!ExplorationHelper.canStart(hidden, 5 * u, NectarMeadow))
    assert(!ExplorationHelper.canStart(ready, 4 * u, NectarMeadow))
    assert(ExplorationHelper.canStart(ready.copy(ants = ready.ants.copy(scouts = 0L)), 5 * u, NectarMeadow))
    assertEquals(ExplorationHelper.cost(5 * u, NectarMeadow), ActionCost(sugar = 50 * u, idleWorkers = 5 * u))
  }

  test("meadow previews match collection cargo without generating knowledge") {
    val sent = ExplorationHelper.start(colony(), 5 * u, NectarMeadow)
    val result = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(result.basicResources.sugar.amount, 3550L * u)
    assertEquals(result.basicResources.knowledge.amount, 0L)
    assertEquals(result.ants.idleWorkersCount, 20L * u)
    assert(ExplorationHelper.summary(colony(), 5 * u, NectarMeadow).contains("600 sugar"))
  }

  test("meadow returns through the tick engine, respects storage caps and releases scouts") {
    val sent = ExplorationHelper.start(colony(), 5 * u, NectarMeadow)
    val capped = sent.copy(basicResources = sent.basicResources.copy(sugar = BasicResource(2900 * u, 3000 * u)),
      world = sent.world.copy(currentTick = NectarMeadow.duration - 1L))
    val result = TickUpdater.updateAllData(capped, None)
    assert(result.exploration.explorationParties.isEmpty)
    assertEquals(ScoutProgression.available(result.ants, result.exploration), u)
    assert(result.basicResources.sugar.amount <= 3000L * u)
    assert(result.basicResources.sugar.amount > 2900L * u)
    assertEquals(result.ants.idleWorkersCount, 20L * u)
  }

  test("Reinforced Mandibles changes actual tunneling output by fifty percent") {
    val initial = colony().copy(ants = AntsData(workers = 5 * u, tasks = Seq(AntTask.Tunneler -> (5 * u))))
    val plain = TickUpdater.updateAllData(initial, None)
    val upgraded = TickUpdater.updateAllData(initial.copy(upgrades = initial.upgrades.unlock(ReinforcedMandibles)), None)
    assertEquals(ColonyDevelopment.tunnelingPerWorkerTick(initial.upgrades) * TicksPerSecond, u)
    assertEquals(upgraded.basicResources.tunnelingSpace.amount - initial.basicResources.tunnelingSpace.amount, 15000L)
    assertEquals(plain.basicResources.tunnelingSpace.amount - initial.basicResources.tunnelingSpace.amount, 10000L)
  }

  test("Brood Care changes the exact worker and scout maturity boundaries, including existing eggs") {
    val initial = colony().copy(ants = AntsData(eggsAndLarvae = Seq(AntBrood.Egg(0L), AntBrood.ScoutEgg(0L))))
    val upgraded = initial.copy(upgrades = initial.upgrades.unlock(BroodCare))
    def grow(data: AllData, tick: Long): AllData = ScoutProgression.growBrood(data.copy(world = data.world.copy(currentTick = tick)))
    assertEquals(grow(initial, 40L).ants.workers, 0L)
    assertEquals(grow(upgraded, 39L).ants.workers, 0L)
    assertEquals(grow(upgraded, 40L).ants.workers, u)
    assertEquals(grow(upgraded, 119L).ants.scouts, 0L)
    assertEquals(grow(upgraded, 120L).ants.scouts, u)
    assertEquals(grow(initial, 120L).ants.scouts, 0L)
    assertEquals(grow(initial, 150L).ants.scouts, u)
  }

  test("new upgrades require mapped sites and a nursery where specified") {
    val ready = colony()
    assert(!ReinforcedMandibles.shouldShow(ready.copy(exploration = ready.exploration.copy(revealedSites = 1))))
    assert(ReinforcedMandibles.shouldShow(ready.copy(exploration = ready.exploration.copy(revealedSites = 2))))
    assert(!BroodCare.shouldShow(ready.copy(exploration = ready.exploration.copy(revealedSites = 2))))
    assert(BroodCare.shouldShow(ready))
    assert(!BroodCare.shouldShow(ready.copy(nestAttributes = NestAttributes.initial)))
  }

  test("new colony upgrades and a meadow party survive save reload without changing rewards") {
    val sent = ExplorationHelper.start(colony().copy(upgrades = UpgradesData.initial.unlock(ReinforcedMandibles, BroodCare)), 5 * u, NectarMeadow)
    val loaded = AllDataForSave.fromAllData(sent).toAllData
    assert(loaded.upgrades(ReinforcedMandibles).unlocked && loaded.upgrades(BroodCare).unlocked)
    assertEquals(ExplorationDestination.forParty(loaded.exploration.explorationParties.head), NectarMeadow)
    val party = loaded.exploration.explorationParties.head
    assertEquals(ExplorationHelper.exploreBonus(loaded, party).basicResources, ExplorationHelper.exploreBonus(sent, party).basicResources)
  }
  test("chamber previews include derived nursery places and actual laying interval reduction") {
    val d = colony()
    val nursery = ChamberBonuses.forDisplay(Chamber.ChamberType.Nursery, d.ants, d.nestAttributes, d.upgrades)
    val built = ChamberBonuses.completeUpgrade(d, Chamber.ChamberType.Nursery)
    assertEquals(nursery.maxEggs / u, (BroodProgression.capacity(built.nestAttributes) - BroodProgression.capacity(d.nestAttributes)).toLong)
    assertEquals(nursery.broodNurseSlots, 5)
    assertEquals(built.nestAttributes.maxEggs, d.nestAttributes.maxEggs)
    val queen = ChamberBonuses.forDisplay(Chamber.ChamberType.Queen, d.ants, d.nestAttributes, d.upgrades)
    val expanded = ChamberBonuses.completeUpgrade(d, Chamber.ChamberType.Queen)
    assertEquals(queen.layingIntervalReduction,
      BroodProgression.interval(d.ants, d.nestAttributes) - BroodProgression.interval(d.ants, expanded.nestAttributes))
  }

}
