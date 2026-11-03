package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object CarcassProgression {

  def occupiedSlots(exploration: ExplorationData): Int =
    exploration.carcasses.size + exploration.retrievals.size

  def canRetrieve(ants: AntsData, nest: NestAttributes, exploration: ExplorationData, carcass: Carcass): Boolean =
    occupiedSlots(exploration) < nest.maxCarcasses && ants.idleWorkersCount >= carcass.requiredWorkers

  def retrieve(allData: AllData, index: Int): AllData =
    allData.exploration.discoveredCarcasses.lift(index) match {
      case Some(carcass) if canRetrieve(allData.ants, allData.nestAttributes, allData.exploration, carcass) =>
        allData
          .modify(_.ants).using(_.unlockTask(AntTask.CarcassCarrier).addToTask(AntTask.CarcassCarrier, carcass.requiredWorkers))
          .modify(_.exploration.discoveredCarcasses).using(_.patch(index, Nil, 1))
          .modify(_.exploration.retrievals).using(_ :+ CarcassRetrieval(carcass, allData.currentTick + carcass.retrievalTicks))
      case _ => allData
    }

  def discover(allData: AllData, size: Int): AllData =
    allData
      .modify(_.exploration.discoveredCarcasses)
      .using(_ :+ Carcass(ExplorationProgression.reward(CarcassFood * size, allData.exploration.experience),
        ExplorationProgression.reward(CarcassFood * size, allData.exploration.experience),
        ExplorationProgression.reward(CarcassProtein * size, allData.exploration.experience),
        CarcassCarriers * size, CarcassRetrievalTicks * size))
      .modify(_.exploration.carcassDiscovered).setTo(true)
      .addMessage("Exploration: Found a dead insect.")

  def returnCarcasses(allData: AllData): AllData = {
    val (finished, ongoing) = allData.exploration.retrievals.partition(_.targetTick <= allData.currentTick)
    finished.foldLeft(allData.modify(_.exploration.retrievals).setTo(ongoing)) { (data, retrieval) =>
      data
        .modify(_.ants).using(_.removeFromTask(AntTask.CarcassCarrier, retrieval.carcass.requiredWorkers))
        .modify(_.exploration.carcasses).using(_ :+ retrieval.carcass)
        .modify(_.basicResources.protein).using(_ + retrieval.carcass.proteinReward)
        .modify(_.exploration.proteinDiscovered).setTo(true)
        .addMessage(s"Carcass returned: +${UINumbersUtils.prettyNumberStr(retrieval.carcass.proteinReward)} protein.")
    }
  }

  def foodOffset(exploration: ExplorationData, ants: AntsData): Long =
    Math.min(exploration.carcasses.map(_.foodRemaining).sum,
      ScoutProgression.upkeepCount(ants, exploration) * antsSugarUpkeepTick)

  // Consume the oldest reserves first, including across carcasses within one tick.
  def consumeFood(allData: AllData): (AllData, Long) = {
    val offset = foodOffset(allData.exploration, allData.ants)
    val (_, remaining) = allData.exploration.carcasses.foldLeft((offset, List.empty[Carcass])) {
      case ((needed, kept), carcass) =>
        val consumed = Math.min(needed, carcass.foodRemaining)
        val food = carcass.foodRemaining - consumed
        (needed - consumed, if (food > 0) carcass.copy(foodRemaining = food) :: kept else kept)
    }
    (allData.modify(_.exploration.carcasses).setTo(remaining.reverse), offset)
  }
}
