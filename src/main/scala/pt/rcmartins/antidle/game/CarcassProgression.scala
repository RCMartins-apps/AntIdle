package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object CarcassProgression {

  def harvesters(ants: AntsData): Long =
    ants.tasks.find(_._1 == AntTask.CarcassHarvester).map(_._2 / u).getOrElse(0L)

  def harvestedFood(exploration: ExplorationData, ants: AntsData): Long =
    exploration.carcasses.headOption
      .map(carcass => Math.min(carcass.foodRemaining, harvesters(ants) * HarvesterFoodPerTick))
      .getOrElse(0L)

  def foodOffset(exploration: ExplorationData, ants: AntsData): Long =
    Math.min(harvestedFood(exploration, ants), ScoutProgression.upkeepCount(ants, exploration) * antsSugarUpkeepTick)

  def discover(allData: AllData, size: Int): AllData =
    allData
      .modify(_.exploration.carcasses)
      .using(_ :+ Carcass(CarcassFood * size, CarcassFood * size, CarcassProtein * size))
      .modify(_.exploration.carcassDiscovered).setTo(true)
      .modify(_.ants).using(_.unlockTask(AntTask.CarcassHarvester))
      .addMessage("Exploration: Brought back a dead insect! Assign carcass harvesters to feed the colony and recover protein.")

  // Food offsets upkeep for this tick. Surplus harvested food is consumed, not stored.
  def harvest(allData: AllData): (AllData, Long) = {
    val food = harvestedFood(allData.exploration, allData.ants)
    val offset = foodOffset(allData.exploration, allData.ants)
    allData.exploration.carcasses match {
      case carcass :: remaining if food > 0 && food >= carcass.foodRemaining =>
        val harvested = allData
          .modify(_.exploration.carcasses).setTo(remaining)
          .modify(_.exploration.proteinDiscovered).setTo(true)
          .modify(_.basicResources.protein).using(_ + carcass.proteinReward)
          .addMessage(s"Carcass harvested: recovered ${UINumbersUtils.prettyNumberStr(carcass.proteinReward)} protein. Protein can fund a Nursery and scout eggs.")
        val released = if (remaining.isEmpty)
          harvested.modify(_.ants).using(ants =>
            ants.removeFromTask(AntTask.CarcassHarvester, harvesters(ants) * u))
        else harvested
        (released, offset)
      case carcass :: remaining if food > 0 =>
        (allData.modify(_.exploration.carcasses)
          .setTo(carcass.copy(foodRemaining = carcass.foodRemaining - food) :: remaining), offset)
      case _ => (allData, 0L)
    }
  }
}
