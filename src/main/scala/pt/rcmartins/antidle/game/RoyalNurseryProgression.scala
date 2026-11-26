package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType.RoyalNurseryPlanning

object RoyalNurseryProgression {
  def buildCost: ActionCost = Construction.cost(ChamberType.RoyalNursery, 0)

  def canPlan(nest: NestAttributes, upgrades: UpgradesData): Boolean =
    upgrades(RoyalNurseryPlanning).unlocked && nest.chambers.royalNurseryChamber.level == 0 &&
      !nest.buildQueue.exists(_.chamberType == ChamberType.RoyalNursery)

}
