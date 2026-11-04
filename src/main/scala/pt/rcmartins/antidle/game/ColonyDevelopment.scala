package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.{AntBrood, UpgradesData}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

object ColonyDevelopment {
  def tunnelingPerWorkerTick(upgrades: UpgradesData): Long =
    if (upgrades(ReinforcedMandibles).unlocked) defaultTaskTunnelingSpaceTick * 3L / 2L
    else defaultTaskTunnelingSpaceTick

  def growthTicks(brood: AntBrood, upgrades: UpgradesData): Long = {
    val base = brood match {
      case _: AntBrood.ScoutEgg => ScoutGrowthTicks
      case _ => AntBrood.defaultTicksToGrow
    }
    if (upgrades(BroodCare).unlocked) base * 4L / 5L else base
  }
}
