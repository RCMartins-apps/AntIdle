package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.{CoveredTrails, SeedProcessing}

object OutpostProgression {
  val DewPerWorkerSecond: Long = 1500L
  def dewPerTick(ants: AntsData): Long = ants.taskWorkers(AntTask.WaterCollector) / u * DewPerWorkerSecond / TicksPerSecond
  val Housing: Long = 50L * u
  val ForagerSlots: Long = 5L * u
  val MaxExtensions: Int = 2
  val ExpansionHousing: Long = 25L * u
  val ProteinPerWorkerSecond: Long = 400L
  val WaterPerWorkerSecond: Long = 200L
  val cost: ActionCost = ActionCost(sugar = 3000L * u, protein = 40L * u,
    knowledge = 300L * u, tunnelingSpace = 750L * u, water = 150L * u)

  def visible(exploration: ExplorationData): Boolean =
    DiscoveryProgression.DewPlants.found(exploration)

  def extensions(nest: NestAttributes): Int =
    if (nest.dryClearingOutpost) Math.max(0, Math.min(MaxExtensions, nest.dryClearingExtensions)) else 0

  def level(nest: NestAttributes): Int = if (nest.dryClearingOutpost) 1 + extensions(nest) else 0
  def capacity(nest: NestAttributes): Long = level(nest).toLong * ForagerSlots
  def housing(nest: NestAttributes): Long = if (nest.dryClearingOutpost) Housing + extensions(nest).toLong * ExpansionHousing else 0L

  def expansionVisible(nest: NestAttributes, exploration: ExplorationData): Boolean =
    nest.dryClearingOutpost && extensions(nest) < MaxExtensions &&
      exploration.revealedSites >= 5 + extensions(nest)

  def expansionCost(nest: NestAttributes): ActionCost = {
    val rank = extensions(nest).toLong
    ActionCost(sugar = (5000L + 3000L * rank) * u, protein = (75L + 50L * rank) * u,
      knowledge = (500L + 300L * rank) * u, tunnelingSpace = (1000L + 500L * rank) * u,
      water = (250L + 100L * rank) * u)
  }

  def canExpand(nest: NestAttributes, exploration: ExplorationData, resources: BasicResources): Boolean =
    expansionVisible(nest, exploration) && resources.hasResources(expansionCost(nest))

  def expand(data: AllData): AllData =
    if (!canExpand(data.nestAttributes, data.exploration, data.basicResources)) data
    else DefaultWorkerAssignment.normalize(reconcile(data.spendResources(expansionCost(data.nestAttributes))
      .modify(_.nestAttributes).using(n => n.copy(dryClearingExtensions = extensions(n) + 1, maxAnts = n.maxAnts + ExpansionHousing))
      .addMessage(s"Dry Clearing Outpost expanded to level ${level(data.nestAttributes) + 1}: +25 colony capacity and 5 Seed Forager slots.")))

  def proteinPerWorkerSecond(upgrades: UpgradesData): Long =
    if (upgrades(SeedProcessing).unlocked) ProteinPerWorkerSecond * 3L / 2L else ProteinPerWorkerSecond

  def waterPerWorkerSecond(upgrades: UpgradesData): Long =
    if (upgrades(CoveredTrails).unlocked) WaterPerWorkerSecond * 3L / 4L else WaterPerWorkerSecond

  def canEstablish(data: AllData): Boolean =
    canEstablish(data.nestAttributes, data.exploration, data.basicResources)

  def canEstablish(nest: NestAttributes, exploration: ExplorationData, resources: BasicResources): Boolean =
    !nest.dryClearingOutpost && visible(exploration) && resources.hasResources(cost)

  def reconcile(data: AllData): AllData = {
    val ready = data.modify(_.nestAttributes.dryClearingExtensions).setTo(extensions(data.nestAttributes))
    if (ready.nestAttributes.dryClearingOutpost) ready.modify(_.ants).using(_.unlockTask(AntTask.SeedForager)) else ready
  }

  def establish(data: AllData): AllData =
    if (!canEstablish(data)) data
    else DefaultWorkerAssignment.normalize(reconcile(data.spendResources(cost)
      .modify(_.nestAttributes).using(n => n.copy(dryClearingOutpost = true, dryClearingExtensions = 0, maxAnts = n.maxAnts + Housing))
      .addMessage("Dry Clearing Outpost established! Assign Seed Foragers in Tasks; they need water to gather protein-rich seeds.")))

  case class Harvest(protein: Long = 0L, water: Long = 0L)

  // Make room for this tick's aphid feeding before collecting; full stores waste no water.
  def harvestPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, resources: BasicResources): Harvest = {
    val workers = Math.min(capacity(nest), Math.max(0L, ants.taskWorkers(AntTask.SeedForager))) / u
    val fed = AphidHusbandry.proteinPerTick(ants, nest, upgrades, resources.protein.amount)
    val room = Math.max(0L, resources.protein.maxAmount - Math.max(0L, resources.protein.amount - fed))
    val demand = workers * waterPerWorkerSecond(upgrades) / TicksPerSecond
    val potential = workers * proteinPerWorkerSecond(upgrades) / TicksPerSecond
    if (demand == 0L || potential == 0L) Harvest()
    else {
      val availableWater = Math.min(Math.max(0L, resources.water.amount), demand)
      val protein = Math.min(room, availableWater * potential / demand)
      val water = (protein * demand + potential - 1L) / potential
      Harvest(protein = protein, water = water)
    }
  }
}
