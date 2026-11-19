package pt.rcmartins.antidle.game
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
object Prestige {
  def sugar(amount: Long, lineage: Lineage): Long =
    (BigInt(amount) * (100L + Math.max(0, lineage.foundings).toLong * 10L) / 100L).min(BigInt(Long.MaxValue)).toLong
  case class Understanding(id: String, title: String, cost: Long, minimumFoundings: Int, effect: String,
      owned: Lineage => Boolean, grant: Lineage => Lineage, prerequisite: Lineage => Boolean = _ => true) {
    def available(lineage: Lineage): Boolean = lineage.foundings >= minimumFoundings && prerequisite(lineage)
    def learn(lineage: Lineage): Lineage = grant(lineage).copy(dna = lineage.dna - cost)
  }
  val SharedPaths = Understanding("shared-paths", "Shared Paths", 1L, 1,
    "Automated collection: 5–50 workers per trail; 1 scout per 10 workers, rounded up; 60% of manual haul rate.",
    _.sharedPaths, _.copy(sharedPaths = true))
  val BeyondWoodland = Understanding("beyond-woodland", "Beyond the Woodland", 1L, 2,
    "Opens 3 handcrafted second-ring sites after the 6 nearby hexes. Discover Aphid Shelters, Trail Logistics and Coordinated Expeditions.",
    _.beyondWoodland, _.copy(beyondWoodland = true), _.sharedPaths)
  val SatelliteNests = Understanding("satellite-nests", "Satellite Nests", 1L, 3,
    "Dry Clearing: half the sugar and Knowledge to establish. Choose Seed Station (+50% protein, +25% water use) or Scout Station (40% shorter studies with 2–3 scouts, 5 water per study).",
    _.satelliteNests, _.copy(satelliteNests = true), _.beyondWoodland)
  val understandings: List[Understanding] = List(SharedPaths, BeyondWoodland, SatelliteNests)

  def projectedPrerequisites(lineage: Lineage, selected: Set[String]): Lineage =
    understandings.filter(u => selected(u.id)).foldLeft(lineage) { (current, understanding) =>
      if (understanding.available(current)) understanding.grant(current) else current
    }
  val DnaReward: Long = 1L
  def sugarBonus(lineage: Lineage): Long = Math.max(0, lineage.foundings).toLong * 10L
  def canFound(data: AllData): Boolean = data.ants.daughterQueens >= u &&
    data.ants.lineage.foundings < Int.MaxValue && data.ants.lineage.dna <= Long.MaxValue - DnaReward
  def plan(data: AllData, selected: Seq[String], drySeason: Boolean = false): Option[Lineage] = {
    val old = data.ants.lineage
    if ((drySeason && !DrySeasonChallenge.canEnter(old)) || old.foundings == Int.MaxValue || old.dna > Long.MaxValue - DnaReward ||
        selected.distinct.size != selected.size || selected.exists(id => !understandings.exists(_.id == id))) None
    else understandings.filter(u => selected.contains(u.id)).foldLeft(Option(old.copy(
      foundings = old.foundings + 1, dna = old.dna + DnaReward,
      drySeasonActive = drySeason, drySeasonCompleted = old.drySeasonCompleted || DrySeasonChallenge.earnsReward(data)))) {
      case (current, understanding) => current.filter(l => !understanding.owned(l) && understanding.available(l) && l.dna >= understanding.cost).map(understanding.learn)
    }
  }
  def purchase(data: AllData, id: String): AllData = {
    val lineage = data.ants.lineage
    understandings.find(_.id == id).filter(u => !u.owned(lineage) && u.available(lineage) && lineage.dna >= u.cost)
      .map(u => data.copy(ants = data.ants.copy(lineage = u.learn(lineage)))).getOrElse(data)
  }
  def found(data: AllData, selected: Seq[String] = Nil, drySeason: Boolean = false): AllData = {
    if (!canFound(data) || plan(data, selected, drySeason).isEmpty) data
    else {
      val lineage = plan(data, selected, drySeason).get
      AllData.initial.copy(world = data.world.copy(saveSeed = data.world.saveSeed + 1L),
        ants = AntsData.initial.copy(lineage = lineage),
        statistics = StatisticsData.newColony(data.statistics),
        exploration = ExplorationData.initial,
        messages = Seq(
          if (DrySeasonChallenge.earnsReward(data)) "Dry Season completed! Future repletes hold more water."
          else if (drySeason) "A daughter queen founds a colony in the Dry Season."
          else "A daughter queen founds a new colony. Your lineage endures."))
    }
  }
  def purchase(data: AllData): AllData = purchase(data, SharedPaths.id)
}
