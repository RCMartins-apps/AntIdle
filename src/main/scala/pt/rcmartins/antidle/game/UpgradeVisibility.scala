package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model.AllData
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object UpgradeVisibility {
  // Keep earned visibility, but hide unpurchased Knowledge upgrades until exploration exists.
  def reconcile(data: AllData): AllData =
    BroodProgression.reconcile(TerritoryProgression.reconcile(PheromoneTrails.reconcile(RoyalBroodProgression.reconcile(OutpostProgression.reconcile(RepleteProgression.reconcile(Construction.reconcile(ChamberBonuses.reconcileProteinCapacity(DiscoveryProgression.unlockTasks(UpgradeType.all.foldLeft(data) { (current, upgrade) =>
      val status = current.upgrades(upgrade)
      val visible = UpgradeResearch.isVisible(current, upgrade)
      if (status.show != visible)
        current.modify(_.upgrades).using(_.modify(upgrade)(_.copy(show = visible)))
      else current
    })))))))))
}
