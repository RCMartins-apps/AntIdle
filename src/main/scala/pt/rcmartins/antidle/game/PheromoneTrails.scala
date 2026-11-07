package pt.rcmartins.antidle.game
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
object PheromoneTrails {
  val markCost: ActionCost = ActionCost(sugar = 50L * u)
  val Capacity: Long = 10L * u
  val Supply: Long = 1200L * u
  def guides(ants: AntsData, exploration: ExplorationData): Long =
    if (ants.lineage.sharedPaths && exploration.sugarTrail.marked && exploration.sugarTrail.remaining > 0L) u else 0L
  def workers(ants: AntsData): Long = Math.min(Capacity, Math.max(0L, ants.taskWorkers(AntTask.TrailForager)))
  def production(ants: AntsData, exploration: ExplorationData, resources: BasicResources): Long =
    if (guides(ants, exploration) == 0L || ants.scouts - exploration.explorationParties.map(_.scouts).sum < u) 0L
    else Math.min(resources.sugar.freeSpace, Math.min(exploration.sugarTrail.remaining,
      Prestige.sugar(workers(ants) / u * 8000L / TicksPerSecond, ants.lineage)))
  def discover(data: AllData, destination: ExplorationDestination): AllData =
    if (data.ants.lineage.sharedPaths && destination == ExplorationDestination.NectarMeadow && data.exploration.sugarTrail.remaining <= 0L)
      data.copy(exploration = data.exploration.copy(sugarTrail = SugarTrail(Supply)))
        .addMessage("A nectar source was found. Mark a pheromone trail in Explore.")
    else data
  def canMark(data: AllData): Boolean = data.unlocks.tabs.exploreTabUnlocked && data.ants.lineage.sharedPaths &&
    data.exploration.sugarTrail.remaining > 0L && !data.exploration.sugarTrail.marked &&
    ScoutProgression.available(data.ants, data.exploration) >= u && data.hasResources(markCost)
  def mark(data: AllData): AllData =
    if (!canMark(data)) data else data.spendResources(markCost).copy(
      ants = data.ants.unlockTask(AntTask.TrailForager),
      exploration = data.exploration.copy(sugarTrail = data.exploration.sugarTrail.copy(marked = true)))
  def assign(initial: AllData, delta: Long): AllData = {
    val data = AgingJobReplacement.cancel(initial, AntTask.TrailForager)
    if (!data.exploration.sugarTrail.marked || !data.ants.lineage.sharedPaths) data
    else {
      val ants = if (delta > 0L) data.ants.addToTask(AntTask.TrailForager, Math.min(delta, Capacity - workers(data.ants)))
        else data.ants.removeFromTask(AntTask.TrailForager, Math.min(workers(data.ants), -delta))
      DefaultWorkerAssignment.normalize(data.copy(ants = ants))
    }
  }
  def recall(initial: AllData): AllData = {
    val data = AgingJobReplacement.cancel(initial, AntTask.TrailForager)
    DefaultWorkerAssignment.normalize(data.copy(
    ants = data.ants.removeFromTask(AntTask.TrailForager, data.ants.taskWorkers(AntTask.TrailForager)),
    exploration = data.exploration.copy(sugarTrail = data.exploration.sugarTrail.copy(marked = false))))
  }
  def reconcile(data: AllData): AllData = {
    val trail = data.exploration.sugarTrail
    if (!data.ants.lineage.sharedPaths || !trail.marked || trail.remaining <= 0L ||
      data.ants.scouts - data.exploration.explorationParties.map(_.scouts).sum < u) recall(data)
    else {
      val ready = data.copy(ants = data.ants.unlockTask(AntTask.TrailForager))
      val excess = Math.max(0L, ready.ants.taskWorkers(AntTask.TrailForager) - Capacity)
      if (excess == 0L) ready else DefaultWorkerAssignment.normalize(ready.copy(
        ants = ready.ants.removeFromTask(AntTask.TrailForager, excess)))
    }
  }
  def tick(data: AllData): AllData = {
    val ready = reconcile(data)
    val gain = production(ready.ants, ready.exploration, ready.basicResources)
    val next = ready.giveResources(sugar = gain).copy(exploration = ready.exploration.copy(
      sugarTrail = ready.exploration.sugarTrail.copy(remaining = Math.max(0L, ready.exploration.sugarTrail.remaining - gain))))
    if (gain > 0L && next.exploration.sugarTrail.remaining == 0L) recall(next).addMessage("The nectar source is depleted. Trail workers and scout released.")
    else next
  }
}
