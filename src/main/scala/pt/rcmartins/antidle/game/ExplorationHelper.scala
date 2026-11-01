package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

import scala.util.Random

object ExplorationHelper {

  def cost(workers: Long, distant: Boolean): ActionCost =
    ActionCost(sugar = workers * (if (distant) 20 else 10), idleWorkers = workers)

  def canStart(allData: AllData, workers: Long, distant: Boolean): Boolean =
    workers >= u && workers % u == 0 && allData.unlocks.tabs.exploreTabUnlocked &&
      allData.exploration.explorationParties.size < MaxExplorationParties &&
      allData.hasResources(cost(workers, distant)) &&
      (!distant || ScoutProgression.available(allData.ants, allData.exploration) >= u)

  def start(allData: AllData, workers: Long, distant: Boolean): AllData =
    if (!canStart(allData, workers, distant)) allData
    else allData.spendResources(cost(workers, distant))
      .modify(_.ants).using(_.addToTask(AntTask.Explorer, workers))
      .modify(_.exploration.explorationParties)
      .using(ExplorationParty(
        workers, allData.currentTick,
        allData.currentTick + (if (distant) DistantExplorationTicks else ExplorationTimeTicks),
        scouts = if (distant) u else 0L,
      ) :: _)
      .modify(_.statistics.explorationsCount).using(_.addValue(1))
      .modify(_.statistics.explorationsWorkersSentU).using(_.addValue(workers))

  def exploreBonus(allData: AllData, exploration: ExplorationParty): AllData = {
    val random = new Random(allData.world.saveSeed + allData.world.currentTick + exploration.startingTick)
    val returned = allData.modify(_.ants)
      .using(_.removeFromTask(AntTask.Explorer, exploration.amountWorkers))
    val findCarcass = !allData.exploration.carcassDiscovered || exploration.scouts > 0 ||
      random.nextInt(100) < 45

    if (findCarcass && allData.exploration.carcasses.size < MaxStoredCarcasses)
      CarcassProgression.discover(returned, if (exploration.scouts > 0) 2 else 1)
    else if (findCarcass || random.nextInt(100) < 80) {
      val sugarFound = dices(random, exploration.amountWorkers / u, 20).sum * u
      returned.giveResources(sugar = sugarFound)
        .addMessage(s"Exploration: Found ${UINumbersUtils.prettyNumberStr(sugarFound)} sugar!" +
          (if (findCarcass) " Carcass stores are full; harvest one to make room." else ""))
    } else returned.addMessage("Exploration: Found nothing...")
  }

  def dice(random: Random, maxValue: Long): Long = random.nextLong(maxValue) + 1L

  def dices(random: Random, amount: Long, maxValue: Long): Seq[Long] =
    (1 to amount.toInt).map(_ => dice(random, maxValue))
}
