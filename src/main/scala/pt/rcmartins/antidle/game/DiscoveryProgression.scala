package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._

object DiscoveryProgression {
  case class Discovery(id: String, title: String, destination: String, trips: Int, description: String) {
    def progress(data: ExplorationData): Int = Math.max(0, Math.min(trips, data.surveys.getOrElse(destination, 0)))
    def found(data: ExplorationData): Boolean = progress(data) >= trips
  }

  val WildAphids = Discovery("wild-aphids", "Wild Aphids", "woodland-edge", 3,
    "Research Aphid Tending in Upgrades to unlock a new sugar-producing worker task.")
  val SeedCache = Discovery("seed-cache", "Seed Cache", "nectar-meadow", 2,
    "Reveals Seed Gathering.")
  val DryTrail = Discovery("dry-trail", "Dry Trail", "rotten-log", 2,
    "Reveals a route through the dry ground.")
  val DewPlants = Discovery("dew-plants", "Dew-covered Plants", "dry-clearing", 3,
    "Unlocks water scavenging, Replete Storage research and establishing the Dry Clearing Outpost in Nest.")
  val ResinSeep = Discovery("resin-seep", "Resin Seep", "fallen-pine", 3,
    "Unlocks resin collection and Royal Nursery Planning.")
  val ContestedGround = Discovery("contested-ground", "Contested Foraging Ground", "rival-food-cache", 1,
    "A rival colony contests a rich food source. Hold it from Territory for recurring protein and disclosed losses.")
  val all: List[Discovery] = List(ContestedGround, WildAphids, SeedCache, DryTrail, DewPlants, ResinSeep)

  def hasUnfound(data: ExplorationData, destination: ExplorationDestination): Boolean =
    all.exists(discovery => discovery.destination == destination.id && !discovery.found(data))

  def has(data: ExplorationData, id: String): Boolean = all.exists(d => d.id == id && d.found(data))

  def recordReturn(data: AllData, destination: ExplorationDestination): AllData =
    all.filter(_.destination == destination.id).foldLeft(data) { (current, discovery) =>
      if (discovery.found(current.exploration)) current
      else {
        val progress = discovery.progress(current.exploration) + 1
        val updated = current.modify(_.exploration.surveys).using(_.updated(destination.id, progress))
        if (progress == discovery.trips)
          updated.addMessage(s"Discovery: ${discovery.title}! ${discovery.description}")
        else updated
      }
    }

  def unlockTasks(initial: AllData): AllData = {
    val data = if (DewPlants.found(initial.exploration)) initial.copy(ants = initial.ants.unlockTask(AntTask.WaterCollector)) else initial
    if (data.upgrades(UpgradesData.UpgradeType.AphidTending).unlocked)
      data.modify(_.ants).using(_.unlockTask(AntTask.AphidTender))
    else data
  }
}
