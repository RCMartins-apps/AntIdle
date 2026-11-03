package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object DefaultWorkerAssignment {
  def select(data: AllData, task: Option[AntTask]): AllData =
    if (!data.upgrades(UpgradeType.DefaultWorkers).unlocked) data
    else data.modify(_.ants).using(_.withDefaultTask(task))

  def normalize(data: AllData): AllData = {
    if (data.upgrades(UpgradeType.DefaultWorkers).unlocked)
      data.modify(_.ants).using(_.assignIdleToDefault)
    else if (data.ants.defaultTask.nonEmpty) data.modify(_.ants.defaultTask).setTo(None)
    else data
  }
}
