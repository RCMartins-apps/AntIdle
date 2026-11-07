package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._

object ResourceVisibility {
  // Resource unlocks are permanent, even after spending the last unit or losing ants.
  def reconcile(data: AllData): AllData = {
    val known = data.unlocks.resources
    data
      .modify(_.unlocks.resources.showTunnelingSpace).setTo(known.showTunnelingSpace || data.basicResources.tunnelingSpace.amount > 0L)
      .modify(_.unlocks.resources.showWater).setTo(known.showWater || data.basicResources.water.amount > 0L || data.nestAttributes.dryClearingOutpost)
      .modify(_.unlocks.resources.showKnowledge).setTo(known.showKnowledge || data.basicResources.knowledge.amount > 0L)
      .modify(_.unlocks.resources.showResin).setTo(known.showResin || data.basicResources.resin.amount > 0L ||
        DiscoveryProgression.ResinSeep.found(data.exploration) || data.upgrades(UpgradesData.UpgradeType.RoyalNurseryPlanning).unlocked ||
        data.nestAttributes.chambers.royalNurseryChamber.level > 0)
      .modify(_.unlocks.resources.showEggs).setTo(known.showEggs || data.unlocks.actions.canLayEggs || data.ants.eggsAndLarvae.nonEmpty)
      .modify(_.statistics.maxAliveWorkers).using(_.max(data.ants.workersCount))
      .modify(_.unlocks.resources.showWorkers).setTo(known.showWorkers || data.ants.workers > 0L)
      .modify(_.unlocks.resources.showRepletes).setTo(known.showRepletes || data.ants.repletes > 0L || data.upgrades(UpgradesData.UpgradeType.RepleteStorage).unlocked)
      .modify(_.unlocks.resources.showDaughterQueens).setTo(known.showDaughterQueens || data.ants.royalBrood.nonEmpty || data.ants.daughterQueens > 0L)
      .modify(_.unlocks.resources.showScouts).setTo(known.showScouts || data.ants.scouts > 0L || data.nestAttributes.chambers.nurseryChamber.level > 0)
      .modify(_.exploration.proteinDiscovered).setTo(data.exploration.proteinDiscovered || data.basicResources.protein.amount > 0L ||
        data.nestAttributes.chambers.nurseryChamber.level > 0 || data.upgrades(UpgradesData.UpgradeType.AphidTending).unlocked || data.nestAttributes.dryClearingOutpost)
  }
}
