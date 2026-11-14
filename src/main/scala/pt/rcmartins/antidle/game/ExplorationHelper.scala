package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object ExplorationHelper {
  def cost(workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): ActionCost =
    ActionCost(sugar = workers * destination.sugarPerWorker + Math.max(0, Math.min(ExplorationProgression.MaxExtraScouts, extraScouts)) * ExplorationProgression.ExtraScoutSugar, idleWorkers = workers)
  def resource(destination: ExplorationDestination): String =
    if (destination.waterReward > 0L) "water" else if (destination.resinReward > 0L) "resin"
    else if (destination.sugarReward > 0L) "sugar" else "protein"
  def amount(cargo: ActionCost, key: String): Long = key match {
    case "sugar" => cargo.sugar; case "protein" => cargo.protein; case "water" => cargo.water; case "resin" => cargo.resin
  }
  def resourceCost(key: String, value: Long): ActionCost = key match {
    case "sugar" => ActionCost(sugar = value); case "protein" => ActionCost(protein = value)
    case "water" => ActionCost(water = value); case "resin" => ActionCost(resin = value)
  }
  def free(data: AllData, key: String): Long = {
    val space = key match {
      case "sugar" => data.basicResources.sugar.freeSpace; case "protein" => data.basicResources.protein.freeSpace
      case "water" => data.basicResources.water.freeSpace; case "resin" => data.basicResources.resin.freeSpace
    }
    Math.max(0L, space - data.exploration.explorationParties.map(p => amount(p.cargo, key)).sum)
  }
  def load(data: AllData, workers: Long, destination: ExplorationDestination, raidReward: Option[Long] = None): ActionCost = {
    val base = if (RaidProgression.isRaid(destination)) raidReward.getOrElse(RaidProgression.rewardRange(workers)._1)
      else ExplorationProgression.partyReward(Math.max(Math.max(destination.sugarReward, destination.proteinReward),
        Math.max(destination.waterReward, destination.resinReward)), workers, ExplorationProgression.suppliesRank(data.upgrades))
    val key = resource(destination)
    resourceCost(key, Math.min(base, Math.min(ExplorationMap.remaining(data.exploration, destination.id), free(data, key))))
  }
  def canDepart(data: ExplorationData, ants: AntsData, upgrades: UpgradesData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Boolean =
    workers >= destination.minimumWorkers && workers <= ExplorationProgression.MaxWorkers && workers % ExplorationProgression.WorkerStep == 0 &&
      extraScouts >= 0 && extraScouts <= ExplorationProgression.MaxExtraScouts && destination.progressionUnlocked(data) &&
      ExplorationMap.remaining(data, destination.id) > 0L &&
      data.explorationParties.size < ExplorationProgression.maxParties(data, upgrades) &&
      ants.availableWorkersCount >= workers && ScoutProgression.available(ants, data) >= destination.scouts + extraScouts.toLong * u
  def canStart(data: AllData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): Boolean =
    data.unlocks.tabs.exploreTabUnlocked && canDepart(data.exploration, data.ants, data.upgrades, workers, destination, extraScouts) &&
      data.hasResources(cost(workers, destination, extraScouts)) && amount(load(data, workers, destination), resource(destination)) > 0L
  def start(data: AllData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): AllData =
    if (!canStart(data, workers, destination, extraScouts)) data else {
      val scouts = destination.scouts + extraScouts.toLong * u
      val (ready, ages) = PopulationAging.depart(data, workers, scouts)
      val party = ExplorationParty(workers, data.currentTick, data.currentTick + ExplorationProgression.duration(destination, extraScouts),
        scouts = scouts, destination = destination.id, suppliesLevel = ExplorationProgression.suppliesRank(data.upgrades), ages = ages, extraScouts = extraScouts)
      val outcome = Option.when(RaidProgression.isRaid(destination))(RaidProgression.roll(RaidProgression.seed(ready, party), workers))
      val cargo = load(data, workers, destination, outcome.map(_.protein))
      val departed = party.copy(raidOutcome = outcome, cargo = cargo)
      val spent = ready.spendResources(cost(workers, destination, extraScouts))
      val depleted = data.exploration.depleted.getOrElse(destination.id, 0L) + amount(cargo, resource(destination))
      DefaultWorkerAssignment.normalize(spent.copy(
        ants = spent.ants.unlockTask(AntTask.Explorer).addToTask(AntTask.Explorer, workers),
        exploration = spent.exploration.copy(explorationParties = departed :: spent.exploration.explorationParties,
          depleted = spent.exploration.depleted.updated(destination.id, depleted)),
        statistics = spent.statistics.copy(explorationsCount = spent.statistics.explorationsCount.addValue(1),
          explorationsWorkersSentU = spent.statistics.explorationsWorkersSentU.addValue(workers))))
    }
  def summary(data: AllData, workers: Long, destination: ExplorationDestination, extraScouts: Int = 0): String = {
    val cargo = load(data, workers, destination)
    val reward = if (RaidProgression.isRaid(destination)) {
      val high = load(data, workers, destination, Some(RaidProgression.rewardRange(workers)._2))
      s"${UINumbersUtils.prettyNumberStr(cargo.protein)}–${UINumbersUtils.prettyNumberStr(high.protein)}"
    } else UINumbersUtils.prettyNumberStr(amount(cargo, resource(destination)))
    s"${UINumbersUtils.prettyTimeFromTicks(ExplorationProgression.duration(destination, extraScouts))} · $reward ${resource(destination)}"
  }
  def outcomePreview(data: ExplorationData, workers: Long, destination: ExplorationDestination): String =
    if (RaidProgression.isRaid(destination)) RaidProgression.risk(workers) else s"${resource(destination).capitalize} guaranteed; no knowledge from collection."
  def exploreBonus(initial: AllData, party: ExplorationParty): AllData =
    if (!initial.exploration.explorationParties.contains(party)) initial
    else if (party.mission == "aphids") AphidRelocation.finish(initial, party)
    else if (party.mission != "haul") ExplorationMap.finishScout(initial, party)
    else {
      val destination = ExplorationDestination.forParty(party)
      val (afterBattle, ages) = RaidProgression.resolve(initial, party)
      val removed = afterBattle.copy(ants = afterBattle.ants.removeFromTask(AntTask.Explorer, party.amountWorkers),
        exploration = afterBattle.exploration.copy(explorationParties = afterBattle.exploration.explorationParties.patch(afterBattle.exploration.explorationParties.indexOf(party), Nil, 1)),
        statistics = afterBattle.statistics.copy(explorationsCompleted = afterBattle.statistics.explorationsCompleted.addValue(1)))
      val cargo = party.cargo
      val received = removed.giveResources(sugar = cargo.sugar, protein = cargo.protein, water = cargo.water, resin = cargo.resin)
      val key = resource(destination)
      val retained = key match {
        case "sugar" => received.basicResources.sugar.amount - removed.basicResources.sugar.amount
        case "protein" => received.basicResources.protein.amount - removed.basicResources.protein.amount
        case "water" => received.basicResources.water.amount - removed.basicResources.water.amount
        case "resin" => received.basicResources.resin.amount - removed.basicResources.resin.amount
      }
      // Storage may fill during travel. Leave overflow at the site, never destroy finite essentials.
      val unused = Math.max(0L, amount(cargo, key) - retained)
      val returned = received.copy(exploration = received.exploration.copy(depleted = received.exploration.depleted.updated(destination.id,
        Math.max(0L, received.exploration.depleted.getOrElse(destination.id, 0L) - unused))))
        .addMessage(s"${destination.name}: collected ${UINumbersUtils.prettyNumberStr(retained)} $key.")
      val discovered = if (RaidProgression.isRaid(destination)) DiscoveryProgression.recordReturn(returned, destination) else returned
      ResourceVisibility.reconcile(DefaultWorkerAssignment.normalize(PopulationAging.returnHome(discovered, ages)))
    }
}
