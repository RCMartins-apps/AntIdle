package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ExplorationDestination.NectarMeadow
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class ColonyDevelopmentSuite extends FunSuite {
  def colony(xp: Long = 600L): AllData = {
    val initial = AllData.initial
    initial.copy(
      ants = AntsData(workers = 20 * u, scouts = u, tasks = Seq(AntTask.Explorer -> 0L)),
      exploration = ExplorationData(experience = xp),
      basicResources = initial.basicResources.copy(sugar = BasicResource(3000 * u, 10000 * u)),
      nestAttributes = initial.nestAttributes.copy(chambers = initial.nestAttributes.chambers.copy(
        nurseryChamber = initial.nestAttributes.chambers.nurseryChamber.copy(level = 1))),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(exploreTabUnlocked = true)),
    )
  }

  test("Nectar Meadow is hidden until level four and requires five workers and a scout") {
    val low = colony(599L)
    assert(!NectarMeadow.isUnlocked(low.exploration, low.ants, low.nestAttributes))
    assert(!ExplorationHelper.canStart(low, 5 * u, NectarMeadow))
    val ready = colony()
    assert(NectarMeadow.isUnlocked(ready.exploration, ready.ants, ready.nestAttributes))
    assert(!ExplorationHelper.canStart(ready, 4 * u, NectarMeadow))
    assert(!ExplorationHelper.canStart(ready.copy(ants = ready.ants.copy(scouts = 0L)), 5 * u, NectarMeadow))
    assertEquals(ExplorationHelper.cost(5 * u, NectarMeadow), ActionCost(sugar = 200 * u, idleWorkers = 5 * u))
    assert(ExplorationHelper.canStart(ready, 5 * u, NectarMeadow))
  }

  test("meadow previews match guaranteed rewards, including a level crossed on return") {
    val sent = ExplorationHelper.start(colony(950L), 5 * u, NectarMeadow)
    val result = ExplorationHelper.exploreBonus(sent, sent.exploration.explorationParties.head)
    assertEquals(result.exploration.experience, 1040L)
    assertEquals(result.basicResources.sugar.amount, 4000L * u)
    assertEquals(result.basicResources.knowledge.amount, 32L * u)
    assertEquals(result.ants.idleWorkersCount, 20L * u)
    assert(result.exploration.discoveredCarcasses.isEmpty)
    assert(ExplorationHelper.summary(colony(950L).exploration, 5 * u, NectarMeadow).contains("1200 sugar"))
    assert(ExplorationHelper.preview(colony().exploration, 5 * u, NectarMeadow).contains("Sugar guaranteed"))
    assert(result.messages.head.contains("1200 sugar"))
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

  test("new upgrades are hidden before their level and nursery prerequisites") {
    assert(!ReinforcedMandibles.shouldShow(colony(299L)))
    assert(ReinforcedMandibles.shouldShow(colony(300L)))
    assert(!BroodCare.shouldShow(colony(599L)))
    assert(BroodCare.shouldShow(colony()))
    assert(!BroodCare.shouldShow(colony().copy(nestAttributes = NestAttributes.initial)))
    Seq(ReinforcedMandibles, BroodCare).foreach { upgrade =>
      assert(upgrade.cost.sugar > 0L && upgrade.cost.protein > 0L && upgrade.cost.knowledge > 0L)
    }
  }

  test("new colony upgrades and a meadow party survive save reload without changing rewards") {
    val sent = ExplorationHelper.start(colony().copy(upgrades = UpgradesData.initial.unlock(ReinforcedMandibles, BroodCare)), 5 * u, NectarMeadow)
    val loaded = AllDataForSave.fromAllData(sent).toAllData
    assert(loaded.upgrades(ReinforcedMandibles).unlocked && loaded.upgrades(BroodCare).unlocked)
    assertEquals(ExplorationDestination.forParty(loaded.exploration.explorationParties.head), NectarMeadow)
    val party = loaded.exploration.explorationParties.head
    assertEquals(ExplorationHelper.exploreBonus(loaded, party).basicResources, ExplorationHelper.exploreBonus(sent, party).basicResources)
  }
}
