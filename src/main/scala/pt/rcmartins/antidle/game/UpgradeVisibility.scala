package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model.AllData
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object UpgradeVisibility {
  // Visibility is permanent once earned. Reconcile from state rather than replaying unlock events.
  def reconcile(data: AllData): AllData =
    UpgradeType.all.foldLeft(data) { (current, upgrade) =>
      val status = current.upgrades(upgrade)
      if (!status.show && (status.unlocked || upgrade.shouldShow(current)))
        current.modify(_.upgrades).using(_.show(upgrade))
      else current
    }
}
