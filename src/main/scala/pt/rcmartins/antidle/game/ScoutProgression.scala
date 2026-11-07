package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object ScoutProgression {

  val eggCost: ActionCost = ActionCost(sugar = ScoutSugarCost, protein = ScoutProteinCost)

  def atHome(ants: AntsData, exploration: ExplorationData): Long =
    Math.max(0L, ants.scouts - exploration.explorationParties.map(_.scouts).sum)

  def available(ants: AntsData, exploration: ExplorationData): Long =
    Math.max(0L, atHome(ants, exploration) - PheromoneTrails.guides(ants, exploration))

  // Like expedition workers, scouts away from the nest do not consume colony food.
  def upkeepCount(ants: AntsData, exploration: ExplorationData): Long =
    ants.upkeepWorkersCount - exploration.explorationParties.map(_.scouts).sum / u

  def canRaise(allData: AllData): Boolean =
    canRaise(allData.ants, allData.nestAttributes, allData.basicResources)

  def canRaise(ants: AntsData, nest: NestAttributes, resources: BasicResources): Boolean = {
    nest.chambers.nurseryChamber.level > 0 &&
      ants.eggsAndLarvae.count(_.isEgg) < nest.maxEggsCount &&
      ants.populationCount < nest.maxAntsCount && resources.hasResources(eggCost)
  }

  def raise(allData: AllData): AllData =
    if (!canRaise(allData)) allData
    else allData.spendResources(eggCost)
      .modify(_.ants.eggsAndLarvae)
      .using(_ :+ AntBrood.ScoutEgg(allData.currentTick))
      .modify(_.statistics.eggsCount).using(_.addValue(1))

  def growBrood(allData: AllData): AllData = {
    val (grown, growing) = allData.ants.eggsAndLarvae.partition {
      case brood: AntBrood.Egg => allData.currentTick >= brood.initialTick + ColonyDevelopment.growthTicks(brood, allData.upgrades)
      case brood: AntBrood.ScoutEgg => allData.currentTick >= brood.initialTick + ColonyDevelopment.growthTicks(brood, allData.upgrades)
      case _ => false
    }
    val newWorkers = grown.count(_.isInstanceOf[AntBrood.Egg]) * u
    val newScouts = grown.count(_.isInstanceOf[AntBrood.ScoutEgg]) * u
    val updated = allData
      .modify(_.ants.eggsAndLarvae).setTo(growing)
      .modify(_.ants.workers).using(_ + newWorkers)
      .modify(_.ants.scouts).using(_ + newScouts)
      .modify(_.statistics.workersMatured).using(_.addValue(newWorkers / u))
      .modify(_.statistics.scoutsMatured).using(_.addValue(newScouts / u))
      .modify(_.statistics.maxAliveWorkers)
      .usingIf(newWorkers > 0)(_.max(allData.ants.workersCount + (newWorkers / u).toInt))
    DefaultWorkerAssignment.normalize(PopulationAging.reconcile(updated))
  }
}
