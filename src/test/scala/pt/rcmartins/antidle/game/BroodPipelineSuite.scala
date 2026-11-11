package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._

class BroodPipelineSuite extends FunSuite {
  def fresh: AllData = AllData.initial.copy(world = AllData.initial.world.copy(saveSeed = 123L, currentTick = 0L, targetTick = 0L))
    .giveResources(sugar = 1000 * u)
  def normal: AllData = fresh.copy(ants = AntsData(), basicResources = fresh.basicResources.copy(protein = BasicResource(100 * u, 100 * u)))
  def advance(d: AllData): AllData = BroodProgression.tick(d.copy(world = d.world.copy(currentTick = d.currentTick + 1L)))
  def load(d: AllData): AllData = AllDataForSave.fromAllData(d).toJson.fromJson[AllDataForSave].toOption.get.toAllData

  test("fresh colonies found with fast small brood while legacy saves retain standard workers") {
    assert(!fresh.ants.standardWorkersUnlocked)
    val laid = BroodProgression.lay(fresh)
    assertEquals(laid.basicResources.sugar.amount, fresh.basicResources.sugar.amount - 5 * u)
    assertEquals(laid.ants.eggsAndLarvae.head.asInstanceOf[AntBrood.Developing].kind, "small")
    val adult = (1 to 76).foldLeft(laid)((d, _) => advance(d))
    assertEquals(adult.ants.smallWorkers, u)
    assertEquals(adult.ants.workers, 0L)
    assert(adult.ants.eggsAndLarvae.isEmpty)
    assertEquals(adult.statistics.smallWorkersMatured.thisRun, 1)
    assertEquals("{}".fromJson[AntsData].toOption.get.standardWorkersUnlocked, true)
    assertEquals(load(adult).ants.smallWorkers, u)
  }
  test("small sugar, tunnelling, scraps and nursing contributions are half standard; upkeep is half") {
    val standard = normal.copy(ants = AntsData(workers = 4 * u, tasks = Seq(AntTask.SugarCollector -> (4 * u))))
    val small = fresh.copy(ants = AntsData.initial.copy(smallWorkers = 4 * u))
    assertEquals(Foraging.production(small.ants, small.upgrades) * 2L, Foraging.production(standard.ants, standard.upgrades))
    assertEquals(ScoutProgression.upkeepPerTick(small.ants, small.exploration) * 2L, ScoutProgression.upkeepPerTick(standard.ants, standard.exploration))
    val tunneled = TickUpdater.updateAllData(standard.copy(ants = standard.ants.copy(tasks = Seq(AntTask.Tunneler -> (4 * u)))), None)
    val tinyTunnels = TickUpdater.updateAllData(small.copy(ants = small.ants.copy(smallTunnelers = 4 * u)), None)
    assertEquals(tinyTunnels.basicResources.tunnelingSpace.amount * 2L, tunneled.basicResources.tunnelingSpace.amount)
    assertEquals(BroodProgression.scavenged(AntsData(smallWorkers = 2 * u, smallScavengers = 2 * u), 100 * u) * 2L,
      BroodProgression.scavenged(AntsData(workers = 2 * u, tasks = Seq(AntTask.Scavenger -> (2 * u))), 100 * u))
    assertEquals(BroodProgression.nursePower(AntsData(smallWorkers = 3 * u, smallNurses = 3 * u)) * 2L,
      BroodProgression.nursePower(AntsData(workers = 3 * u, tasks = Seq(AntTask.BroodNurse -> (3 * u)))))
  }
  test("small lifespans are finite from first adulthood, shorter than standard and never reroll") {
    val adult = (1 to 76).foldLeft(BroodProgression.lay(fresh))((d, _) => advance(d))
    val ages = adult.ants.ages.smallWorkers
    assertEquals(ages.map(_.count).sum, 1L)
    assert(ages.forall(c => c.expiresAt >= adult.currentTick + 30L * 60L * TicksPerSecond && c.expiresAt < adult.currentTick + 40L * 60L * TicksPerSecond))
    assertEquals(load(adult).ants.ages.smallWorkers, ages)
    val dead = PopulationAging.tick(adult.copy(world = adult.world.copy(currentTick = ages.head.expiresAt)))
    assertEquals(dead.ants.smallWorkers, 0L)
    assertEquals(dead.statistics.workersLostToAging.thisRun, 1)
  }
  test("research switches only future eggs, preserving already-laid small brood and adults") {
    val laid = BroodProgression.lay(fresh)
    val researched = BroodProgression.reconcile(laid.copy(upgrades = laid.upgrades.unlock(StandardWorkers)))
    assert(researched.ants.standardWorkersUnlocked)
    assertEquals(researched.ants.eggsAndLarvae, laid.ants.eggsAndLarvae)
    val second = BroodProgression.lay(researched.copy(ants = researched.ants.copy(layingCooldown = 0L)))
    assertEquals(second.ants.eggsAndLarvae.collect { case b: AntBrood.Developing => b.kind }, Seq("small", "worker"))
    assertEquals(BroodProgression.eggCost(second.ants), 10 * u)
    val reset = Prestige.found(second.copy(ants = second.ants.copy(daughterQueens = u)))
    assert(!reset.ants.standardWorkersUnlocked)
    assertEquals(reset.ants.smallWorkers, 0L)
  }
  test("orders reserve future housing but create at most one egg per laying interval") {
    val ordered = BroodProgression.order(fresh, 20)
    assertEquals(ordered.ants.eggOrders, fresh.nestAttributes.maxAntsCount)
    assert(ordered.ants.eggsAndLarvae.isEmpty)
    assertEquals(ordered.basicResources, fresh.basicResources)
    val first = BroodProgression.layOrdered(ordered)
    assertEquals(first.ants.eggsAndLarvae.size, 1)
    assertEquals(first.ants.eggOrders, ordered.ants.eggOrders - 1)
    assertEquals(BroodProgression.layOrdered(first), first)
    val cancelled = BroodProgression.cancel(first)
    assertEquals(cancelled.ants.eggOrders, 0)
    assertEquals(cancelled.basicResources, first.basicResources)
    assertEquals(BroodProgression.interval(normal.ants, normal.nestAttributes), 100L)
  }
  test("unfunded worker and scout orders reserve housing, wait, then charge only when laid") {
    val empty = normal.copy(basicResources = normal.basicResources.copy(
      sugar = BasicResource(0L, 1000 * u), protein = BasicResource(0L, 100 * u)),
      nestAttributes = normal.nestAttributes.copy(maxAnts = 5 * u,
        chambers = normal.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
    val workers = BroodProgression.order(empty, 2)
    val queued = ScoutProgression.raise(workers, 10)
    assertEquals(queued.ants.eggOrders, 2)
    assertEquals(queued.ants.scoutEggOrders, Seq(2, 2, 2))
    assertEquals(queued.basicResources, empty.basicResources)
    assertEquals(BroodProgression.layOrdered(queued), queued)
    assertEquals(BroodProgression.order(queued, 1), queued)
    assertEquals(ScoutProgression.raise(queued), queued)
    assertEquals(load(queued).ants.scoutEggOrders, queued.ants.scoutEggOrders)
    val funded = queued.giveResources(sugar = 10 * u)
    val laid = BroodProgression.layOrdered(funded)
    assertEquals(laid.ants.eggOrders, 1)
    assertEquals(laid.ants.eggsAndLarvae.size, 1)
    assertEquals(laid.basicResources.sugar.amount, 0L)

    val scout = ScoutProgression.raise(empty, 2)
    assertEquals(scout.ants.scoutEggOrders, Seq(0, 0))
    assertEquals(BroodProgression.layOrdered(scout.giveResources(sugar = 50 * u)).ants.eggsAndLarvae.size, 0)
    val scoutLaid = BroodProgression.layOrdered(scout.giveResources(sugar = 50 * u, protein = 2 * u))
    assertEquals(scoutLaid.ants.scoutEggOrders, Seq(0))
    assertEquals(scoutLaid.ants.eggsAndLarvae.size, 1)
    assertEquals(scoutLaid.basicResources.sugar.amount, 0L)
    assertEquals(scoutLaid.basicResources.protein.amount, 0L)
  }
  test("larvae and pupae continue occupying brood places; expansion and nurses solve separate bottlenecks") {
    val b = AntBrood.Developing(0L)
    val full = normal.copy(ants = normal.ants.copy(eggsAndLarvae = Seq(b.copy(care = b.eggEnd), b.copy(care = b.larvaEnd))))
    assert(!BroodProgression.canLay(full.ants, full.nestAttributes, full.basicResources.sugar.amount))
    assertEquals(BroodProgression.order(full, 1).ants.eggOrders, 1)
    val nursery = full.nestAttributes.copy(chambers = full.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1)))
    assertEquals(BroodProgression.capacity(nursery), BroodProgression.capacity(full.nestAttributes) + 3)
    assert(BroodProgression.canLay(full.ants, nursery, full.basicResources.sugar.amount))
  }
  test("standard development consumes its exact larval budget even with fractional care and nurses") {
    val nursed = normal.copy(ants = AntsData(workers = u, tasks = Seq(AntTask.BroodNurse -> u)),
      nestAttributes = normal.nestAttributes.copy(chambers = normal.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))),
      upgrades = normal.upgrades.unlock(BroodCare))
    val grown = (1 to 240).foldLeft(BroodProgression.lay(nursed))((d, _) => advance(d))
    assert(grown.ants.eggsAndLarvae.isEmpty)
    assertEquals(grown.ants.workers, 2 * u)
    assertEquals(nursed.basicResources.sugar.amount - grown.basicResources.sugar.amount, 30 * u)
    assertEquals(nursed.basicResources.protein.amount - grown.basicResources.protein.amount, 2 * u)
  }
  test("protein shortages preserve larval progress while eggs and pupae can still develop") {
    val larva = AntBrood.Developing(0L).copy(care = 100L * u)
    val hungry = normal.copy(ants = normal.ants.copy(eggsAndLarvae = Seq(larva)),
      basicResources = normal.basicResources.copy(protein = BasicResource(0L, 100 * u)))
    val stopped = advance(hungry)
    assertEquals(stopped.ants.eggsAndLarvae, hungry.ants.eggsAndLarvae)
    assertEquals(stopped.basicResources, hungry.basicResources)
    assert(BroodProgression.status(stopped.ants, stopped.nestAttributes, stopped.basicResources).contains("Needs protein"))
    val fed = advance(stopped.giveResources(protein = u))
    assert(fed.ants.eggsAndLarvae.head.asInstanceOf[AntBrood.Developing].care > larva.care)
  }
  test("care allocation rotates fairly and a small nurse adds half a standard nurse's budget") {
    val eggs = Vector.fill(6)(AntBrood.Developing(0L))
    def progressed(ants: AntsData): Long = {
      val d = normal.copy(ants = ants.copy(eggsAndLarvae = eggs), nestAttributes = normal.nestAttributes.copy(maxEggs = 6 * u,
        chambers = normal.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
      (1 to 6).foldLeft(d)((state, _) => advance(state)).ants.eggsAndLarvae.collect { case b: AntBrood.Developing => b.care }.sum
    }
    val base = progressed(AntsData())
    val small = progressed(AntsData(smallWorkers = u, smallNurses = u))
    val standard = progressed(AntsData(workers = u, tasks = Seq(AntTask.BroodNurse -> u)))
    assertEquals(small - base, (standard - base) / 2L)
    assertEquals(base, 18L * u)
  }
  test("small workers cannot substitute for the standard expedition or promotion pool") {
    val d = fresh.copy(ants = AntsData.initial.copy(smallWorkers = 20 * u),
      unlocks = fresh.unlocks.copy(tabs = fresh.unlocks.tabs.copy(exploreTabUnlocked = true)))
    assertEquals(d.ants.availableWorkersCount, 0L)
    assertEquals(ExplorationHelper.start(d, u, ExplorationDestination.Surroundings), d)
    val known = d.copy(exploration = d.exploration.copy(surveys = Map("rival-food-cache" -> 1)))
    assert(!TerritoryProgression.canClaim(known, 10))
  }
  test("small shared task capacities and starvation never leave assignments above population") {
    val d = BroodProgression.reconcile(fresh.copy(ants = AntsData(smallWorkers = 5 * u),
      nestAttributes = fresh.nestAttributes.copy(chambers = fresh.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1)))))
    val assigned = BroodProgression.assignSmall(d, AntTask.Scavenger, 100 * u)
    assertEquals(assigned.ants.smallScavengers, 2 * u)
    val busy = BroodProgression.assignSmall(assigned, AntTask.BroodNurse, 100 * u)
    assertEquals(busy.ants.smallNurses, 3 * u)
    val dead = PopulationAging.loseSmall(busy.ants, 4L)
    assertEquals(dead.smallWorkers, u)
    assert(dead.smallTunnelers + dead.smallScavengers + dead.smallNurses <= dead.smallWorkers)
  }
  test("founding play can unlock standard workers in minutes with a comfortable lifespan margin") {
    var d = fresh.copy(basicResources = fresh.basicResources.copy(sugar = BasicResource(50 * u, 1000 * u)))
    d = BroodProgression.order(d, 6)
    var researchedAt = -1L
    var firstStandardAt = -1L
    for (_ <- 1 to 3000 if firstStandardAt < 0L) {
      if (!d.ants.standardWorkersUnlocked && d.ants.smallWorkers >= u && d.basicResources.tunnelingSpace.amount < 10 * u && d.ants.smallTunnelers == 0L)
        d = BroodProgression.assignSmall(d, AntTask.Tunneler, u)
      if (d.basicResources.tunnelingSpace.amount >= 10 * u && d.ants.smallTunnelers > 0L)
        d = BroodProgression.assignSmall(d, AntTask.Tunneler, -d.ants.smallTunnelers)
      if (!d.ants.standardWorkersUnlocked && d.ants.smallWorkers >= 5 * u && d.hasResources(StandardWorkers.cost)) {
        d = BroodProgression.reconcile(d.spendResources(StandardWorkers.cost).copy(upgrades = d.upgrades.unlock(StandardWorkers)))
        researchedAt = d.currentTick
        d = BroodProgression.assignSmall(d, AntTask.Scavenger, 2 * u)
      }
      if (d.ants.standardWorkersUnlocked && d.ants.eggOrders == 0 && d.ants.eggsAndLarvae.isEmpty && d.ants.workers == 0L)
        d = BroodProgression.order(d, 1)
      d = TickUpdater.updateAllData(d, None)
      if (d.ants.workers > 0L) firstStandardAt = d.currentTick
    }
    assert(researchedAt >= 0L && researchedAt < 5L * 60L * TicksPerSecond, s"research tick: $researchedAt")
    assert(firstStandardAt >= 0L && firstStandardAt < 10L * 60L * TicksPerSecond, s"first standard tick: $firstStandardAt")
    assert(d.ants.ages.smallWorkers.forall(_.expiresAt > firstStandardAt + 15L * 60L * TicksPerSecond))
    assertEquals(d.statistics.starvationDeaths.thisRun, 0)
  }
  test("loading founding adults restores their task panel without replaying one-time unlock events") {
    val d = fresh.copy(ants = AntsData.initial.copy(smallWorkers = 6 * u))
    val restored = load(d)
    assert(restored.unlocks.tabs.antTasksUnlocked)
    assert(restored.ants.tasks.exists(_._1 == AntTask.SugarCollector))
  }
  test("fresh surplus can feed ordinary larvae at full storage without false drain or double counting") {
    val d = normal.copy(ants = AntsData(workers = 4 * u, tasks = Seq(AntTask.SugarCollector -> (4 * u)),
      eggsAndLarvae = Seq(AntBrood.Developing(0L, care = 100 * u))))
    val prediction = BroodProgression.forecastCost(d.ants, d.nestAttributes, d.upgrades, d.exploration, d.basicResources)
    val next = TickUpdater.updateAllData(d, None)
    assertEquals(next.basicResources.sugar.amount, d.basicResources.sugar.amount)
    assertEquals(next.statistics.sugarU.thisRun - d.statistics.sugarU.thisRun, 2100L)
    assertEquals(d.basicResources.protein.amount - next.basicResources.protein.amount, prediction.protein)
  }

  test("new scout brood shares laying and feeding instead of bypassing the pipeline") {
    val d = normal.copy(nestAttributes = normal.nestAttributes.copy(chambers = normal.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
    val raised = ScoutProgression.raise(d)
    assert(!ScoutProgression.canLay(raised.ants, raised.nestAttributes, raised.basicResources))
    assertEquals(raised.ants.eggsAndLarvae.head.asInstanceOf[AntBrood.Developing].kind, "scout")
    val adult = (1 to 450).foldLeft(raised)((state, _) => advance(state))
    assertEquals(adult.ants.scouts, u)
    assertEquals(adult.ants.workers, 0L)
    assertEquals(d.basicResources.sugar.amount - adult.basicResources.sugar.amount, 70 * u)
    assertEquals(d.basicResources.protein.amount - adult.basicResources.protein.amount, 4 * u)
  }

  test("scout batches clamp only to housing, retain FIFO and survive saves") {
    val d = normal.copy(ants = normal.ants.copy(eggOrders = 2, layingCooldown = 10L),
      nestAttributes = normal.nestAttributes.copy(maxAnts = 100 * u,
        chambers = normal.nestAttributes.chambers.copy(nurseryChamber = Chamber.NurseryChamber(1))))
    val ordered = ScoutProgression.raise(d, 10)
    assertEquals(ordered.ants.scoutEggOrders, Seq.fill(10)(2))
    assertEquals(ordered.basicResources, d.basicResources)
    assertEquals(load(ordered).ants.scoutEggOrders, ordered.ants.scoutEggOrders)
    val sugarLimited = d.copy(basicResources = d.basicResources.copy(sugar = BasicResource(120 * u, 1000 * u)))
    assertEquals(ScoutProgression.raise(sugarLimited, 10).ants.scoutEggOrders.size, 10)
    val proteinLimited = d.copy(basicResources = d.basicResources.copy(protein = BasicResource(5 * u, 100 * u)))
    assertEquals(ScoutProgression.raise(proteinLimited, 10).ants.scoutEggOrders.size, 10)
    val cramped = d.copy(nestAttributes = d.nestAttributes.copy(maxAnts = 5 * u))
    assertEquals(ScoutProgression.raise(cramped, 10).ants.scoutEggOrders.size, 3)
    assertEquals(ScoutProgression.raise(d, 0), d)
    val ready = d.copy(ants = d.ants.copy(eggOrders = 0, layingCooldown = 0L))
    val immediate = ScoutProgression.raise(ready, 10)
    assertEquals(immediate.ants.eggsAndLarvae.size, 1)
    assertEquals(immediate.ants.scoutEggOrders.size, 9)
    assertEquals(immediate.basicResources.sugar.amount, ready.basicResources.sugar.amount - 50 * u)
  }

}
