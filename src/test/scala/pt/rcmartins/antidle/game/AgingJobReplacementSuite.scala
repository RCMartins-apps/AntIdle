package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.AntTask._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class AgingJobReplacementSuite extends FunSuite {
  def colony: AllData = {
    val d = AllData.initial
    d.copy(world = d.world.copy(currentTick = 100L, targetTick = 100L, saveSeed = 42L),
      ants = AntsData(workers = 11L * u, defaultTask = Some(SugarCollector),
        tasks = Seq(SugarCollector -> (5L * u), Tunneler -> (2L * u), Scavenger -> (2L * u), BroodNurse -> (2L * u)),
        ages = ColonyAges(active = true, workers = List(AgeCohort(100L, 3L), AgeCohort(10000L, 8L)))),
      upgrades = d.upgrades.unlock(QueensRoutine, DefaultWorkers),
      nestAttributes = d.nestAttributes.copy(chambers = d.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
  }
  def balanced(a: AntsData): Unit = {
    assert(a.idleWorkersCount >= 0L)
    assert(a.tasks.forall(_._2 >= 0L))
    assertEquals(a.tasks.map(_._2).sum + a.idleWorkersCount, a.workers)
    assert(a.smallCollectors >= 0L)
  }
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("aging uses the default pool and keeps every staffed home job regardless of display order") {
    for (tasks <- Seq(colony.ants.tasks, colony.ants.tasks.reverse)) {
      val d = colony.copy(ants = colony.ants.copy(tasks = tasks))
      val next = PopulationAging.tick(d)
      assertEquals(next.ants.workers, 8L * u)
      assertEquals(next.ants.taskWorkers(SugarCollector), 2L * u)
      for (task <- Seq(Tunneler, Scavenger, BroodNurse)) assertEquals(next.ants.taskWorkers(task), 2L * u)
      assert(next.ants.agingJobVacancies.isEmpty)
      assertEquals(next.ants.ages.workers, List(AgeCohort(10000L, 8L)))
      assertEquals(next.statistics.workersLostToAging.thisRun, 3)
      balanced(next.ants)
    }
  }

  test("the selected default can be a middle job and idle workers are used first") {
    val a = colony.ants.copy(defaultTask = Some(Tunneler),
      tasks = Seq(SugarCollector -> (2L * u), Tunneler -> (5L * u), Scavenger -> (2L * u), BroodNurse -> (2L * u)))
    val next = PopulationAging.tick(colony.copy(ants = a))
    assertEquals(next.ants.taskWorkers(Tunneler), 2L * u)
    assertEquals(next.ants.taskWorkers(BroodNurse), 2L * u)
    val idle = a.copy(tasks = a.tasks.map { case (task, n) => task -> (if (task == Tunneler) n - 2L * u else n) })
    val result = PopulationAging.tick(colony.copy(ants = idle))
    assertEquals(result.ants.taskWorkers(Tunneler), 2L * u)
    assertEquals(result.ants.taskWorkers(SugarCollector), 2L * u)
    balanced(result.ants)
  }

  test("standard default workers replace aging small nurses without exceeding shared caps or resetting ages") {
    val a = colony.ants.copy(workers = 5L * u, smallWorkers = 4L * u,
      smallTunnelers = u, smallScavengers = u, smallNurses = 2L * u,
      tasks = Seq(SugarCollector -> (4L * u), Tunneler -> u, Scavenger -> 0L, BroodNurse -> 0L),
      ages = ColonyAges(active = true, workers = List(AgeCohort(10000L, 5L)),
        smallWorkers = List(AgeCohort(100L, 2L), AgeCohort(10000L, 2L))))
    val next = PopulationAging.tick(colony.copy(ants = a))
    assertEquals(next.ants.smallWorkers, 2L * u)
    assertEquals(next.ants.smallNurses, 0L)
    assertEquals(next.ants.taskWorkers(BroodNurse), 2L * u)
    assertEquals(next.ants.taskWorkers(SugarCollector), 2L * u)
    assertEquals(next.ants.smallScavengers, u)
    assertEquals(next.ants.smallTunnelers, u)
    assertEquals(next.ants.ages.workers, a.ages.workers)
    assert(next.ants.agingJobVacancies.isEmpty)
    balanced(next.ants)
  }

  test("vacancies survive an empty pool and newly matured workers refill jobs before default work") {
    val a = colony.ants.copy(workers = 3L * u,
      tasks = Seq(SugarCollector -> 0L, Tunneler -> u, Scavenger -> u, BroodNurse -> u),
      ages = ColonyAges(active = true, workers = List(AgeCohort(100L, 1L), AgeCohort(10000L, 2L))))
    val lost = PopulationAging.tick(colony.copy(ants = a))
    assertEquals(lost.ants.agingJobVacancies, Seq(BroodNurse -> u))
    assertEquals(lost.ants.taskWorkers(BroodNurse), 0L)
    val restored = load(lost)
    assertEquals(restored.ants.agingJobVacancies, lost.ants.agingJobVacancies)
    val brood = AntBrood.Developing(0L, "worker", 599L * u)
    val adult = BroodProgression.tick(restored.copy(ants = restored.ants.copy(eggsAndLarvae = Seq(brood))))
    assertEquals(adult.ants.workers, 3L * u)
    assertEquals(adult.ants.taskWorkers(BroodNurse), u)
    assertEquals(adult.ants.taskWorkers(SugarCollector), 0L)
    assert(adult.ants.agingJobVacancies.isEmpty)
    assertEquals(adult.ants.ages.workers.map(_.count).sum, 3L)
    balanced(adult.ants)
  }

  test("unlocked Queen's Routine maintains jobs even when automatic egg laying is paused") {
    assert(!colony.ants.queenRoutine.enabled)
    assertEquals(PopulationAging.tick(colony).ants.taskWorkers(BroodNurse), 2L * u)
    val locked = colony.copy(upgrades = colony.upgrades.copy(upgrades = colony.upgrades.upgrades.updated(QueensRoutine, UpgradeData(false, false))))
    val lost = PopulationAging.tick(locked)
    assertEquals(lost.ants.taskWorkers(BroodNurse), 0L)
    assert(lost.ants.agingJobVacancies.isEmpty)
  }

  test("editing a job or cancelling pending replacements respects player intent") {
    val d = colony.copy(ants = colony.ants.copy(agingJobVacancies = Seq(BroodNurse -> (2L * u), Scavenger -> u)))
    val edited = DefaultWorkerAssignment.assign(d, BroodNurse, u)
    assertEquals(edited.ants.taskWorkers(BroodNurse), 3L * u)
    assert(!edited.ants.agingJobVacancies.exists(_._1 == BroodNurse))
    assert(AgingJobReplacement.cancelAll(d).ants.agingJobVacancies.isEmpty)
    val small = BroodProgression.assignSmall(d, BroodNurse, -u)
    assert(!small.ants.agingJobVacancies.exists(_._1 == BroodNurse))
    assertEquals("{}".fromJson[AntsData].toOption.get.agingJobVacancies, Seq.empty)
  }

  test("small collectors can refill supported jobs; shared capacity leaves the rest pending") {
    val a = AntsData(smallWorkers = 4L * u, smallScavengers = u,
      tasks = Seq(SugarCollector -> 0L, Scavenger -> 0L), agingJobVacancies = Seq(Scavenger -> (3L * u)))
    val next = DefaultWorkerAssignment.normalize(colony.copy(ants = a))
    assertEquals(next.ants.smallScavengers, 2L * u)
    assertEquals(next.ants.smallCollectors, 2L * u)
    assertEquals(next.ants.agingJobVacancies, Seq(Scavenger -> (2L * u)))
    balanced(next.ants)
  }

  test("a default change drops that job's quota and never borrows expedition or territory workers") {
    val a = colony.ants.copy(workers = 2L * u, defaultTask = Some(SugarCollector),
      tasks = Seq(SugarCollector -> 0L, Explorer -> u, TerritoryForager -> u, BroodNurse -> 0L),
      agingJobVacancies = Seq(BroodNurse -> u))
    val next = DefaultWorkerAssignment.normalize(colony.copy(ants = a))
    assertEquals(next.ants.agingJobVacancies, Seq(BroodNurse -> u))
    assertEquals(next.ants.taskWorkers(Explorer), u)
    assertEquals(next.ants.taskWorkers(TerritoryForager), u)
    val changed = DefaultWorkerAssignment.select(next, Some(BroodNurse))
    assert(changed.ants.agingJobVacancies.isEmpty)
    balanced(changed.ants)
  }

  test("expired or recalled pheromone trails never retain replacement requests") {
    val d = colony.copy(ants = colony.ants.copy(agingJobVacancies = Seq(TrailForager -> u)))
    assert(PheromoneTrails.recall(d).ants.agingJobVacancies.isEmpty)
    assert(PheromoneTrails.reconcile(d).ants.agingJobVacancies.isEmpty)
  }

  test("starvation does not create aging vacancies") {
    val a = colony.ants.copy(workers = 3L * u, tasks = Seq(Tunneler -> u, Scavenger -> u, BroodNurse -> u))
    val lost = PopulationAging.starveOne(colony.copy(ants = a))
    assertEquals(lost.workers, 2L * u)
    assert(lost.agingJobVacancies.isEmpty)
  }
}
