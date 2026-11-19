package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.{AllData, Lineage}

object DrySeasonChallenge {
  val Title = "Dry Season"
  val RequiredRepletes = 2
  val WaterBonus: Long = 25L * u

  def canEnter(lineage: Lineage): Boolean =
    lineage.foundings >= 3 && lineage.satelliteNests && !lineage.drySeasonActive && !lineage.drySeasonCompleted

  def goalsMet(data: AllData): Boolean =
    data.nestAttributes.dryClearingOutpost && data.ants.repletes >= RequiredRepletes.toLong * u

  def earnsReward(data: AllData): Boolean = data.ants.lineage.drySeasonActive && goalsMet(data)

  def description: String =
    s"Dew Gatherers collect half as much water. Found with the Dry Clearing Outpost and $RequiredRepletes living repletes to earn +${WaterBonus / u} water capacity per replete permanently."
}
