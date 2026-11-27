package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._

private[game] object SugarProduction {
  final case class Contributions(
      foraging: Long,
      research: Long,
      inherited: Long,
      aphids: Long,
      milestones: Long
  ) {
    def total: Long = foraging + research + inherited + aphids + milestones
  }

  def contributions(
      ants: AntsData,
      nest: NestAttributes,
      upgrades: UpgradesData,
      availableProtein: Long,
      stats: StatisticsData,
  ): Contributions = {
    val base = Foraging.mixedBasePerTick(ants, upgrades)
    val researched = Foraging.researchedProduction(base, upgrades)
    val inherited = Prestige.sugar(researched, ants.lineage)
    val aphids = AphidHusbandry.productionPerTick(ants, nest, upgrades, availableProtein)
    val totalBeforeMilestones = inherited + aphids
    Contributions(
      base,
      researched - base,
      inherited - researched,
      aphids,
      ColonyMilestones.sugar(totalBeforeMilestones, stats) - totalBeforeMilestones
    )
  }

}
