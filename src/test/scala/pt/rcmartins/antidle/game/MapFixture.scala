package pt.rcmartins.antidle.game
import pt.rcmartins.antidle.model._
object MapFixture {
  def explored: ExplorationData = ExplorationData(revealedSites = 6,
    surveys = ExplorationMap.sites.map(_.id -> 3).toMap)
}
