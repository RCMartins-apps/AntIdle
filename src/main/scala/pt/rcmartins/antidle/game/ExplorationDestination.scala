package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.{AntsData, ExplorationData, ExplorationParty, NestAttributes}

sealed abstract class ExplorationDestination(
    val id: String,
    val name: String,
    val duration: Long,
    val sugarPerWorker: Long,
    val minimumWorkers: Long,
    val scouts: Long,
    val carcassSize: Int,
    val sugarReward: Long = 0L,
    val proteinReward: Long = 0L,
    val requiredDiscovery: Option[String] = None,
    val waterReward: Long = 0L,
    val resinReward: Long = 0L,
) {
  def actionName: String = id match {
    case "woodland-edge" => "Collect Insects"
    case "rotten-log" => "Search Deadwood"
    case "nectar-meadow" => "Collect Nectar"
    case "seed-gathering" => "Gather Seeds"
    case "scavenge-water" => "Collect Water"
    case _ => s"Collect from $name"
  }

  // Shared by the map UI and departure validation. Available workers, guides
  // and supplies are departure checks, not permanent visibility requirements.
  def progressionUnlocked(data: ExplorationData): Boolean =
    ExplorationMap.siteForRoute(id).exists(site => ExplorationMap.revealed(data, site.id) && ExplorationMap.studies(data, site.id) > 0) &&
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
    sugarPerWorker = 10L,
    minimumWorkers = u,
    scouts = 0L,
    carcassSize = 1,
  )
  case object WoodlandEdge extends ExplorationDestination(
    id = "woodland-edge",
    proteinReward = 10L * u,
    name = "Woodland Edge",
    duration = DistantExplorationTicks,
    sugarPerWorker = 20L,
    minimumWorkers = u,
    scouts = 0L,
    carcassSize = 2,
  )
  case object RottenLog extends ExplorationDestination(
    id = "rotten-log",
    proteinReward = 20L * u,
    name = "Rotten Log",
    duration = 60L * TicksPerSecond,
    sugarPerWorker = 30L,
    minimumWorkers = 5L * u,
    scouts = 0L,
    carcassSize = 0,
  )

  case object NectarMeadow extends ExplorationDestination(
    id = "nectar-meadow",
    name = "Nectar Meadow",
    duration = 90L * TicksPerSecond,
    sugarPerWorker = 10L,
    minimumWorkers = 5L * u,
    scouts = 0L,
    carcassSize = 0,
    sugarReward = 300L * u,
  )

  case object SeedGathering extends ExplorationDestination(
    id = "seed-gathering",
    name = "Seed Gathering",
    duration = 120L * TicksPerSecond,
    sugarPerWorker = 25L,
    minimumWorkers = 10L * u,
    scouts = 0L,
    carcassSize = 0,
    proteinReward = 10L * u,
    requiredDiscovery = Some("seed-cache"),
  )

  case object DryClearing extends ExplorationDestination(
    id = "dry-clearing",
    name = "Dry Clearing",
    duration = 120L * TicksPerSecond,
    sugarPerWorker = 35L,
    minimumWorkers = 10L * u,
    scouts = 0L,
    carcassSize = 0,
    requiredDiscovery = Some("dry-trail"),
  )
  case object ScavengeWater extends ExplorationDestination(
    id = "scavenge-water",
    name = "Scavenge Water",
    duration = 90L * TicksPerSecond,
    sugarPerWorker = 25L,
    minimumWorkers = 5L * u,
    scouts = 0L,
    carcassSize = 0,
    requiredDiscovery = Some("dew-plants"),
    waterReward = 20L * u,
  )

  case object FallenPine extends ExplorationDestination(
    id = "fallen-pine",
    name = "Fallen Pine",
    duration = 120L * TicksPerSecond,
    sugarPerWorker = 30L,
    minimumWorkers = 10L * u,
    scouts = 0L,
    carcassSize = 0,
  )
  case object CollectResin extends ExplorationDestination(
    id = "collect-resin",
    name = "Collect Resin",
    duration = 120L * TicksPerSecond,
    sugarPerWorker = 35L,
    minimumWorkers = 10L * u,
    scouts = 0L,
    carcassSize = 0,
    requiredDiscovery = Some("resin-seep"),
    resinReward = 5L * u,
  ) {
    override def actionName: String = "Collect Resin"
  }

  case object RivalFoodCache extends ExplorationDestination(
    id = "rival-food-cache",
    name = "Rival Food Cache",
    duration = 120L * TicksPerSecond,
    sugarPerWorker = 30L,
    minimumWorkers = 10L * u,
    scouts = u,
    carcassSize = 0,
  ) {
    override def actionName: String = "Raid Rival Food Cache"
  }

  lazy val all: List[ExplorationDestination] = List(Surroundings, WoodlandEdge, RottenLog, NectarMeadow, SeedGathering, RivalFoodCache, DryClearing, ScavengeWater, FallenPine, CollectResin)

  def forParty(party: ExplorationParty): ExplorationDestination =
    all.find(_.id == party.destination).getOrElse(Surroundings)
}
