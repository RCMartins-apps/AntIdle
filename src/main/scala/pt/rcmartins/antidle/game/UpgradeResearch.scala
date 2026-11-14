package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.balance.UpgradeBalance
import pt.rcmartins.antidle.model.AllData
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

/** Visibility is earned history, affordability is current state, and purchase is one-time. */
object UpgradeResearch {
  def isVisible(data: AllData, upgrade: UpgradeType): Boolean = {
    val status = data.upgrades(upgrade)
    (!upgrade.definition.requirements.beyondWoodland || data.ants.lineage.beyondWoodland) &&
      (status.unlocked || ((status.show || upgrade.shouldShow(data)) &&
      (upgrade.cost.knowledge == 0L || UpgradeBalance.explorationAvailable(data))))
  }

  def canPurchase(data: AllData, upgrade: UpgradeType): Boolean =
    !data.upgrades(upgrade).unlocked && isVisible(data, upgrade) && data.hasResources(upgrade.cost)

  def purchase(data: AllData, upgrade: UpgradeType): AllData =
    if (!canPurchase(data, upgrade)) data
    else UpgradeVisibility.reconcile(data.spendResources(upgrade.cost).copy(upgrades = data.upgrades.unlock(upgrade)))
}
