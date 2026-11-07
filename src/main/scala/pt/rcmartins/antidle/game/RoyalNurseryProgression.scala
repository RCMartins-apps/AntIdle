package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.RoyalNurseryPlanning

object RoyalNurseryProgression {
  val buildCost: ActionCost = ActionCost(sugar = 3000 * u, protein = 50 * u, water = 100 * u,
    tunnelingSpace = getChamberTunnelingSpaceNeeded(ChamberType.RoyalNursery, 0), resin = 80 * u)

  def canPlan(nest: NestAttributes, upgrades: UpgradesData): Boolean =
    upgrades(RoyalNurseryPlanning).unlocked && nest.chambers.royalNurseryChamber.level == 0 &&
      !nest.buildQueue.exists(_.chamberType == ChamberType.RoyalNursery)
}
