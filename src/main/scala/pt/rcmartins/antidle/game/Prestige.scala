package pt.rcmartins.antidle.game
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
object Prestige {
  def sugar(amount: Long, lineage: Lineage): Long =
    (BigInt(amount) * (100L + Math.max(0, lineage.foundings).toLong * 10L) / 100L).min(BigInt(Long.MaxValue)).toLong
  def canFound(data: AllData): Boolean = data.ants.daughterQueens >= u
  def found(data: AllData): AllData = {
    if (!canFound(data) || data.ants.lineage.foundings == Int.MaxValue || data.ants.lineage.dna == Long.MaxValue) data
    else {
      val lineage = data.ants.lineage.copy(foundings = data.ants.lineage.foundings + 1, dna = data.ants.lineage.dna + 1L)
      AllData.initial.copy(world = data.world.copy(saveSeed = data.world.saveSeed + 1L),
        ants = AntsData.initial.copy(lineage = lineage),
        statistics = StatisticsData.newColony(data.statistics),
        exploration = ExplorationData.initial.copy(proteinDiscovered = data.exploration.proteinDiscovered),
        messages = Seq("A daughter queen founds a new colony. Your lineage endures."))
        .copy(unlocks = AllData.initial.unlocks.copy(resources = data.unlocks.resources))
    }
  }
  def purchase(data: AllData): AllData = {
    val lineage = data.ants.lineage
    if (lineage.foundings < 1 || lineage.sharedPaths || lineage.dna < 1L) data
    else data.copy(ants = data.ants.copy(lineage = lineage.copy(dna = lineage.dna - 1L, sharedPaths = true)))
  }
}
