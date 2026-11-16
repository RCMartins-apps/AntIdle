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
  val understandings: List[Understanding] = List(SharedPaths, BeyondWoodland)
  val DnaReward: Long = 1L
  def sugarBonus(lineage: Lineage): Long = Math.max(0, lineage.foundings).toLong * 10L
  def canFound(data: AllData): Boolean = data.ants.daughterQueens >= u &&
    data.ants.lineage.foundings < Int.MaxValue && data.ants.lineage.dna <= Long.MaxValue - DnaReward
  def plan(data: AllData, selected: Seq[String]): Option[Lineage] = {
    val old = data.ants.lineage
    if (old.foundings == Int.MaxValue || old.dna > Long.MaxValue - DnaReward ||
        selected.distinct.size != selected.size || selected.exists(id => !understandings.exists(_.id == id))) None
    else understandings.filter(u => selected.contains(u.id)).foldLeft(Option(old.copy(foundings = old.foundings + 1, dna = old.dna + DnaReward))) {
      case (current, understanding) => current.filter(l => !understanding.owned(l) && understanding.available(l) && l.dna >= understanding.cost).map(understanding.learn)
    }
  }
  def purchase(data: AllData, id: String): AllData = {
    val lineage = data.ants.lineage
    understandings.find(_.id == id).filter(u => !u.owned(lineage) && u.available(lineage) && lineage.dna >= u.cost)
      .map(u => data.copy(ants = data.ants.copy(lineage = u.learn(lineage)))).getOrElse(data)
  }
  def found(data: AllData, selected: Seq[String] = Nil): AllData = {
    if (!canFound(data) || plan(data, selected).isEmpty) data
    else {
      val lineage = plan(data, selected).get
      AllData.initial.copy(world = data.world.copy(saveSeed = data.world.saveSeed + 1L),
        ants = AntsData.initial.copy(lineage = lineage),
        statistics = StatisticsData.newColony(data.statistics),
        exploration = ExplorationData.initial.copy(proteinDiscovered = data.exploration.proteinDiscovered),
        messages = Seq("A daughter queen founds a new colony. Your lineage endures."))
        .copy(unlocks = AllData.initial.unlocks.copy(resources = data.unlocks.resources))
    }
  }
  def purchase(data: AllData): AllData = purchase(data, SharedPaths.id)
}
