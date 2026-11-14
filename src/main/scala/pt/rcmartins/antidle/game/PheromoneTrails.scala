package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._

/** Safe, continuous collection: shared finite deposits, no mission slots or extra rewards. */
object PheromoneTrails {
  val EfficiencyPercent: Long = 60L
  def efficiency(upgrades: UpgradesData): Long = if (upgrades(UpgradesData.UpgradeType.TrailLogistics).unlocked) 75L else EfficiencyPercent
  val Capacity: Long = ExplorationProgression.MaxWorkers
  val Step: Long = ExplorationProgression.WorkerStep
  private val GuideCapacity: Long = 10L * u
  def requiredScouts(workers: Long): Long = (Math.max(0L, workers) + GuideCapacity - 1L) / GuideCapacity * u
  def target(exploration: ExplorationData, destination: String): Long =
    exploration.collectionTrails.find(_.destination == destination).map(_.workers).getOrElse(0L)
  def targetWorkers(exploration: ExplorationData): Long = exploration.collectionTrails.map(_.workers).sum
  def workers(ants: AntsData): Long = Math.max(0L, ants.taskWorkers(AntTask.TrailForager))
  def staffed(ants: AntsData, exploration: ExplorationData, destination: String): Long = {
    val before = exploration.collectionTrails.filter(_.destination < destination).map(_.workers).sum
    Math.min(target(exploration, destination), Math.max(0L, workers(ants) - before))
  }
  def guides(ants: AntsData, exploration: ExplorationData): Long =
    if (!ants.lineage.sharedPaths) 0L else Math.min(
      Math.max(0L, ants.scouts - exploration.explorationParties.map(_.scouts).sum),
      exploration.collectionTrails.map(t => requiredScouts(t.workers)).sum)
  def eligible(exploration: ExplorationData, route: ExplorationDestination): Boolean =
    !RaidProgression.isRaid(route) && route.progressionUnlocked(exploration) &&
      ExplorationMap.remaining(exploration, route.id) > 0L
  private def route(id: String): Option[ExplorationDestination] = ExplorationDestination.all.find(_.id == id)

  def scoutsAvailableFor(ants: AntsData, exploration: ExplorationData, destination: String): Long = {
    val reservedElsewhere = exploration.collectionTrails.filterNot(_.destination == destination)
      .map(t => requiredScouts(t.workers)).sum
    Math.max(0L, ScoutProgression.atHome(ants, exploration) - reservedElsewhere)
  }
  def nextTarget(exploration: ExplorationData, destination: String): Long =
    Math.min(Capacity, target(exploration, destination) + Step)
  def canAdjust(data: AllData, destination: String, delta: Long): Boolean = {
    val old = target(data.exploration, destination)
    val next = old + delta
    data.ants.lineage.sharedPaths && data.unlocks.tabs.exploreTabUnlocked &&
      (delta == Step || delta == -Step) && next >= 0L && next <= Capacity &&
      (if (delta < 0L) old >= Step else
        route(destination).exists(eligible(data.exploration, _)) && data.ants.availableWorkersCount >= Step &&
          scoutsAvailableFor(data.ants, data.exploration, destination) >= requiredScouts(next))
  }
  def adjust(initial: AllData, destination: String, delta: Long): AllData = {
    if (!canAdjust(initial, destination, delta)) initial
    else {
      val data = AgingJobReplacement.cancel(initial, AntTask.TrailForager)
      val old = target(data.exploration, destination)
      val next = old + delta
      val targets = data.exploration.collectionTrails.filterNot(_.destination == destination) ++
        Option.when(next > 0L)(CollectionTrail(destination, next))
      val ants = if (delta > 0L) data.ants.unlockTask(AntTask.TrailForager).addToTask(AntTask.TrailForager, delta)
        else data.ants.removeFromTask(AntTask.TrailForager, Math.max(0L, staffed(data.ants, data.exploration, destination) - next))
      reconcile(DefaultWorkerAssignment.normalize(data.copy(ants = ants,
        exploration = data.exploration.copy(collectionTrails = targets))))
    }
  }
  def recall(initial: AllData, destination: String): AllData = {
    val data = AgingJobReplacement.cancel(initial, AntTask.TrailForager)
    reconcile(DefaultWorkerAssignment.normalize(data.copy(
      ants = data.ants.removeFromTask(AntTask.TrailForager, staffed(data.ants, data.exploration, destination)),
      exploration = data.exploration.copy(collectionTrails = data.exploration.collectionTrails.filterNot(_.destination == destination)))))
  }
  def reconcile(data: AllData): AllData = {
    val trails = if (!data.ants.lineage.sharedPaths) Nil else data.exploration.collectionTrails
      .groupBy(_.destination).toList.sortBy(_._1).flatMap { case (id, entries) =>
        val count = Math.min(Capacity, Math.max(0L, entries.head.workers)) / Step * Step
        Option.when(count >= Step && route(id).exists(eligible(data.exploration, _)))(CollectionTrail(id, count))
      }
    // Preserve route priority and release only its actual staff, not unfilled targets.
    var availableScouts = ScoutProgression.atHome(data.ants, data.exploration) / u
    var availableWorkers = workers(data.ants)
    var retainedWorkers = 0L
    val supported = trails.flatMap { trail =>
      val assigned = Math.min(trail.workers, availableWorkers)
      availableWorkers -= assigned
      val count = Math.min(trail.workers, availableScouts * GuideCapacity)
      availableScouts -= requiredScouts(count) / u
      retainedWorkers += Math.min(assigned, count)
      Option.when(count >= Step)(trail.copy(workers = count))
    }
    val excess = Math.max(0L, workers(data.ants) - retainedWorkers)
    val ready = data.copy(ants = data.ants.removeFromTask(AntTask.TrailForager, excess),
      exploration = data.exploration.copy(collectionTrails = supported))
    if (excess > 0L) DefaultWorkerAssignment.normalize(AgingJobReplacement.cancel(ready, AntTask.TrailForager))
    else if (supported.isEmpty) AgingJobReplacement.cancel(ready, AntTask.TrailForager) else ready
  }

