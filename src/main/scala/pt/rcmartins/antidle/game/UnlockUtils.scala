package pt.rcmartins.antidle.game

import com.raquo.airstream.ownership.Subscription
import com.raquo.laminar.api.L.{u => _, _}
import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import pt.rcmartins.antidle.model._

import scala.util.chaining.scalaUtilChainingOps

object UnlockUtils {

  def checkUnlocks(initialAllData: AllData)(implicit owner: SubscriptionManager): Unit = {
    unlockSubscription[BasicResource](
      sugarSignal,
      _ >= 5 * u,
      _ =>
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.actions.canLayEggs).setTo(true)),
        ),
      initialAllData.unlocks.actions.canLayEggs,
    )

    unlockSubscription[AntsData](
      antsSignal,
      _.eggsAndLarvae.nonEmpty,
      _ =>
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.resources.showEggs).setTo(true))
        ),
      initialAllData.unlocks.resources.showEggs,
    )

    unlockSubscription[Long](
      workersSignal,
      _ >= 1 * u,
      _ => {
        Var.update(
          unlocksData ->
            ((_: Unlocks).modifyAll(_.tabs.antTasksUnlocked, _.resources.showWorkers).setTo(true)),
          antsData -> ((_: AntsData).unlockTask(AntTask.SugarCollector)),
        )
        addMessage("We should give our ants some tasks to do. Let's start with collecting sugars.")
      },
      initialAllData.unlocks.tabs.antTasksUnlocked,
    )

    unlockSubscription[Long](
      antAndBroodCount,
      _ >= 5 * u,
      _ => {
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.actions.canBuildNestUpgrade).setTo(true)),
        )
        addMessage("We should upgrade our nest to be able to hold more ants.")
      },
      initialAllData.unlocks.actions.canBuildNestUpgrade,
    )

    unlockSubscription[Seq[BuildTask]](
      buildQueueSignal,
      _.nonEmpty,
      _ => {
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.tabs.buildQueueUnlocked).setTo(true)),
          antsData -> ((_: AntsData).unlockTask(AntTask.Tunneler)),
        )
        addMessage("Assign some of our ants as tunnelers.")
      },
      initialAllData.unlocks.tabs.buildQueueUnlocked,
    )

    unlockSubscription[BasicResource](
      tunnelingSpaceSignal,
      _ > 0,
      _ => {
        Var.update(
          unlocksData -> ((_: Unlocks).modifyAll(_.resources.showTunnelingSpace).setTo(true)),
        )
        addMessage("Tunneling Space is the main resource needed to build new chambers.")
      },
      initialAllData.unlocks.resources.showTunnelingSpace,
    )

    unlockSubscription[BasicResource](
      colonyPointsSignal,
      _ > 0,
      _ => {
        Var.update(
          unlocksData ->
            ((_: Unlocks)
              .modifyAll(_.resources.showColonyPoints, _.tabs.upgradesTabUnlocked)
              .setTo(true)),
        )
        addMessage("We can now upgrade our ants and our nest using colony points.")
      },
      initialAllData.unlocks.tabs.upgradesTabUnlocked,
    )

    unlockSubscription[UpgradesData](
      upgradesSignal,
      _(UnlockQueensChamber).unlocked,
      _ =>
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.actions.canBuildQueenChamber).setTo(true)),
        ),
      initialAllData.upgrades(UnlockQueensChamber).unlocked,
    )

    unlockSubscription[UpgradesData](
      upgradesSignal,
      _(UnlockFoodStorageChamber).unlocked,
      _ =>
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.actions.canBuildFoodStorageChamber).setTo(true)),
        ),
      initialAllData.upgrades(UnlockFoodStorageChamber).unlocked,
    )

    unlockSubscription[UpgradesData](
      upgradesSignal,
      _(UnlockExplorerTask).unlocked,
      _ =>
        Var.update(
          unlocksData -> ((_: Unlocks).modify(_.tabs.exploreTabUnlocked).setTo(true)),
          antsData -> ((_: AntsData).unlockTask(AntTask.Explorer)),
        ),
      initialAllData.upgrades(UnlockExplorerTask).unlocked,
    )
  }

  private def unlockSubscription[A](
      valueSignal: Signal[A],
      triggerThreshold: A => Boolean,
      unlock: Long => Unit,
      alreadyUnlockedCondition: Boolean,
  )(implicit owner: SubscriptionManager): Unit =
    if (!alreadyUnlockedCondition) {
      var subscription: Option[Subscription] = None
      subscription = Some(
        valueSignal
          .withCurrentValueOf(currentTickSignal)
          .foreach { case (value, currentTick) =>
            if (triggerThreshold(value)) {
              unlock(currentTick)
              subscription.foreach(_.kill())
            }
          }
          .tap(owner.track)
      )
    }

}
