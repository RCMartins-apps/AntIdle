package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

import scala.util.Random

object ExplorationHelper {
  def cost(workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): ActionCost =
    ActionCost(sugar = workers * destination.sugarPerWorker + Math.max(0, Math.min(ExplorationProgression.MaxExtraScouts, extraScouts)) * ExplorationProgression.ExtraScoutSugar, idleWorkers = workers)

  def canDepart(data: ExplorationData, ants: AntsData, upgrades: UpgradesData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Boolean =
    workers >= destination.minimumWorkers && workers <= ExplorationProgression.MaxWorkers && workers % u == 0 &&
      extraScouts >= 0 && extraScouts <= ExplorationProgression.MaxExtraScouts &&
      ExplorationProgression.level(data.experience) >= destination.requiredLevel &&
      destination.requiredDiscovery.forall(DiscoveryProgression.has(data, _)) &&
      data.explorationParties.size < ExplorationProgression.maxParties(data.experience, upgrades) &&
      ants.availableWorkersCount >= workers && ScoutProgression.available(ants, data) >= destination.scouts + extraScouts.toLong * u

  def canStart(data: AllData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Boolean =
    data.unlocks.tabs.exploreTabUnlocked && canDepart(data.exploration, data.ants, data.upgrades, workers, destination, extraScouts) &&
      data.hasResources(cost(workers, destination, extraScouts))

  def start(data: AllData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): AllData =
    if (!canStart(data, workers, destination, extraScouts)) data
    else {
      val scouts = destination.scouts + extraScouts.toLong * u
      val (ready, ages) = PopulationAging.depart(data, workers, scouts)
      DefaultWorkerAssignment.normalize(ready.spendResources(cost(workers, destination, extraScouts))
        .modify(_.ants).using(_.unlockTask(AntTask.Explorer).addToTask(AntTask.Explorer, workers))
        .modify(_.exploration.explorationParties)
        .using(ExplorationParty(
          workers, data.currentTick, data.currentTick + ExplorationProgression.duration(destination, extraScouts),
          scouts = scouts, destination = destination.id,
          suppliesLevel = ExplorationProgression.suppliesRank(data.exploration), ages = ages, extraScouts = extraScouts,
        ) :: _)
        .modify(_.statistics.explorationsCount).using(_.addValue(1))
        .modify(_.statistics.explorationsWorkersSentU).using(_.addValue(workers)))
    }

  def summary(data: ExplorationData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): String = {
    val rank = ExplorationProgression.suppliesRank(data)
    val party = ExplorationParty(workers, 0L, destination.duration,
      scouts = destination.scouts, destination = destination.id, suppliesLevel = rank)
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(data.experience, xp)
    val knowledge = ExplorationProgression.expeditionReward(destination.knowledge, experience, workers, rank)
    val sugar = if (destination.sugarReward > 0L) {
      val reward = ExplorationProgression.expeditionReward(destination.sugarReward, experience, workers, rank)
      s" · ${UINumbersUtils.prettyNumberStr(reward)} sugar"
    } else ""
    val protein = if (destination.proteinReward > 0L) {
      val reward = ExplorationProgression.expeditionReward(destination.proteinReward, experience, workers, rank)
      s" · ${UINumbersUtils.prettyNumberStr(reward)} protein"
    } else ""
    val water = if (destination.waterReward > 0L) {
      val reward = ExplorationProgression.expeditionReward(destination.waterReward, experience, workers, rank)
      s" · ${UINumbersUtils.prettyNumberStr(reward)} water"
    } else ""
    val resin = if (destination.resinReward > 0L) {
      val reward = ExplorationProgression.expeditionReward(destination.resinReward, experience, workers, rank)
      s" · ${UINumbersUtils.prettyNumberStr(reward)} resin"
    } else ""
    s"${UINumbersUtils.prettyTimeFromTicks(ExplorationProgression.duration(destination, extraScouts))} · $xp XP · ${UINumbersUtils.prettyNumberStr(knowledge)} knowledge$sugar$protein$water$resin"
  }

  def preview(data: ExplorationData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): String = {
    val rank = ExplorationProgression.suppliesRank(data)
    val party = ExplorationParty(workers, 0L, destination.duration,
      scouts = destination.scouts, destination = destination.id, suppliesLevel = rank)
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(data.experience, xp)
    def amount(base: Long): String = UINumbersUtils.prettyNumberStr(
      ExplorationProgression.expeditionReward(base, experience, workers, rank))
    val result = if (destination.resinReward > 0L)
      "Resin guaranteed; stored up to resin capacity"
    else if (destination.waterReward > 0L)
      "Water guaranteed; stored up to water capacity"
    else if (destination.proteinReward > 0L)
      "Protein guaranteed; no carcass storage needed"
    else if (destination.sugarReward > 0L)
      "Sugar guaranteed; no carcass storage needed"
    else if (destination.carcassSize == 0)
      "Knowledge guaranteed; no carcass storage needed"
    else {
      val carcass = s"${amount(CarcassProtein * destination.carcassSize)} protein + ${amount(CarcassFood * destination.carcassSize)} sugar reserve"
      if (destination == ExplorationDestination.WoodlandEdge || !data.carcassDiscovered)
        s"Carcass guaranteed: $carcass"
      else s"45% carcass ($carcass); 44% sugar (${amount(u)}–${amount(20L * u)}); 11% no resources"
    }
    s"${summary(data, workers, destination, extraScouts)} · $result"
  }

  def exploreBonus(initial: AllData, party: ExplorationParty): AllData =
    if (!initial.exploration.explorationParties.contains(party)) initial
    else finish(initial, party)

  private def finish(initial: AllData, party: ExplorationParty): AllData = {
    val data = initial.modify(_.exploration.explorationParties).using { parties =>
      val index = parties.indexOf(party)
      if (index < 0) parties else parties.patch(index, Nil, 1)
    }
    val random = new Random(data.world.saveSeed + data.world.currentTick + party.startingTick)
    val destination = ExplorationDestination.forParty(party)
    val previousXP = data.exploration.experience
    val xp = ExplorationProgression.earned(party)
    val experience = ExplorationProgression.addExperience(previousXP, xp)
    val withXP = data.modify(_.exploration.experience).setTo(experience)
      .modify(_.statistics.explorationsCompleted).using(_.addValue(1))
    val leveled = if (ExplorationProgression.level(experience) > ExplorationProgression.level(previousXP))
      withXP.addMessage(s"Exploration level ${ExplorationProgression.level(experience)}! ${ExplorationProgression.nextMilestone(experience)}")
    else withXP
    val knowledge = ExplorationProgression.expeditionReward(destination.knowledge, experience, party.amountWorkers, party.suppliesLevel)
    val returned = leveled.modify(_.ants).using(_.removeFromTask(AntTask.Explorer, party.amountWorkers))
      .giveResources(knowledge = knowledge)
    val report = s"${destination.name}: +$xp XP · +${UINumbersUtils.prettyNumberStr(knowledge)} knowledge."

    val rewarded = if (destination.resinReward > 0L) {
      val resin = ExplorationProgression.expeditionReward(destination.resinReward, experience, party.amountWorkers, party.suppliesLevel)
      val collected = Math.min(resin, returned.basicResources.resin.freeSpace)
      returned.giveResources(resin = resin).addMessage(s"$report Collected ${UINumbersUtils.prettyNumberStr(collected)} resin!")
    } else if (destination.waterReward > 0L) {
      val water = ExplorationProgression.expeditionReward(destination.waterReward, experience, party.amountWorkers, party.suppliesLevel)
      val collected = Math.min(water, returned.basicResources.water.freeSpace)
      returned.giveResources(water = water).addMessage(s"$report Collected ${UINumbersUtils.prettyNumberStr(collected)} water!")
    } else if (destination.proteinReward > 0L) {
      val protein = ExplorationProgression.expeditionReward(destination.proteinReward, experience, party.amountWorkers, party.suppliesLevel)
      val collected = Math.min(protein, returned.basicResources.protein.freeSpace)
      returned.giveResources(protein = protein)
        .modify(_.exploration.proteinDiscovered).setTo(true)
        .addMessage(s"$report Collected ${UINumbersUtils.prettyNumberStr(collected)} protein!")
    } else if (destination.sugarReward > 0L) {
      val sugar = ExplorationProgression.expeditionReward(destination.sugarReward, experience, party.amountWorkers, party.suppliesLevel)
      val collected = Math.min(sugar, returned.basicResources.sugar.freeSpace)
      returned.giveResources(sugar = sugar)
        .addMessage(s"$report Collected ${UINumbersUtils.prettyNumberStr(collected)} sugar!")
    } else if (destination.carcassSize == 0) returned.addMessage(report) else if (!data.exploration.carcassDiscovered || destination == ExplorationDestination.WoodlandEdge || random.nextInt(100) < 45)
      CarcassProgression.discover(returned, destination.carcassSize, party.amountWorkers, party.suppliesLevel)
        .addMessage(report)
    else if (random.nextInt(100) < 80) {
      val sugar = ExplorationProgression.expeditionReward(dice(random, 20L) * u, experience, party.amountWorkers, party.suppliesLevel)
      val collected = Math.min(sugar, returned.basicResources.sugar.freeSpace)
      returned.giveResources(sugar = sugar)
        .addMessage(s"$report Collected ${UINumbersUtils.prettyNumberStr(collected)} sugar!")
    } else returned.addMessage(s"$report No resources found.")
    DefaultWorkerAssignment.normalize(PopulationAging.returnHome(PheromoneTrails.discover(DiscoveryProgression.recordReturn(rewarded, destination), destination), party.ages))
  }

  def dice(random: Random, maxValue: Long): Long = random.nextLong(maxValue) + 1L
}