  case class Harvest(destination: String, workers: Long, sugarCost: Long, cargo: ActionCost, status: String)
  def nominal(route: ExplorationDestination, workers: Long, upgrades: UpgradesData): (Long, Long) = {
    val denominator = route.duration * 100L
    val gain = ExplorationProgression.partyReward(
      List(route.sugarReward, route.proteinReward, route.waterReward, route.resinReward).max,
      workers, ExplorationProgression.suppliesRank(upgrades)) * efficiency(upgrades) / denominator
    val cost = (ExplorationHelper.cost(workers, route).sugar * efficiency(upgrades) + denominator - 1L) / denominator
    (gain, cost)
  }
  def applyHarvest(resources: BasicResources, harvest: Harvest): BasicResources = {
    val spent = resources.spendResources(ActionCost(sugar = harvest.sugarCost))
    spent.copy(sugar = spent.sugar + harvest.cargo.sugar, protein = spent.protein + harvest.cargo.protein,
      water = spent.water + harvest.cargo.water, resin = spent.resin + harvest.cargo.resin)
  }
  def forecast(ants: AntsData, exploration: ExplorationData, upgrades: UpgradesData, initialResources: BasicResources): List[Harvest] = {
    if (!ants.lineage.sharedPaths) return Nil
    var availableWorkers = workers(ants)
    var availableScouts = Math.max(0L, ants.scouts - exploration.explorationParties.map(_.scouts).sum)
    var resources = initialResources
    exploration.collectionTrails.sortBy(_.destination).flatMap { trail =>
      route(trail.destination).filter(eligible(exploration, _)).map { destination =>
        val assigned = Math.min(trail.workers, availableWorkers)
        availableWorkers -= assigned
        val supported = Math.min(trail.workers, availableScouts / u * GuideCapacity)
        val active = Math.min(assigned, supported)
        val guided = supported >= Step
        availableScouts = Math.max(0L, availableScouts - requiredScouts(supported))
        val (maximum, supply) = nominal(destination, active, upgrades)
        val key = ExplorationHelper.resource(destination)
        val resource = key match {
          case "sugar" => resources.sugar; case "protein" => resources.protein
          case "water" => resources.water; case _ => resources.resin
        }
        val reserved = exploration.explorationParties.map(p => ExplorationHelper.amount(p.cargo, key)).sum
        val room = Math.max(0L, resource.freeSpace - reserved)
        val amount = Math.min(maximum, Math.min(room, ExplorationMap.remaining(exploration, destination.id)))
        val cost = if (maximum == 0L || amount == 0L) 0L else (supply * amount + maximum - 1L) / maximum
        val reason = if (!guided) "Needs scouts" else if (active == 0L) "Needs workers"
          else if (room == 0L) "Storage full" else if (resources.sugar.amount < cost) "Needs sugar" else "Collecting"
        val harvest = if (reason != "Collecting") Harvest(destination.id, active, 0L, ActionCost.empty, reason)
          else Harvest(destination.id, active, cost, ExplorationHelper.resourceCost(key, amount), reason)
        resources = applyHarvest(resources, harvest)
        harvest
      }
    }
  }
  def forecastResources(ants: AntsData, exploration: ExplorationData, upgrades: UpgradesData, resources: BasicResources): BasicResources =
    forecast(ants, exploration, upgrades, resources).foldLeft(resources)(applyHarvest)
  def tick(initial: AllData): AllData = {
    val ready = reconcile(initial)
    val next = forecast(ready.ants, ready.exploration, ready.upgrades, ready.basicResources).foldLeft(ready) { (data, harvest) =>
      val route = ExplorationDestination.all.find(_.id == harvest.destination).get
      val amount = ExplorationHelper.amount(harvest.cargo, ExplorationHelper.resource(route))
      if (amount <= 0L) data else data.spendResources(ActionCost(sugar = harvest.sugarCost))
        .giveResources(sugar = harvest.cargo.sugar, protein = harvest.cargo.protein, water = harvest.cargo.water, resin = harvest.cargo.resin)
        .copy(exploration = data.exploration.copy(depleted = data.exploration.depleted.updated(route.id,
          data.exploration.depleted.getOrElse(route.id, 0L) + amount)))
    }
    reconcile(next)
  }
  def migrateLegacy(data: AllData): AllData = {
    val old = data.exploration.sugarTrail
    if (old == SugarTrail()) data else {
      val used = data.exploration.depleted.getOrElse("nectar-meadow", 0L)
      val restored = data.copy(exploration = data.exploration.copy(sugarTrail = SugarTrail(),
        depleted = data.exploration.depleted.updated("nectar-meadow", Math.max(0L, used - old.remaining))))
      if (data.exploration.collectionTrails.nonEmpty) restored else DefaultWorkerAssignment.normalize(restored.copy(
        ants = restored.ants.removeFromTask(AntTask.TrailForager, workers(restored.ants))))
    }
  }
}
