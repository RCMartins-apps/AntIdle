package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

/** Current-colony achievements open paid research; they are not inherited Understandings. */
object MilestoneResearch {
  val ScoutDurationPercent: Long = 80L
  val NurseCarePercent: Long = 150L
  val LayingIntervalPercent: Long = 80L
  def standardWorkers(stats: StatisticsData): Int = Math.max(0, stats.workersMatured.thisRun - stats.smallWorkersMatured.thisRun)
  case class Goal(upgrade: UpgradesData.UpgradeType, workers: Boolean) {
    def target: Int = if (workers) upgrade.definition.requirements.matureWorkers else upgrade.definition.requirements.completedMissions
    def progress(data: AllData): Int = Math.min(target, if (workers) standardWorkers(data.statistics) else data.statistics.explorationsCompleted.thisRun)
    def label: String = if (workers) "standard workers raised" else "expeditions completed"
    def eligible(data: AllData): Boolean = upgrade.definition.requirements.copy(matureWorkers = 0, completedMissions = 0).met(data)
  }
  def goals: List[Goal] = List(Goal(ScoutWayfinding, false), Goal(NurseTraining, true), Goal(QueensRhythm, true))
  def next(data: AllData): Option[Goal] = goals.find(g => g.eligible(data) && !data.upgrades(g.upgrade).unlocked && g.progress(data) < g.target)
}
