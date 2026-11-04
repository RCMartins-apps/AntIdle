package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

import scala.util.Random

object ExplorationHelper {
  def cost(workers: Long, destination: ExplorationDestination): ActionCost =
    ActionCost(sugar = workers * destination.sugarPerWorker, idleWorkers = workers)

  def canDepart(data: ExplorationData, ants: AntsData, workers: Long, destination: ExplorationDestination): Boolean =
    workers >= destination.minimumWorkers && workers % u == 0 &&
      ExplorationProgression.level(data.experience) >= destination.requiredLevel &&
      data.explorationParties.size < ExplorationProgression.maxParties(data.experience) &&
      ants.availableWorkersCount >= workers && ScoutProgression.available(ants, data) >= destination.scouts

  def canStart(data: AllData, workers: Long, destination: ExplorationDestination): Boolean =
    data.unlocks.tabs.exploreTabUnlocked && canDepart(data.exploration, data.ants, workers, destination) &&
      data.hasResources(cost(workers, destination))

  def start(data: AllData, workers: Long, destination: ExplorationDestination): AllData =
    if (!canStart(data, workers, destination)) data
    else data.spendResources(cost(workers, destination))
      .modify(_.ants).using(_.unlockTask(AntTask.Explorer).addToTask(AntTask.Explorer, workers))
      .modify(_.exploration.explorationParties)
      .using(ExplorationParty(
        workers, data.currentTick, data.currentTick + destination.duration,
        scouts = destination.scouts, destination = destination.id,
        suppliesLevel = ExplorationProgression.suppliesRank(data.exploration),
      ) :: _)
      .modify(_.statistics.explorationsCount).using(_.addValue(1))
      .modify(_.statistics.explorationsWorkersSentU).using(_.addValue(workers))

  def summary(data: ExplorationData, workers: Long, destination: ExplorationDestination): String = {
    val rank = ExplorationProgression.suppliesRank(data)
    val party = ExplorationParty(workers, 0L, destination.duration,
      scouts = destination.scouts, destination = destination.id, suppliesLevel = rank)
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(data.experience, xp)
    val knowledge = ExplorationProgression.expeditionReward(destination.knowledge, experience, workers, rank)
    s"${UINumbersUtils.prettyTimeFromTicks(destination.duration)} · $xp XP · ${UINumbersUtils.prettyNumberStr(knowledge)} knowledge"
  }

  def preview(data: ExplorationData, workers: Long, destination: ExplorationDestination): String = {
    val rank = ExplorationProgression.suppliesRank(data)
    val party = ExplorationParty(workers, 0L, destination.duration,
      scouts = destination.scouts, destination = destination.id, suppliesLevel = rank)
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(data.experience, xp)
    def amount(base: Long): String = UINumbersUtils.prettyNumberStr(
      ExplorationProgression.expeditionReward(base, experience, workers, rank))
    val result = if (destination == ExplorationDestination.RottenLog)
      "Knowledge guaranteed; no carcass storage needed"
    else {
      val carcass = s"${amount(CarcassProtein * destination.carcassSize)} protein + ${amount(CarcassFood * destination.carcassSize)} sugar reserve"
      if (destination == ExplorationDestination.WoodlandEdge || !data.carcassDiscovered)
        s"Carcass guaranteed: $carcass"
      else s"45% carcass ($carcass); 44% sugar (${amount(u)}–${amount(20L * u)}); 11% no resources"
    }
    s"${summary(data, workers, destination)} · $result"
  }

  def exploreBonus(data: AllData, party: ExplorationParty): AllData = {
    val random = new Random(data.world.saveSeed + data.world.currentTick + party.startingTick)
    val destination = ExplorationDestination.forParty(party)
    val previousXP = data.exploration.experience
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(previousXP, xp)
    val withXP = data.modify(_.exploration.experience).setTo(experience)
    val leveled = if (ExplorationProgression.level(experience) > ExplorationProgression.level(previousXP))
      withXP.addMessage(s"Exploration level ${ExplorationProgression.level(experience)}! ${ExplorationProgression.nextMilestone(experience)}")
    else withXP
    val knowledge = ExplorationProgression.expeditionReward(destination.knowledge, experience, party.amountWorkers, party.suppliesLevel)
    val returned = leveled.modify(_.ants).using(_.removeFromTask(AntTask.Explorer, party.amountWorkers))
      .giveResources(knowledge = knowledge)
    val report = s"${destination.name}: +$xp XP · +${UINumbersUtils.prettyNumberStr(knowledge)} knowledge."

    if (destination == ExplorationDestination.RottenLog) returned.addMessage(report) else if (!data.exploration.carcassDiscovered || destination == ExplorationDestination.WoodlandEdge || random.nextInt(100) < 45)
      CarcassProgression.discover(returned, destination.carcassSize, party.amountWorkers, party.suppliesLevel)
        .addMessage(report)
    else if (random.nextInt(100) < 80) {
      val sugar = ExplorationProgression.expeditionReward(dice(random, 20L) * u, experience, party.amountWorkers, party.suppliesLevel)
      returned.giveResources(sugar = sugar)
        .addMessage(s"$report Found ${UINumbersUtils.prettyNumberStr(sugar)} sugar!")
    } else returned.addMessage(s"$report No resources found.")
  }

  def dice(random: Random, maxValue: Long): Long = random.nextLong(maxValue) + 1L
}
