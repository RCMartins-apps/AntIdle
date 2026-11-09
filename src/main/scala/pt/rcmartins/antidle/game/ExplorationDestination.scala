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
    val resinReward: Long = 0L,
) {
  def actionName: String = s"Explore $name"

  // Shared by the route UI and departure validation. Scout training affects
  // visibility below; available scouts/workers/supplies are departure checks.
  def progressionUnlocked(data: ExplorationData): Boolean =
    ExplorationProgression.level(data.experience) >= requiredLevel &&
      requiredDiscovery.forall(DiscoveryProgression.has(data, _))

  def isUnlocked(data: ExplorationData, ants: AntsData, nest: NestAttributes): Boolean =
    progressionUnlocked(data) &&
      (scouts == 0 || nest.chambers.nurseryChamber.level > 0 || ants.scouts >= scouts)
}

/** Destination tuning catalog. Durations are ticks; resource rewards/counts use u.
  * sugarPerWorker is whole sugar per worker (multiplied by a worker count in u).
  * Keep IDs stable: saves, surveys and discovery prerequisites refer to them.
  */
object ExplorationDestination {
  case object Surroundings extends ExplorationDestination(
    id = "surroundings",
    name = "Surroundings",
    duration = ExplorationTimeTicks,
    baseXP = 10L,
    sugarPerWorker = 10L,
    requiredLevel = 1,
    minimumWorkers = u,
    scouts = 0L,
    carcassSize = 1,
    knowledge = 2L * u,
  )
  case object WoodlandEdge extends ExplorationDestination(
    id = "woodland-edge",
    name = "Woodland Edge",
    duration = DistantExplorationTicks,
    baseXP = 30L,
    sugarPerWorker = 20L,
    requiredLevel = 1,
    minimumWorkers = u,
    scouts = u,
    carcassSize = 2,
    knowledge = 6L * u,
  )
  case object RottenLog extends ExplorationDestination(
    id = "rotten-log",
    name = "Rotten Log",
    duration = 60L * TicksPerSecond,
    baseXP = 60L,
    sugarPerWorker = 30L,
    requiredLevel = 3,
    minimumWorkers = 5L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 20L * u,
  )

  case object NectarMeadow extends ExplorationDestination(
    id = "nectar-meadow",
    name = "Nectar Meadow",
    duration = 90L * TicksPerSecond,
    baseXP = 45L,
    sugarPerWorker = 40L,
    requiredLevel = 4,
    minimumWorkers = 5L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 8L * u,
    sugarReward = 300L * u,
  )

  case object SeedGathering extends ExplorationDestination(
    id = "seed-gathering",
    name = "Seed Gathering",
    duration = 120L * TicksPerSecond,
    baseXP = 60L,
    sugarPerWorker = 25L,
    requiredLevel = 5,
    minimumWorkers = 10L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 12L * u,
    proteinReward = 10L * u,
    requiredDiscovery = Some("seed-cache"),
  )

  case object DryClearing extends ExplorationDestination(
    id = "dry-clearing",
    name = "Dry Clearing",
    duration = 120L * TicksPerSecond,
    baseXP = 75L,
    sugarPerWorker = 35L,
    requiredLevel = 6,
    minimumWorkers = 10L * u,
    scouts = 2L * u,
    carcassSize = 0,
    knowledge = 25L * u,
    requiredDiscovery = Some("dry-trail"),
  )
  case object ScavengeWater extends ExplorationDestination(
    id = "scavenge-water",
    name = "Scavenge Water",
    duration = 90L * TicksPerSecond,
    baseXP = 30L,
    sugarPerWorker = 25L,
    requiredLevel = 6,
    minimumWorkers = 5L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 5L * u,
    requiredDiscovery = Some("dew-plants"),
    waterReward = 20L * u,
  )

  case object FallenPine extends ExplorationDestination(
    id = "fallen-pine",
    name = "Fallen Pine",
    duration = 120L * TicksPerSecond,
    baseXP = 80L,
    sugarPerWorker = 30L,
    requiredLevel = 7,
    minimumWorkers = 10L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 30L * u,
  )
  case object CollectResin extends ExplorationDestination(
    id = "collect-resin",
    name = "Collect Resin",
    duration = 120L * TicksPerSecond,
    baseXP = 50L,
    sugarPerWorker = 35L,
    requiredLevel = 7,
    minimumWorkers = 10L * u,
    scouts = 2L * u,
    carcassSize = 0,
    knowledge = 10L * u,
    requiredDiscovery = Some("resin-seep"),
    resinReward = 5L * u,
  ) {
    override def actionName: String = "Collect Resin"
  }

  case object RivalFoodCache extends ExplorationDestination(
    id = "rival-food-cache",
    name = "Rival Food Cache",
    duration = 120L * TicksPerSecond,
    baseXP = 60L,
    sugarPerWorker = 30L,
    requiredLevel = 5,
    minimumWorkers = 10L * u,
    scouts = u,
    carcassSize = 0,
    knowledge = 15L * u,
  ) {
    override def actionName: String = "Raid Rival Food Cache"
  }

  lazy val all: List[ExplorationDestination] = List(Surroundings, WoodlandEdge, RottenLog, NectarMeadow, SeedGathering, RivalFoodCache, DryClearing, ScavengeWater, FallenPine, CollectResin)

  def forParty(party: ExplorationParty): ExplorationDestination =
    all.find(_.id == party.destination).getOrElse(Surroundings)
}
