package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.QueensRoutine

/** A single queen-tended recovery brood; never a source of ordinary income. */
object EmergencyRecovery {
  val EggCost: Long = 5L * u
  val ForagingTicks: Long = 25L * TicksPerSecond
  val Kind: String = "emergency-small"

  def broodCount(ants: AntsData): Int = ants.eggsAndLarvae.count {
    case b: AntBrood.Developing => b.kind == Kind
    case _ => false
  }

  def hasRecoveryBrood(ants: AntsData): Boolean = ants.eggsAndLarvae.exists {
    case b: AntBrood.Developing => b.small
    case _: AntBrood.Egg => true // Legacy worker eggs need no feeding.
    case _ => false
  }

  def needed(ants: AntsData): Boolean =
    ants.standardWorkersUnlocked && ants.totalWorkers == 0L && !hasRecoveryBrood(ants)

  def automatic(data: AllData): Boolean = data.upgrades(QueensRoutine).unlocked &&
    data.ants.queenRoutine.enabled && data.ants.queenRoutine.targetWorkers > 0 && data.unlocks.actions.canLayEggs

  def canRaise(ants: AntsData, sugar: Long): Boolean =
    needed(ants) && ants.layingCooldown <= 0L && sugar >= EggCost

  // One exceptional brood place prevents full, unfed ordinary brood from blocking recovery.
  // The usual population/space limits continue to apply to every ordinary egg.
  private def lay(data: AllData): AllData = data.copy(
    ants = data.ants.copy(eggsAndLarvae = data.ants.eggsAndLarvae :+ AntBrood.Developing(data.currentTick, Kind),
      layingCooldown = 5L * TicksPerSecond, emergencyForagingTicks = 0L),
    statistics = data.statistics.copy(eggsCount = data.statistics.eggsCount.addValue(1L)))
    .addMessage("The queen is rearing one small worker to restart sugar collection.")

  def raise(data: AllData): AllData =
    if (!data.unlocks.actions.canLayEggs || !canRaise(data.ants, data.basicResources.sugar.amount)) data
    else lay(data.spendResources(ActionCost(sugar = EggCost)))

  def tick(data: AllData): AllData = {
    if (!needed(data.ants) || !automatic(data)) {
      if (data.ants.emergencyForagingTicks == 0L) data
      else data.copy(ants = data.ants.copy(emergencyForagingTicks = 0L))
    } else if (canRaise(data.ants, data.basicResources.sugar.amount - data.ants.queenRoutine.sugarReserve)) {
      lay(data.spendResources(ActionCost(sugar = EggCost)))
    } else {
      val progress = Math.min(ForagingTicks, Math.max(0L, data.ants.emergencyForagingTicks) + 1L)
      val prepared = data.copy(ants = data.ants.copy(emergencyForagingTicks = progress))
      if (progress < ForagingTicks || data.ants.layingCooldown > 0L) prepared
      else {
        // Her protected provisions are gathered and consumed here, never banked in storage.
        lay(prepared.copy(statistics = prepared.statistics.copy(sugarU = prepared.statistics.sugarU.addValue(EggCost))))
      }
    }
  }

  def status(ants: AntsData): String =
    s"Queen gathering emergency supplies · ${UINumbersUtils.prettyTimeFromTicks(Math.max(0L, ForagingTicks - ants.emergencyForagingTicks))} · 5 sugar for one small-worker egg"
}
