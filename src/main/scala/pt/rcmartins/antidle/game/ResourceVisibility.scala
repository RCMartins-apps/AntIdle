package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._

object ResourceVisibility {
  // Visibility latches within a colony; founding starts a new discovery history.
  // Old pristine daughter-colony saves may still contain inherited visibility.
  // Only repair an untouched start: never infer unlock history from an empty stock.
  def resetUnstartedFounding(data: AllData): AllData = {
    val pristine = data.statistics == StatisticsData.newColony(data.statistics) &&
      data.ants == AntsData.initial.copy(lineage = data.ants.lineage) &&
      data.basicResources == BasicResources.initial && data.nestAttributes == NestAttributes.initial &&
      data.upgrades == UpgradesData.initial &&
      data.unlocks.copy(resources = Unlocks.initial.resources) == Unlocks.initial &&
      data.exploration.copy(proteinDiscovered = false) == ExplorationData.initial
    if (data.ants.lineage.foundings > 0 && pristine)
      data.copy(unlocks = data.unlocks.copy(resources = Unlocks.initial.resources), exploration = ExplorationData.initial)
    else data
  }

  // Spending the last unit or losing ants does not hide current-colony unlocks.
  def reconcile(data: AllData): AllData = {
    val known = data.unlocks.resources
    ColonyMilestones.recordPeaks(data)
      .modify(_.unlocks.resources.showTunnelingSpace).setTo(known.showTunnelingSpace || data.basicResources.tunnelingSpace.amount > 0L)
      .modify(_.unlocks.resources.showWater).setTo(known.showWater || data.basicResources.water.amount > 0L || data.nestAttributes.dryClearingOutpost || DiscoveryProgression.DewPlants.found(data.exploration))
      .modify(_.unlocks.resources.showKnowledge).setTo(known.showKnowledge || data.basicResources.knowledge.amount > 0L)
      .modify(_.unlocks.resources.showResin).setTo(known.showResin || data.basicResources.resin.amount > 0L ||
        DiscoveryProgression.ResinSeep.found(data.exploration) || data.upgrades(UpgradesData.UpgradeType.RoyalNurseryPlanning).unlocked ||
        data.nestAttributes.chambers.royalNurseryChamber.level > 0)
      .modify(_.unlocks.resources.showEggs).setTo(known.showEggs || data.unlocks.actions.canLayEggs || data.ants.eggsAndLarvae.nonEmpty)
      .modify(_.statistics.maxAliveWorkers).using(_.max(data.ants.totalWorkersCount))
      .modify(_.unlocks.resources.showWorkers).setTo(known.showWorkers || data.ants.totalWorkers > 0L)
      .modify(_.unlocks.resources.showRepletes).setTo(known.showRepletes || data.ants.repletes > 0L || data.upgrades(UpgradesData.UpgradeType.RepleteStorage).unlocked)
      .modify(_.unlocks.resources.showDaughterQueens).setTo(known.showDaughterQueens || data.ants.royalBrood.nonEmpty || data.ants.daughterQueens > 0L)
      .modify(_.unlocks.resources.showSmallWorkers).setTo(known.showSmallWorkers || data.ants.smallWorkers > 0L)
      .modify(_.unlocks.resources.showScouts).setTo(known.showScouts || data.ants.scouts > 0L || data.nestAttributes.chambers.nurseryChamber.level > 0)
      .modify(_.exploration.proteinDiscovered).setTo(data.exploration.proteinDiscovered || data.basicResources.protein.amount > 0L ||
        data.nestAttributes.chambers.nurseryChamber.level > 0 || data.upgrades(UpgradesData.UpgradeType.AphidTending).unlocked || data.nestAttributes.dryClearingOutpost)
  }
}
