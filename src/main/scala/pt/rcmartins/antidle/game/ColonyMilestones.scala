package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model.StatisticsData

/** Achievement tiers share one definition for progress, rewards and production. */
object ColonyMilestones {
  case class Goal(id: String, title: String, targets: List[Long], amount: StatisticsData => Long, resource: Boolean = false) {
    def progress(stats: StatisticsData): Long = Math.max(0L, amount(stats))
    def completed(stats: StatisticsData): Int = targets.count(_ <= progress(stats))
    def next(stats: StatisticsData): Option[Long] = targets.find(_ > progress(stats))
  }
  private def resource(id: String, title: String, tiers: List[Long], amount: StatisticsData => Long): Goal =
    Goal(id, title, tiers.map(_ * u), amount, resource = true)
  val goals: List[Goal] = List(
    resource("sugar", "Sugar gathered", List(100, 1000, 10000, 100000, 1000000), _.sugarU.total),
    resource("protein", "Protein gathered", List(10, 100, 1000, 10000), _.proteinU.total),
    resource("knowledge", "Knowledge gathered", List(25, 100, 500, 1000), _.knowledgeU.total),
    resource("space", "Space excavated", List(100, 1000, 10000, 100000), _.tunnelingSpaceU.total),
    resource("water", "Water gathered", List(25, 100, 1000, 10000), _.waterU.total),
    resource("resin", "Resin gathered", List(10, 100, 1000, 10000), _.resinU.total),
    Goal("workers", "Standard workers raised", List(10, 25, 50, 100, 500), s => Math.max(0L, s.workersMatured.total.toLong - s.smallWorkersMatured.total)),
    Goal("population", "Workers alive at once", List(10, 25, 50, 100, 250), _.maxAliveWorkers.total.toLong),
    Goal("scouts", "Scouts raised", List(1, 5, 10, 25), _.scoutsMatured.total.toLong),
    Goal("expeditions", "Expeditions completed", List(5, 20, 50, 100), _.explorationsCompleted.total.toLong),
    Goal("carcasses", "Carcasses retrieved", List(1, 5, 20), _.carcassesRetrieved.total.toLong),
    Goal("repletes", "Repletes promoted", List(1, 5, 10), _.repletesPromoted.total.toLong),
    Goal("contests", "Territorial contests completed", List(1, 5, 20), _.territoryContests.total.toLong),
    Goal("queens", "Daughter queens raised", List(1, 3, 10), _.daughterQueensMatured.total.toLong)
  )
  def peak(goal: Goal, stats: StatisticsData): Option[Long] = goal.id match {
    case "sugar" => Some(stats.maxSugarU.total)
    case "space" => Some(stats.maxTunnelingSpaceU.total)
    case "protein" => Some(stats.maxProteinU.total)
    case "knowledge" => Some(stats.maxKnowledgeU.total)
    case "water" => Some(stats.maxWaterU.total)
    case "resin" => Some(stats.maxResinU.total)
    case _ => None
  }
  def recordPeaks(data: pt.rcmartins.antidle.model.AllData): pt.rcmartins.antidle.model.AllData = {
    val r = data.basicResources
    val s = data.statistics
    data.copy(statistics = s.copy(
      maxSugarU = s.maxSugarU.max(r.sugar.amount), maxProteinU = s.maxProteinU.max(r.protein.amount),
      maxKnowledgeU = s.maxKnowledgeU.max(r.knowledge.amount), maxTunnelingSpaceU = s.maxTunnelingSpaceU.max(r.tunnelingSpace.amount),
      maxWaterU = s.maxWaterU.max(r.water.amount), maxResinU = s.maxResinU.max(r.resin.amount)))
  }
  def visible(goal: Goal, data: pt.rcmartins.antidle.model.AllData): Boolean = {
    val r = data.unlocks.resources
    goal.progress(data.statistics) > 0L || (goal.id match {
      case "sugar" => true
      case "space" => r.showTunnelingSpace
      case "protein" => data.exploration.proteinDiscovered
      case "knowledge" => r.showKnowledge
      case "water" => r.showWater
      case "resin" => r.showResin
      case "population" => r.showWorkers
      case "workers" => data.ants.standardWorkersUnlocked
      case "scouts" => r.showScouts
      case "expeditions" => data.unlocks.tabs.exploreTabUnlocked
      case "carcasses" => data.exploration.carcassDiscovered
      case "repletes" => r.showRepletes
      case "contests" => data.exploration.territory.held
      case "queens" => r.showDaughterQueens
      case _ => false
    })
  }
  def bonus(stats: StatisticsData): Int = goals.map(_.completed(stats)).sum
  private lazy val ratios: Vector[(BigInt, BigInt)] =
    Vector.tabulate(goals.map(_.targets.size).sum + 1)(n => BigInt(101).pow(n) -> BigInt(100).pow(n))
  def multiplier(stats: StatisticsData): String = {
    val (n, d) = ratios(bonus(stats))
    UINumbersUtils.decimalRatio(n, d)
  }
  def sugar(amount: Long, stats: StatisticsData): Long = {
    val (n, d) = ratios(bonus(stats))
    if (amount <= 0L) amount else (BigInt(amount) * n / d).min(BigInt(Long.MaxValue)).toLong
  }
  // Deposit units needed for a retained enhanced yield, rounded up for overflow refunds.
  def baseSugar(retained: Long, stats: StatisticsData): Long = {
    val (n, d) = ratios(bonus(stats))
    ((BigInt(Math.max(0L, retained)) * d + n - 1) / n).toLong
  }
}
