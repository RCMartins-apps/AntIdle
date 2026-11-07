package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object CarcassProgression {

  def occupiedSlots(exploration: ExplorationData): Int =
    exploration.carcasses.size + exploration.retrievals.size

  def canRetrieve(ants: AntsData, nest: NestAttributes, exploration: ExplorationData, carcass: Carcass): Boolean =
    occupiedSlots(exploration) < nest.maxCarcasses && ants.availableWorkersCount >= carcass.requiredWorkers

  def retrieve(allData: AllData, index: Int): AllData =
    allData.exploration.discoveredCarcasses.lift(index) match {
      case Some(carcass) if canRetrieve(allData.ants, allData.nestAttributes, allData.exploration, carcass) =>
        val (ready, ages) = PopulationAging.depart(allData, carcass.requiredWorkers)
        DefaultWorkerAssignment.normalize(ready
          .modify(_.ants).using(_.unlockTask(AntTask.CarcassCarrier).addToTask(AntTask.CarcassCarrier, carcass.requiredWorkers))
          .modify(_.exploration.discoveredCarcasses).using(_.patch(index, Nil, 1))
          .modify(_.exploration.retrievals).using(_ :+ CarcassRetrieval(carcass, allData.currentTick + carcass.retrievalTicks, ages)))
      case _ => allData
    }

  def discover(allData: AllData, size: Int, workers: Long = u, supplies: Int = 0): AllData =
    allData
      .modify(_.exploration.discoveredCarcasses)
      .using(_ :+ Carcass(ExplorationProgression.expeditionReward(CarcassFood * size, allData.exploration.experience, workers, supplies),
        ExplorationProgression.expeditionReward(CarcassFood * size, allData.exploration.experience, workers, supplies),
        ExplorationProgression.expeditionReward(CarcassProtein * size, allData.exploration.experience, workers, supplies),
        CarcassCarriers * size, CarcassRetrievalTicks * size))
      .modify(_.exploration.carcassDiscovered).setTo(true)
      .addMessage("Exploration: Found a dead insect.")

  def returnCarcasses(allData: AllData): AllData = {
    val (finished, ongoing) = allData.exploration.retrievals.partition(_.targetTick <= allData.currentTick)
    DefaultWorkerAssignment.normalize(finished.foldLeft(allData.modify(_.exploration.retrievals).setTo(ongoing)) { (data, retrieval) =>
      val collected = Math.min(retrieval.carcass.proteinReward, data.basicResources.protein.freeSpace)
      val returned = data
        .modify(_.ants).using(_.removeFromTask(AntTask.CarcassCarrier, retrieval.carcass.requiredWorkers))
        .modify(_.exploration.carcasses).using(_ :+ retrieval.carcass)
        .giveResources(protein = retrieval.carcass.proteinReward)
        .modify(_.statistics.carcassesRetrieved).using(_.addValue(1))
        .modify(_.exploration.proteinDiscovered).setTo(true)
        .addMessage(s"Carcass returned: +${UINumbersUtils.prettyNumberStr(collected)} protein.")
      PopulationAging.returnHome(returned, retrieval.ages)
    })
  }

  def foodOffset(exploration: ExplorationData, ants: AntsData): Long =
    Math.min(exploration.carcasses.map(_.foodRemaining).sum,
      ScoutProgression.upkeepPerTick(ants, exploration))

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
