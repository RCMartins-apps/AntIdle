package pt.rcmartins.antidle.game

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{u => _, _}
import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model._

import scala.annotation.tailrec
import scala.scalajs.js.timers.setInterval
import scala.util.chaining.scalaUtilChainingOps

object Utils {

  var pause: Boolean = false
  val useCatchupTicksVar: Var[Boolean] = Var(false)
  val unlocksOwner: SubscriptionManager = new SubscriptionManager

  private val defaultMode: Int = 30
  val allSaveModes: Seq[Int] = Seq(0, defaultMode, 60, 180)
  val autoSaveMode: Var[Int] = Var(defaultMode)
  val lastSavedTick: Var[Long] = Var(0L)
  private val nextSaveTickSignal: Signal[Option[Long]] =
    lastSavedTick.signal.combineWith(autoSaveMode).map {
      case (_, 0)            => None
      case (lastSaved, mode) => Some(lastSaved + mode * Constants.TicksPerSecond)
    }

  val worldData: Var[WorldData] = Var(AllData.initial.world)
  val antsData: Var[AntsData] = Var(AllData.initial.ants)
  val antsSignal: Signal[AntsData] = antsData.signal
  val basicResourcesData: Var[BasicResources] = Var(AllData.initial.basicResources)
  val resourcesSignal: Signal[BasicResources] = basicResourcesData.signal
  val nestAttributesData: Var[NestAttributes] = Var(AllData.initial.nestAttributes)
  val nestSignal: Signal[NestAttributes] = nestAttributesData.signal
  val chambersSignal: Signal[AllChamberData] = nestSignal.map(_.chambers).distinct
  val unlocksData: Var[Unlocks] = Var(AllData.initial.unlocks)
  val unlocksSignal: Signal[Unlocks] = unlocksData.signal.distinct
  val upgradesData: Var[UpgradesData] = Var(AllData.initial.upgrades)
  val upgradesSignal: Signal[UpgradesData] = upgradesData.signal.distinct
  val explorationData: Var[ExplorationData] = Var(AllData.initial.exploration)
  val explorationSignal: Signal[ExplorationData] = explorationData.signal.distinct
  val statisticsData: Var[StatisticsData] = Var(AllData.initial.statistics)

  val currentTickSignal: Signal[Long] = worldData.signal.map(_.currentTick).distinct
  val targetTickSignal: Signal[Long] = worldData.signal.map(_.targetTick).distinct

  val workersSignal: Signal[Long] = antsSignal.map(_.workers).distinct
  val eggsCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isEgg) * u).distinct

  val larvaeCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isLarvae) * u).distinct

  val pupaeCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isPupae) * u).distinct

  val scoutsSignal: Signal[Long] = antsSignal.map(_.scouts).distinct
  val availableScoutsSignal: Signal[Long] = antsSignal.combineWith(explorationSignal)
    .map { case (ants, exploration) => ScoutProgression.available(ants, exploration) }.distinct
  val scoutResourceSignal: Signal[BasicResource] = scoutsSignal.combineWith(nestSignal)
    .map { case (amount, nest) => BasicResource(amount, nest.maxScouts) }

  val antAndBroodCount: Signal[Long] =
    workersSignal
      .combineWith(eggsCountSignal, larvaeCountSignal, pupaeCountSignal, scoutsSignal)
      .map { case (workers, eggs, larvae, pupae, scouts) =>
        workers + eggs + larvae + pupae + scouts
      }
      .distinct

  val unlockedAntTasks: Signal[Seq[AntTask]] =
    antsSignal.map(_.tasks.map(_._1)).distinct

  val idleWorkersCountSignal: Signal[Long] = antsData.signal.map(_.idleWorkersCount).distinct

  val sugarSignal: Signal[BasicResource] = resourcesSignal.map(_.sugar).distinct
  val proteinSignal: Signal[BasicResource] = resourcesSignal.map(_.protein).distinct
  val nurseryUnlockedSignal: Signal[Boolean] = explorationSignal.combineWith(proteinSignal)
    .map { case (exploration, protein) => exploration.proteinDiscovered || protein.amount > 0 }.distinct
