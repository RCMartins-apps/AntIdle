package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

/** Handcrafted first ring. Knowledge belongs to scout surveys, not worker hauls. */
object ExplorationMap {
  case class Site(id: String, name: String, knowledge: Long, routes: List[String], x: Int, y: Int)
  val sites: Vector[Site] = Vector(
    Site("woodland-edge", "Woodland Edge", 800L * u, List("woodland-edge"), 50, 18),
    Site("nectar-meadow", "Nectar Meadow", 1000L * u, List("nectar-meadow", "seed-gathering"), 77, 34),
    Site("rotten-log", "Rotten Log", 1100L * u, List("rotten-log"), 77, 66),
    Site("dry-clearing", "Dry Clearing", 1200L * u, List("scavenge-water"), 50, 82),
    Site("fallen-pine", "Fallen Pine", 1400L * u, List("collect-resin"), 23, 66),
    Site("rival-food-cache", "Rival Cache", 1500L * u, List("rival-food-cache"), 23, 34),
  )
  val SurveyTicks: Long = 180L * TicksPerSecond
  val RevealTicks: Long = 60L * TicksPerSecond
  val ScoutSugar: Long = 10L * u
  val MaxSurveyScouts: Int = 3
  // Fixed deposits, independent of seeds. Essential resources never require combat.
  val deposits: Map[String, Long] = Map("woodland-edge" -> 800L * u, "nectar-meadow" -> 20000L * u,
    "seed-gathering" -> 2000L * u, "rotten-log" -> 1200L * u, "scavenge-water" -> 6000L * u,
    "collect-resin" -> 2000L * u, "rival-food-cache" -> 1500L * u)
  def site(id: String): Site = sites.find(_.id == id).get
  def siteForRoute(id: String): Option[Site] = sites.find(_.routes.contains(id))
  def revealed(data: ExplorationData, id: String): Boolean = sites.take(data.revealedSites).exists(_.id == id)
  def studies(data: ExplorationData, id: String): Int = Math.max(0, Math.min(3, data.surveys.getOrElse(id, 0)))
  def studyReward(data: ExplorationData, id: String): Long =
    List(50L, 30L, 20L).lift(studies(data, id)).map(site(id).knowledge * _ / 100L).getOrElse(0L)
  def knowledgeRemaining(data: ExplorationData, id: String): Long =
    List(50L, 30L, 20L).drop(studies(data, id)).map(site(id).knowledge * _ / 100L).sum
  def remaining(data: ExplorationData, route: String): Long =
    Math.max(0L, deposits.getOrElse(route, 0L) - data.depleted.getOrElse(route, 0L))
  def busy(data: ExplorationData, id: String, mission: String): Boolean =
    data.explorationParties.exists(p => p.destination == id && p.mission == mission)
  def scoutCost(count: Int): ActionCost = ActionCost(sugar = count.toLong * ScoutSugar)
  def scoutDuration(count: Int, reveal: Boolean): Long =
    ((if (reveal) RevealTicks else SurveyTicks) + count - 1L) / Math.max(1, count)
  def canScout(data: AllData, id: String, count: Int): Boolean =
    data.unlocks.tabs.exploreTabUnlocked && count >= 1 && count <= MaxSurveyScouts &&
      ScoutProgression.available(data.ants, data.exploration) >= count.toLong * u && data.hasResources(scoutCost(count)) &&
      data.exploration.explorationParties.size < ExplorationProgression.maxParties(data.exploration, data.upgrades) &&
      (if (id == "surroundings") data.exploration.revealedSites < sites.size && !busy(data.exploration, id, "reveal")
      else sites.exists(_.id == id) && revealed(data.exploration, id) && studyReward(data.exploration, id) > 0L && !busy(data.exploration, id, "study"))
  def scout(data: AllData, id: String, count: Int): AllData = {
    if (!canScout(data, id, count)) data else {
      val (ready, ages) = PopulationAging.depart(data, 0L, count.toLong * u)
      val reveal = id == "surroundings"
      val party = ExplorationParty(0L, data.currentTick, data.currentTick + scoutDuration(count, reveal),
        scouts = count.toLong * u, destination = id, ages = ages, mission = if (reveal) "reveal" else "study")
      ready.spendResources(scoutCost(count)).copy(exploration = ready.exploration.copy(explorationParties = party :: ready.exploration.explorationParties),
        statistics = ready.statistics.copy(explorationsCount = ready.statistics.explorationsCount.addValue(1)))
    }
  }
  def finishScout(data: AllData, party: ExplorationParty): AllData = {
    val removed = data.copy(exploration = data.exploration.copy(explorationParties = data.exploration.explorationParties.filterNot(_ == party)),
      statistics = data.statistics.copy(explorationsCompleted = data.statistics.explorationsCompleted.addValue(1)))
    val rewarded = if (party.mission == "reveal") {
      val count = Math.min(sites.size, removed.exploration.revealedSites + 1)
      removed.copy(exploration = removed.exploration.copy(revealedSites = count)).addMessage(s"Scouts revealed ${sites(count - 1).name}.")
    } else {
      val reward = studyReward(removed.exploration, party.destination)
      val count = studies(removed.exploration, party.destination) + 1
      val studied = removed.giveResources(knowledge = reward).copy(exploration = removed.exploration.copy(
        surveys = removed.exploration.surveys.updated(party.destination, count)))
        .addMessage(s"${site(party.destination).name}: +${UINumbersUtils.prettyNumberStr(reward)} knowledge.")
      val withFind = if (party.destination == "woodland-edge" && count == 1) CarcassProgression.discover(studied, 1) else studied
      if (party.destination == "nectar-meadow" && count == 1) PheromoneTrails.discover(withFind, ExplorationDestination.NectarMeadow) else withFind
    }
    UpgradeVisibility.reconcile(DefaultWorkerAssignment.normalize(PopulationAging.returnHome(rewarded, party.ages)))
  }
  def exhausted(data: ExplorationData, id: String): Boolean =
    knowledgeRemaining(data, id) == 0L && site(id).routes.forall(remaining(data, _) == 0L)
}
