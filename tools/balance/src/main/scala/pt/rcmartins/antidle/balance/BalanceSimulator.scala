package pt.rcmartins.antidle.balance

import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import scala.collection.mutable

object BalanceSimulator {
  def run(config: Config, policy: Policy, seed: Long,
      checkpoint: (String, AllData) => Unit = (_, _) => (),
      progress: String => Unit = _ => ()): Run = {
    var data = AllData.initial.copy(world = WorldData(seed, 0L, 0L, 0L))
    val events = mutable.ArrayBuffer.empty[Event]
    val milestones = mutable.ArrayBuffer.empty[Milestone]
    val samples = mutable.ArrayBuffer.empty[Sample]
    val waits = mutable.ArrayBuffer.empty[Wait]
    val seen = mutable.Set.empty[(Int, String)]
    val occupancy = mutable.Map.empty[String, (Long, Long)]
    var actions = 0L; var clicks = 0; var colonyClicks = 0; var colonyStart = 0L
    var activeWait: Option[Wait] = None
    val sampleEvery = Math.max(config.sampleTicks.toLong, (config.maxTicks + 4999L) / 5000L)
    def closeWait(): Unit = { activeWait.foreach(w => waits += w.copy(end = data.currentTick)); activeWait = None }
    def milestone(name: String, yes: Boolean): Unit = {
      val colony = data.ants.lineage.foundings
      if (yes && seen.add((colony, name))) {
        val path = s"${policy.name}-$seed/colony-$colony-$name.json"
        milestones += Milestone(colony, name, data.currentTick, data.currentTick - colonyStart, path)
        checkpoint(path, data)
        progress(s"${policy.name}/$seed: $name at ${data.currentTick / TicksPerSecond}s (colony $colony)")
      }
    }
    def observe(): Unit = {
      milestone("first-small-worker", data.ants.smallWorkers > 0L)
      milestone("standard-workers-unlocked", data.ants.standardWorkersUnlocked)
      milestone("first-standard-worker", data.ants.workers > 0L)
      milestone("exploration", data.unlocks.tabs.exploreTabUnlocked)
      milestone("outpost", data.nestAttributes.dryClearingOutpost)
      milestone("royal-nursery", data.nestAttributes.royalBroodSlots > 0)
      milestone("daughter-ready", Prestige.canFound(data))
    }
    def sample(): Unit = {
      val a = data.ants; val r = data.basicResources
      def n(t: AntTask): Int = (a.taskWorkers(t) / u).toInt
      samples += Sample(data.currentTick, a.lineage.foundings, a.workersCount, (a.smallWorkers / u).toInt,
        (a.scouts / u).toInt, r.sugar.amount.toDouble / u, r.protein.amount.toDouble / u,
        r.knowledge.amount.toDouble / u, r.water.amount.toDouble / u, r.resin.amount.toDouble / u,
        r.tunnelingSpace.amount.toDouble / u, n(AntTask.SugarCollector) + (a.smallCollectors / u).toInt,
        n(AntTask.AphidTender) + n(AntTask.SeedForager) + n(AntTask.Scavenger) + (a.smallScavengers / u).toInt,
        n(AntTask.BroodNurse) + n(AntTask.RoyalNurse) + (a.smallNurses / u).toInt,
        n(AntTask.Tunneler) + (a.smallTunnelers / u).toInt,
        a.tasks.filterNot(_._1.showButtons).map(_._2 / u).sum.toInt, a.sugarCumulativeDebt > 0L)
    }
    sample()
    while (data.currentTick < config.maxTicks && data.ants.lineage.foundings < config.foundings) {
      if (colonyClicks < config.openingClicks && (data.currentTick - colonyStart) * config.clicksPerSecond >= colonyClicks.toLong * TicksPerSecond) {
        data = GameTransition.afterAction(data.giveResources(sugar = u)); clicks += 1; colonyClicks += 1
      }
      if (data.currentTick % config.decisionTicks == 0L) {
        val decision = Strategy.decide(data, policy)
        decision.choice match {
          case Some(choice) =>
            val before = data
            data = GameTransition.afterAction(choice.apply(data))
            require(data != before, s"Policy returned a no-op: ${choice.name} at ${data.currentTick}")
            closeWait(); actions += 1
            if (events.size < 20000) events += Event(data.currentTick, before.ants.lineage.foundings, choice.name, choice.category)
            if (data.ants.lineage.foundings != before.ants.lineage.foundings) {
              colonyStart = data.currentTick; colonyClicks = 0
              milestone("founded", true)
            }
          case None =>
            if (!activeWait.exists(w => w.goal == decision.goal && w.missing == decision.missing)) {
              closeWait(); activeWait = Some(Wait(data.currentTick, data.currentTick, data.ants.lineage.foundings, decision.goal, decision.missing))
            }
        }
      }
      if (data.ants.lineage.foundings < config.foundings) {
        data = TickUpdater.updateAllData(data.copy(world = data.world.copy(targetTick = data.currentTick + 1L)), None)
        val r = data.basicResources
        List("sugar" -> r.sugar, "protein" -> r.protein, "knowledge" -> r.knowledge,
          "water" -> r.water, "resin" -> r.resin, "tunnelling space" -> r.tunnelingSpace).foreach { case (name, resource) =>
          val (empty, full) = occupancy.getOrElse(name, (0L, 0L))
          occupancy(name) = (empty + (if (resource.amount == 0L) 1L else 0L),
            full + (if (resource.maxAmount > 0L && resource.amount >= resource.maxAmount) 1L else 0L))
        }
        observe()
        if (data.currentTick % sampleEvery == 0L) sample()
        if (data.currentTick % (15 * 60 * TicksPerSecond) == 0L) progress(s"${policy.name}/$seed: ${data.currentTick / TicksPerSecond}s simulated; ${data.ants.totalWorkersCount} workers")
      }
    }
    closeWait(); sample()
    checkpoint(s"${policy.name}-$seed/final.json", data)
    val stats = data.statistics
    Run(policy, seed, if (data.ants.lineage.foundings >= config.foundings) "goal_reached" else "budget_exhausted",
      data.currentTick, data.ants.lineage.foundings, actions, clicks, actions > events.size,
      events.toVector, milestones.toVector, waits.toVector, samples.toVector,
      occupancy.toList.sortBy(_._1).map { case (name, (empty, full)) => ResourceTime(name, empty, full) },
      stats.starvationDeaths.total, stats.workersLostToAging.total + stats.scoutsLostToAging.total + stats.repletesLostToAging.total)
  }
}
