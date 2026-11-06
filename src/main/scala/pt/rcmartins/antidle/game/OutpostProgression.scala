package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object OutpostProgression {
  val Housing: Long = 50L * u
  val ForagerSlots: Long = 5L * u
  val ProteinPerWorkerSecond: Long = 400L
  val WaterPerWorkerSecond: Long = 200L
  val cost: ActionCost = ActionCost(sugar = 3000L * u, protein = 40L * u,
    knowledge = 300L * u, tunnelingSpace = 750L * u, water = 150L * u)

  def visible(exploration: ExplorationData): Boolean =
    ExplorationProgression.level(exploration.experience) >= 6 && DiscoveryProgression.DewPlants.found(exploration)

  def capacity(nest: NestAttributes): Long = if (nest.dryClearingOutpost) ForagerSlots else 0L

  def canEstablish(data: AllData): Boolean =
    canEstablish(data.nestAttributes, data.exploration, data.basicResources)

  def canEstablish(nest: NestAttributes, exploration: ExplorationData, resources: BasicResources): Boolean =
    !nest.dryClearingOutpost && visible(exploration) && resources.hasResources(cost)

  def reconcile(data: AllData): AllData =
    if (data.nestAttributes.dryClearingOutpost) data.modify(_.ants).using(_.unlockTask(AntTask.SeedForager)) else data

  def establish(data: AllData): AllData =
    if (!canEstablish(data)) data
    else DefaultWorkerAssignment.normalize(reconcile(data.spendResources(cost)
      .modify(_.nestAttributes).using(n => n.copy(dryClearingOutpost = true, maxAnts = n.maxAnts + Housing))
      .addMessage("Dry Clearing Outpost established! Assign Seed Foragers in Tasks; they need water to gather protein-rich seeds.")))

  case class Harvest(protein: Long = 0L, water: Long = 0L)

  // Make room for this tick's aphid feeding before collecting; full stores waste no water.
  def harvestPerTick(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData, resources: BasicResources): Harvest = {
    val workers = Math.min(capacity(nest), Math.max(0L, ants.taskWorkers(AntTask.SeedForager))) / u
    val fed = AphidHusbandry.proteinPerTick(ants, nest, upgrades, resources.protein.amount)
    val room = Math.max(0L, resources.protein.maxAmount - Math.max(0L, resources.protein.amount - fed))
    val ratio = ProteinPerWorkerSecond / WaterPerWorkerSecond
    val needed = room / ratio + (if (room % ratio > 0L) 1L else 0L)
    val water = Math.min(Math.min(Math.max(0L, resources.water.amount), workers * WaterPerWorkerSecond / TicksPerSecond), needed)
    Harvest(protein = Math.min(room, water * ratio), water = water)
  }
}
