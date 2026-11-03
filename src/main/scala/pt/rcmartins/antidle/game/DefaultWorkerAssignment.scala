package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object DefaultWorkerAssignment {
  def select(data: AllData, task: Option[AntTask]): AllData =
    if (!data.upgrades(UpgradeType.DefaultWorkers).unlocked) data
    else data.modify(_.ants).using(_.withDefaultTask(task))

  def normalize(data: AllData): AllData = {
    val visible = if (data.unlocks.tabs.exploreTabUnlocked && !data.upgrades(UpgradeType.DefaultWorkers).show)
      data.modify(_.upgrades).using(_.show(UpgradeType.DefaultWorkers))
    else data
    if (visible.upgrades(UpgradeType.DefaultWorkers).unlocked)
      visible.modify(_.ants).using(_.assignIdleToDefault)
    else if (visible.ants.defaultTask.nonEmpty) visible.modify(_.ants.defaultTask).setTo(None)
    else visible
  }
}
