package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._

object DiscoveryProgression {
  case class Discovery(id: String, title: String, destination: String, trips: Int, description: String) {
    def progress(data: ExplorationData): Int = Math.max(0, Math.min(trips, data.surveys.getOrElse(destination, 0)))
    def found(data: ExplorationData): Boolean = progress(data) >= trips
  }

  val WildAphids = Discovery("wild-aphids", "Wild Aphids", "woodland-edge", 5,
    "Research Aphid Tending in Upgrades to unlock a new sugar-producing worker task.")
  val SeedCache = Discovery("seed-cache", "Seed Cache", "nectar-meadow", 3,
    s"Reveals Seed Gathering at exploration level ${ExplorationDestination.SeedGathering.requiredLevel}: guaranteed protein without carcass retrieval.")
  val DryTrail = Discovery("dry-trail", "Dry Trail", "rotten-log", 4,
    s"Reveals the Dry Clearing at exploration level ${ExplorationDestination.DryClearing.requiredLevel}. Survey it to locate water sources.")
  val DewPlants = Discovery("dew-plants", "Dew-covered Plants", "dry-clearing", 3,
    "Unlocks water scavenging, Replete Storage research and establishing the Dry Clearing Outpost in Nest.")
  val ResinSeep = Discovery("resin-seep", "Resin Seep", "fallen-pine", 3,
    s"Reveals Collect Resin expeditions and Royal Nursery Planning research at exploration level ${UpgradesData.UpgradeType.RoyalNurseryPlanning.definition.requirements.minimumExplorationLevel}.")
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

  def unlockTasks(data: AllData): AllData =
    if (data.upgrades(UpgradesData.UpgradeType.AphidTending).unlocked)
      data.modify(_.ants).using(_.unlockTask(AntTask.AphidTender))
    else data
}
