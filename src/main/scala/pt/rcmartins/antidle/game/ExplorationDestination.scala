package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.ExplorationParty

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
    val colonyPoints: Long = 0L,
)

object ExplorationDestination {
  case object Surroundings extends ExplorationDestination(
    "surroundings", "Surroundings", ExplorationTimeTicks, 10L, 10L, 1, u, 0L, 1)
  case object WoodlandEdge extends ExplorationDestination(
    "woodland-edge", "Woodland Edge", DistantExplorationTicks, 30L, 20L, 1, u, u, 2)
  case object RottenLog extends ExplorationDestination(
    "rotten-log", "Rotten Log", 60L * TicksPerSecond, 60L, 30L, 3, 5L * u, u, 0, 20L * u)

  val all: List[ExplorationDestination] = List(Surroundings, WoodlandEdge, RottenLog)

  // Legacy saves identify woodland trips by their scout.
  def forParty(party: ExplorationParty): ExplorationDestination =
    all.find(_.id == party.destination).getOrElse(if (party.scouts > 0) WoodlandEdge else Surroundings)
}
