package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

/** Pure progression shared by the browser and headless balance runs. */
object UnlockUtils {
  def reconcile(initial: AllData): AllData = {
    val old = initial.unlocks
    val hasWorkers = initial.ants.workers >= u
    val building = initial.nestAttributes.buildQueue.nonEmpty
    val canExpand = initial.ants.populationCount >= 5
    val dug = initial.basicResources.tunnelingSpace.amount > 0L
    val exploring = initial.upgrades(UnlockExplorerTask).unlocked
    val next = old.copy(
      actions = old.actions.copy(
        canLayEggs = old.actions.canLayEggs || initial.basicResources.sugar.amount >= 5L * u,
        canBuildNestUpgrade = old.actions.canBuildNestUpgrade || canExpand,
        canBuildQueenChamber = old.actions.canBuildQueenChamber || initial.upgrades(UnlockQueensChamber).unlocked,
        canBuildFoodStorageChamber = old.actions.canBuildFoodStorageChamber || initial.upgrades(UnlockFoodStorageChamber).unlocked),
      tabs = old.tabs.copy(
        antTasksUnlocked = old.tabs.antTasksUnlocked || hasWorkers,
        buildQueueUnlocked = old.tabs.buildQueueUnlocked || building,
        upgradesTabUnlocked = old.tabs.upgradesTabUnlocked || initial.nestAttributes.chambers.nestChamber.level > 0,
        exploreTabUnlocked = old.tabs.exploreTabUnlocked || exploring),
      resources = old.resources.copy(
        showEggs = old.resources.showEggs || initial.ants.eggsAndLarvae.nonEmpty,
        showWorkers = old.resources.showWorkers || hasWorkers,
        showTunnelingSpace = old.resources.showTunnelingSpace || dug,
        showKnowledge = old.resources.showKnowledge || initial.basicResources.knowledge.amount > 0L))
    var data = initial.copy(unlocks = next)
    if (hasWorkers) data = data.copy(ants = data.ants.unlockTask(AntTask.SugarCollector))
    if (building) data = data.copy(ants = data.ants.unlockTask(AntTask.Tunneler))
    if (exploring) data = data.copy(ants = data.ants.unlockTask(AntTask.Explorer))
    if (hasWorkers && !old.tabs.antTasksUnlocked)
      data = data.addMessage("We should give our ants some tasks to do. Let's start with collecting sugars.")
    if (canExpand && !old.actions.canBuildNestUpgrade)
      data = data.addMessage("We should upgrade our nest to be able to hold more ants.")
    if (building && !old.tabs.buildQueueUnlocked)
      data = data.addMessage("Assign some of our ants as tunnelers.")
    if (dug && !old.resources.showTunnelingSpace)
      data = data.addMessage("Tunneling Space is the main resource needed to build new chambers.")
    data
  }
}
