package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class MilestoneResearchSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(
      ants = AntsData(
        workers = 30 * u,
        scouts = 3 * u,
        tasks = Seq(AntTask.SugarCollector -> (30 * u)),
        defaultTask = Some(AntTask.SugarCollector)
      ),
      nestAttributes = d.nestAttributes.copy(
        maxAnts = 100 * u,
        maxEggs = 20 * u,
        chambers = d.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))
      ),
      upgrades = d.upgrades.unlock(UnlockExplorerTask, BroodCare, QueensRoutine, DefaultWorkers),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)),
      basicResources = d.basicResources.copy(
        sugar = BasicResource(20000 * u, 100000 * u),
        protein = BasicResource(1000 * u, 10000 * u),
        knowledge = BasicResource(1000 * u, Long.MaxValue)
      )
    )
  }

  def earned: AllData = colony.copy(statistics =
    colony.statistics.copy(
      explorationsCompleted = StatisticsData.StatInt(20, 2000),
      workersMatured = StatisticsData.StatInt(55, 2000),
      smallWorkersMatured = StatisticsData.StatInt(5, 1000)
    )
  )

  test(
    "goals use current-colony completions and standard workers, never launches or lifetime totals"
  ) {
    val d = colony.copy(statistics =
      colony.statistics.copy(
        explorationsCount = StatisticsData.StatInt(500, 500),
        explorationsCompleted = StatisticsData.StatInt(19, 2000),
        workersMatured = StatisticsData.StatInt(29, 2000),
        smallWorkersMatured = StatisticsData.StatInt(5, 1000)
      )
    )
    assert(!ScoutWayfinding.shouldShow(d)); assert(!NurseTraining.shouldShow(d));
    assert(!QueensRhythm.shouldShow(d))
    assertEquals(MilestoneResearch.standardWorkers(d.statistics), 24)
    List(ScoutWayfinding, NurseTraining, QueensRhythm).foreach { upgrade =>
      assert(upgrade.shouldShow(earned)); assert(!UpgradeResearch.canPurchase(d, upgrade))
      val bought = UpgradeResearch.purchase(earned, upgrade)
      assert(bought.upgrades(upgrade).unlocked)
      assertEquals(bought.basicResources, earned.basicResources.spendResources(upgrade.cost))
      assertEquals(UpgradeResearch.purchase(bought, upgrade), bought)
    }
  }

  test(
    "only one relevant unfinished milestone is previewed and completed goals reveal normal research"
  ) {
    assertEquals(MilestoneResearch.next(AllData.initial), None)
    assertEquals(MilestoneResearch.next(colony).get.upgrade, ScoutWayfinding)
    val missions = colony.copy(statistics =
      colony.statistics.copy(explorationsCompleted = StatisticsData.StatInt(20, 20))
    )
    assertEquals(MilestoneResearch.next(missions).get.upgrade, NurseTraining)
    val workers = missions.copy(statistics =
      missions.statistics.copy(workersMatured = StatisticsData.StatInt(25, 25))
    )
    assertEquals(MilestoneResearch.next(workers).get.upgrade, QueensRhythm)
    assertEquals(MilestoneResearch.next(earned), None)
  }

  test(
    "new scout departures use the preview duration; cost, Knowledge and existing parties do not change"
  ) {
    val before = ExplorationMap.scout(earned, "surroundings", 3)
    val oldParty = before.exploration.explorationParties.head
    val bought = UpgradeResearch.purchase(before, ScoutWayfinding)
    assertEquals(bought.exploration.explorationParties.head, oldParty)
    for (n <- 1 to 3) {
      val fresh =
        ExplorationMap.scout(UpgradeResearch.purchase(earned, ScoutWayfinding), "surroundings", n)
      val party = fresh.exploration.explorationParties.head
      assertEquals(
        party.targetTick - party.startingTick,
        ExplorationMap.scoutDuration(n, true, fresh.upgrades)
      )
      assertEquals(
        party.targetTick - party.startingTick,
        ExplorationMap.scoutDuration(n, true) * 4L / 5L
      )
      assertEquals(
        fresh.basicResources.sugar.amount,
        earned.basicResources.sugar.amount - ScoutWayfinding.cost.sugar - ExplorationMap
          .scoutCost(n)
          .sugar
      )
    }
    val studied = earned.copy(exploration = ExplorationData(revealedSites = 1))
    val fast = UpgradeResearch.purchase(studied, ScoutWayfinding)
    val ordinary = ExplorationMap.scout(studied, "woodland-edge", 1)
    val quick = ExplorationMap.scout(fast, "woodland-edge", 1)
    assertEquals(
      quick.exploration.explorationParties.head.targetTick,
      ordinary.exploration.explorationParties.head.targetTick * 4L / 5L
    )
    val a = ExplorationHelper.exploreBonus(ordinary, ordinary.exploration.explorationParties.head)
    val b = ExplorationHelper.exploreBonus(quick, quick.exploration.explorationParties.head)
    assertEquals(
      a.basicResources.knowledge.amount - studied.basicResources.knowledge.amount,
      b.basicResources.knowledge.amount - fast.basicResources.knowledge.amount
    )
  }

  test(
    "trained nurses improve real shared brood care, retain small-worker half power and match feeding forecast"
  ) {
    val base = earned.copy(ants =
      earned.ants.copy(
        tasks = Seq(AntTask.BroodNurse -> u, AntTask.SugarCollector -> (29 * u)),
        eggsAndLarvae = Vector.fill(6)(AntBrood.Developing(0L, "worker", 100 * u))
      )
    )
    val trained = UpgradeResearch.purchase(base, NurseTraining)
    assertEquals(BroodProgression.nursePower(trained.ants, trained.upgrades), 3 * u)
    assertEquals(
      BroodProgression.nursePower(AntsData(smallWorkers = u, smallNurses = u), trained.upgrades),
      3 * u / 2
    )
    val ordinary = BroodProgression.tick(base)
    val quick = BroodProgression.tick(trained)
    def care(d: AllData): Long = d.ants.eggsAndLarvae.collect { case b: AntBrood.Developing =>
      b.care
    }.sum
    assert(care(quick) > care(ordinary))
    assertEquals(
      trained.basicResources.protein.amount - quick.basicResources.protein.amount,
      BroodProgression
        .forecastCost(
          trained.ants,
          trained.nestAttributes,
          trained.upgrades,
          trained.exploration,
          trained.basicResources
        )
        .protein
    )
    val noNurses = base.copy(ants = base.ants.copy(tasks = Seq(AntTask.SugarCollector -> (30 * u))))
    val upgraded = noNurses.copy(upgrades = noNurses.upgrades.unlock(NurseTraining))
    assertEquals(care(BroodProgression.tick(noNurses)), care(BroodProgression.tick(upgraded)))
  }

  test("Queen's Rhythm affects newly laid worker and scout eggs and keeps existing cooldown") {
    val cooling = earned.copy(ants = earned.ants.copy(layingCooldown = 50L))
    val researched = UpgradeResearch.purchase(cooling, QueensRhythm)
    assertEquals(researched.ants.layingCooldown, 50L)
    val ready = researched.copy(ants = researched.ants.copy(layingCooldown = 0L))
    val worker = BroodProgression.lay(ready)
    val scout = ScoutProgression.lay(ready)
    assertEquals(
      worker.ants.layingCooldown,
      BroodProgression.interval(ready.ants, ready.nestAttributes, ready.upgrades)
    )
    assertEquals(scout.ants.layingCooldown, worker.ants.layingCooldown)
    assertEquals(
      worker.ants.layingCooldown,
      BroodProgression.interval(ready.ants, ready.nestAttributes) * 4L / 5L
    )
    val bonus = ChamberBonuses.forDisplay(
      Chamber.ChamberType.Queen,
      ready.ants,
      ready.nestAttributes,
      ready.upgrades
    )
    val next = ready.nestAttributes.copy(chambers =
      ready.nestAttributes.chambers.copy(queenChamber = Chamber.QueenChamber(2))
    )
    assertEquals(
      bonus.layingIntervalReduction,
      worker.ants.layingCooldown - BroodProgression.interval(ready.ants, next, ready.upgrades)
    )
  }

  test(
    "milestone research and goals survive reload but reset on founding while lifetime counts remain"
  ) {
    val bought =
      List(ScoutWayfinding, NurseTraining, QueensRhythm).foldLeft(earned)(UpgradeResearch.purchase)
    val loaded =
      AllDataForSave.fromAllData(bought).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    List(ScoutWayfinding, NurseTraining, QueensRhythm).foreach(u =>
      assert(loaded.upgrades(u).unlocked)
    )
    val founded = Prestige.found(loaded.copy(ants = loaded.ants.copy(daughterQueens = u)))
    assertEquals(founded.statistics.explorationsCompleted.thisRun, 0)
    assertEquals(founded.statistics.workersMatured.total, 2000)
    List(ScoutWayfinding, NurseTraining, QueensRhythm).foreach(u => {
      assert(!founded.upgrades(u).unlocked); assert(!u.shouldShow(founded))
    })
  }

}
