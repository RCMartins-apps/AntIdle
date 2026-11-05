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
    val sugarReward: Long = 0L,
    val proteinReward: Long = 0L,
    val requiredDiscovery: Option[String] = None,
    val waterReward: Long = 0L,
) {
  def isUnlocked(data: ExplorationData, ants: AntsData, nest: NestAttributes): Boolean =
    ExplorationProgression.level(data.experience) >= requiredLevel &&
      requiredDiscovery.forall(DiscoveryProgression.has(data, _)) &&
      (scouts == 0 || nest.chambers.nurseryChamber.level > 0 || ants.scouts >= scouts)
}

object ExplorationDestination {
  case object Surroundings extends ExplorationDestination(
    "surroundings", "Surroundings", ExplorationTimeTicks, 10L, 10L, 1, u, 0L, 1, 2L * u)
  case object WoodlandEdge extends ExplorationDestination(
    "woodland-edge", "Woodland Edge", DistantExplorationTicks, 30L, 20L, 1, u, u, 2, 6L * u)
  case object RottenLog extends ExplorationDestination(
    "rotten-log", "Rotten Log", 60L * TicksPerSecond, 60L, 30L, 3, 5L * u, u, 0, 20L * u)

  case object NectarMeadow extends ExplorationDestination(
    "nectar-meadow", "Nectar Meadow", 90L * TicksPerSecond, 45L, 40L, 4, 5L * u, u, 0, 8L * u, 300L * u)

  case object SeedGathering extends ExplorationDestination(
    "seed-gathering", "Seed Gathering", 120L * TicksPerSecond, 60L, 25L, 5, 10L * u, u, 0,
    knowledge = 12L * u, proteinReward = 10L * u, requiredDiscovery = Some("seed-cache"))

  case object DryClearing extends ExplorationDestination(
    "dry-clearing", "Dry Clearing", 120L * TicksPerSecond, 75L, 35L, 6, 10L * u, 2L * u, 0,
    knowledge = 25L * u, requiredDiscovery = Some("dry-trail"))
  case object ScavengeWater extends ExplorationDestination(
    "scavenge-water", "Scavenge Water", 90L * TicksPerSecond, 30L, 25L, 6, 5L * u, u, 0,
    knowledge = 5L * u, requiredDiscovery = Some("dew-plants"), waterReward = 20L * u)

  lazy val all: List[ExplorationDestination] = List(Surroundings, WoodlandEdge, RottenLog, NectarMeadow, SeedGathering, DryClearing, ScavengeWater)

  def forParty(party: ExplorationParty): ExplorationDestination =
    all.find(_.id == party.destination).getOrElse(Surroundings)
}