//  val waterSignal: Signal[BasicResource] = resourcesSignal.map(_.water).distinct
  val tunnelingSpaceSignal: Signal[BasicResource] = resourcesSignal.map(_.tunnelingSpace).distinct
  val colonyPointsSignal: Signal[BasicResource] = resourcesSignal.map(_.colonyPoints).distinct
//  val DNASignal: Signal[BasicResource] = resourcesSignal.map(_.DNA).distinct

  val maxEggs: Signal[Long] = nestAttributesData.signal.map(_.maxEggs).distinct
  val maxWorkers: Signal[Long] = nestAttributesData.signal.map(_.maxWorkers).distinct

  val buildQueueSignal: Signal[List[BuildTask]] = nestSignal.map(_.buildQueue).distinct
  val maxBuildQueueSignal: Signal[Int] = nestSignal.map(_.maxBuildQueue).distinct

  val messagesSeq: Var[Seq[String]] = Var(Seq.empty)
  val messagesSignal: Signal[Seq[String]] = messagesSeq.signal.distinct

  val eggResourceSignal: Signal[BasicResource] =
    eggsCountSignal.combineWith(maxEggs).map { case (amount, max) => BasicResource(amount, max) }

  val workersResourceSignal: Signal[BasicResource] =
    workersSignal.combineWith(maxWorkers).map { case (amount, max) => BasicResource(amount, max) }

  val antTasksUnlockedSignal: Signal[Boolean] =
    unlocksSignal.map(_.tabs.antTasksUnlocked).distinct

  val buildQueueUnlockedSignal: Signal[Boolean] =
    unlocksSignal.map(_.tabs.buildQueueUnlocked).distinct

  val upgradesTabUnlockedSignal: Signal[Boolean] =
    unlocksSignal.map(_.tabs.upgradesTabUnlocked).distinct

  val exploreTabUnlockedSignal: Signal[Boolean] =
    unlocksSignal.map(_.tabs.exploreTabUnlocked).distinct

  val updateBus: EventBus[Unit] = new EventBus()
  val ticksBus: EventBus[Unit] = new EventBus()
  val actionUpdater: EventBus[AllData => AllData] = new EventBus()

  def initialize(): Unit = {
    val owner = new OneTimeOwner(() => ())
    initializeTicksUpdater(owner)
    initializeActionUpdater(owner)
    addMessage("We should collect some sugar to feed our queen.")
    pause = true
    setInterval(200)(updateState())
    actionUpdater.writer.onNext(
      _.tap(UnlockUtils.checkUnlocks(_)(unlocksOwner))
        .tap(allData => lastSavedTick.set(allData.world.currentTick))
    )
    SaveLoad.loadFromLocalStorage() match {
      case Some(allData) => SaveLoad.reloadDataFromLoadedSave(allData, messageAlert = None)
      case None          => pause = false
    }
  }

  def addMessage(message: String): Unit =
    actionUpdater.writer.onNext(_.addMessage(message))

  private def updateState(): Unit =
    if (!pause)
      updateBus.writer.onNext(())

  private val allDataSignals =
    worldData.signal.combineWith(
      antsData,
      resourcesSignal,
      nestAttributesData,
      unlocksData,
      upgradesData,
      explorationData,
      statisticsData,
    )

  private def initializeTicksUpdater(implicit owner: Owner): Unit = {
    updateBus.events
      .sample(worldData)
      .foreach { world =>
        val passedTicks = world.getPassedTicks(System.currentTimeMillis())
        worldData.update(
          _.addToTargetTick(passedTicks)
            .modify(_.lastUpdateTime)
            .using(_ + Constants.millsPerTick * passedTicks)
        )
        ticksBus.writer.onNext(())
      }

    ticksBus.events
      .sample(
        allDataSignals,
        messagesSeq,
        nextSaveTickSignal,
        useCatchupTicksVar,
      )
      .foreach {
        case (
              (world, ants, basic, next, unlocks, upgrades, exploration, statistics),
              messages,
              nextSaveTick,
              useCatchupTicks,
            ) =>
          val ticksToUpdate = world.targetTick - world.currentTick
          if (ticksToUpdate > 0) {
            val allData =
              AllData.simple(
                world,
                ants,
                basic,
                next,
                unlocks,
                upgrades,
                exploration,
                statistics,
                messages,
              )

            @tailrec
            def loop(ticksLeft: Long, allData: AllData): AllData =
              if (ticksLeft <= 0)
                allData
              else
                loop(ticksLeft - 1, TickUpdater.updateAllData(allData, nextSaveTick))

            val ticksToCatchUp: Long =
              if (useCatchupTicks)
                Math.min(ticksToUpdate, Constants.MaxTicksCatchUpMult)
              else
                1

            loop(ticksToCatchUp, allData).updateVars()
          }
      }
  }

  private def initializeActionUpdater(implicit owner: Owner): Unit =
    actionUpdater.events
      .withCurrentValueOf(
        allDataSignals,
        messagesSeq,
      )
      .foreach {
        case (
              actionFunc,
              (world, ants, basic, next, unlocks, upgrades, exploration, statistics),
              messages,
            ) =>
          val allData: AllData =
            AllData.simple(
              world,
              ants,
              basic,
              next,
              unlocks,
              upgrades,
              exploration,
              statistics,
              messages,
            )
          actionFunc(allData).updateVars()
      }

  def giveResources(sugar: Long): Unit =
    actionUpdater.writer.onNext { allData =>
      allData.giveResources(sugar = sugar)
    }

  def ifUnlockedOpt[A](unlocks: Unlocks => Boolean)(value: => A): Signal[Option[A]] =
    unlocksSignal.map(unlocks).distinct.map {
      case false => None
      case true  => Some(value)
    }

  def ifUnlockedOpt[A](signal: Signal[Boolean])(value: => A): Signal[Option[A]] =
    signal.distinct.map {
      case false => None
      case true  => Some(value)
    }

  def ifUpgradeReadyToBuyOpt[A](
      upgradeSignal: Signal[UpgradeData],
  )(value: => A): Signal[Option[A]] =
    upgradeSignal
      .map { upgrade => upgrade.show && !upgrade.unlocked }
      .distinct
      .map {
        case false => None
        case true  => Some(value)
      }

  def hasResourcesSignal(costSignal: Signal[ActionCost]): Signal[Boolean] =
    resourcesSignal.combineWith(antsSignal, costSignal).map { case (resources, ants, cost) =>
      resources.hasResources(cost) && ants.hasIdleWorkersForCost(cost)
    }

  def ifGreater0[A](compareValue: Long)(func: Long => A): Option[A] =
    if (compareValue == 0) None else Some(func(compareValue))

  def useSignalValue[A](signal: Signal[A], action: A => Unit)(implicit owner: Owner): Unit = {
    val eventBus: EventBus[Unit] = new EventBus

    var subscription: Subscription = null
    subscription = eventBus.events
      .sample(signal)
      .foreach { signalValue =>
        action(signalValue)
        subscription.kill()
      }

    eventBus.writer.onNext(())
  }

  class SubscriptionManager extends Owner {
    private var killableSubscriptions: List[Subscription] = List()

    def track(subscription: Subscription): Unit = {
      killableSubscriptions = subscription :: killableSubscriptions
    }

    def killAll(): Unit = {
      killableSubscriptions.foreach(subscription => if (!subscription.isKilled) subscription.kill())
      killableSubscriptions = List()
    }

  }

}
