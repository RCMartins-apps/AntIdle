package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.AllData

object GameTransition {
  def afterAction(data: AllData): AllData =
    ResourceVisibility.reconcile(UnlockUtils.reconcile(DefaultWorkerAssignment.normalize(PopulationAging.reconcile(data))))
}
