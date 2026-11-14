package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.AphidTending

/** Living aphids occupy chamber places; relocation gives no Knowledge or resource haul. */
object AphidRelocation {
  val Batch: Long = AphidHusbandry.AphidsPerTender
  val Workers: Long = 5L * u
  val Scouts: Long = u
  val Duration: Long = 30L * TicksPerSecond
  val Cost: ActionCost = ActionCost(sugar = 10L * u, idleWorkers = Workers)

  def visible(data: AllData): Boolean = data.unlocks.tabs.exploreTabUnlocked &&
    data.upgrades(AphidTending).unlocked && DiscoveryProgression.WildAphids.found(data.exploration)
  def reserved(exploration: ExplorationData): Long = exploration.explorationParties.filter(_.mission == "aphids").map(_.aphids).sum
  def free(nest: NestAttributes, exploration: ExplorationData): Long =
    Math.max(0L, AphidHusbandry.housing(nest) - nest.aphids - reserved(exploration))
  def canStart(data: AllData): Boolean = visible(data) && free(data.nestAttributes, data.exploration) >= Batch &&
    data.hasResources(Cost) && ScoutProgression.available(data.ants, data.exploration) >= Scouts &&
    data.exploration.explorationParties.size < ExplorationProgression.maxParties(data.exploration, data.upgrades)
  def start(data: AllData): AllData = if (!canStart(data)) data else {
    val (ready, ages) = PopulationAging.depart(data, Workers, Scouts)
    val party = ExplorationParty(Workers, data.currentTick, data.currentTick + Duration,
      scouts = Scouts, destination = "woodland-edge", ages = ages, mission = "aphids", aphids = Batch)
    val spent = ready.spendResources(Cost)
    DefaultWorkerAssignment.normalize(spent.copy(
      ants = spent.ants.unlockTask(AntTask.Explorer).addToTask(AntTask.Explorer, Workers),
      exploration = spent.exploration.copy(explorationParties = party :: spent.exploration.explorationParties),
      statistics = spent.statistics.copy(explorationsCount = spent.statistics.explorationsCount.addValue(1),
        explorationsWorkersSentU = spent.statistics.explorationsWorkersSentU.addValue(Workers))))
  }
  def finish(data: AllData, party: ExplorationParty): AllData = {
    if (party.mission != "aphids" || !data.exploration.explorationParties.contains(party)) data
    else {
      val received = Math.min(party.aphids, Math.max(0L, AphidHusbandry.housing(data.nestAttributes) - data.nestAttributes.aphids))
      val returned = data.copy(
        ants = data.ants.removeFromTask(AntTask.Explorer, party.amountWorkers),
        nestAttributes = data.nestAttributes.copy(aphids = data.nestAttributes.aphids + received),
        exploration = data.exploration.copy(explorationParties = data.exploration.explorationParties.patch(data.exploration.explorationParties.indexOf(party), Nil, 1)),
        statistics = data.statistics.copy(explorationsCompleted = data.statistics.explorationsCompleted.addValue(1)))
      DefaultWorkerAssignment.normalize(PopulationAging.returnHome(returned, party.ages))
        .addMessage(s"Woodland Edge: relocated $received aphids.")
    }
  }
}
