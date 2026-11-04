package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.{AntsData, ExplorationData, ExplorationParty, NestAttributes}

sealed abstract class ExplorationDestination(
    val id: String,
    val name: String,
    val duration: Long,
    val baseXP: Long,
    val sugarPerWorker: Long,
    val requiredLevel: Int,
    val minimumWorkers: Long,
    val scouts: Long,
    val carcassSize: Int,
    val knowledge: Long = 0L,
) {
  def isUnlocked(data: ExplorationData, ants: AntsData, nest: NestAttributes): Boolean =
    ExplorationProgression.level(data.experience) >= requiredLevel &&
      (scouts == 0 || nest.chambers.nurseryChamber.level > 0 || ants.scouts >= scouts)
}

object ExplorationDestination {
  case object Surroundings extends ExplorationDestination(
    "surroundings", "Surroundings", ExplorationTimeTicks, 10L, 10L, 1, u, 0L, 1, 2L * u)
  case object WoodlandEdge extends ExplorationDestination(
    "woodland-edge", "Woodland Edge", DistantExplorationTicks, 30L, 20L, 1, u, u, 2, 6L * u)
  case object RottenLog extends ExplorationDestination(
    "rotten-log", "Rotten Log", 60L * TicksPerSecond, 60L, 30L, 3, 5L * u, u, 0, 20L * u)

  val all: List[ExplorationDestination] = List(Surroundings, WoodlandEdge, RottenLog)

  def forParty(party: ExplorationParty): ExplorationDestination =
    all.find(_.id == party.destination).getOrElse(Surroundings)
}
