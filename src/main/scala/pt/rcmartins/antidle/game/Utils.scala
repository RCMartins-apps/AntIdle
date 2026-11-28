package pt.rcmartins.antidle.game

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{u => _, _}
import org.scalajs.dom
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model._

import scala.annotation.tailrec
import scala.scalajs.js.timers.setInterval
import scala.util.chaining.scalaUtilChainingOps

object Utils {

  var pause: Boolean = false
  // A monotonic sample and the leftover milliseconds below one simulation tick.
  private var liveClock: Option[(Long, Long)] = None

  private[game] def resetLiveClock(): Unit = liveClock = None

  val useCatchupTicksVar: Var[Boolean] = Var(false)

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
  val territoryVisibleSignal: Signal[Boolean] =
    explorationSignal.map(TerritoryProgression.visible).distinct

  val discoveriesVisibleSignal: Signal[Boolean] =
    explorationSignal.map(data => data.surveys.values.exists(_ > 0)).distinct

  val statisticsData: Var[StatisticsData] = Var(AllData.initial.statistics)

  val currentTickSignal: Signal[Long] = worldData.signal.map(_.currentTick).distinct
  val targetTickSignal: Signal[Long] = worldData.signal.map(_.targetTick).distinct

  val workersSignal: Signal[Long] = antsSignal.map(_.totalWorkers).distinct
  val eggsCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isEgg) * u).distinct

  val larvaeCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isLarvae) * u).distinct

  val pupaeCountSignal: Signal[Long] =
    antsSignal.map(_.eggsAndLarvae.count(_.isPupae) * u).distinct

  val scoutsSignal: Signal[Long] = antsSignal.map(_.scouts).distinct
  val availableScoutsSignal: Signal[Long] = antsSignal
    .combineWith(explorationSignal)
    .map { case (ants, exploration) => ScoutProgression.available(ants, exploration) }
    .distinct

  val scoutResourceSignal: Signal[BasicResource] = scoutsSignal.map(BasicResource(_, 0L))

  val antAndBroodCount: Signal[Long] =
    antsSignal.map(_.populationCount.toLong * u).distinct

  val unlockedAntTasks: Signal[Seq[AntTask]] =
    antsSignal
      .map(a => if (!a.standardWorkersUnlocked && a.workers == 0L) Seq.empty else a.tasks.map(_._1))
      .distinct

  val idleWorkersCountSignal: Signal[Long] = antsData.signal.map(_.idleWorkersCount).distinct
  val availableWorkersCountSignal: Signal[Long] =
    antsData.signal.map(_.availableWorkersCount).distinct

  val sugarSignal: Signal[BasicResource] = resourcesSignal.map(_.sugar).distinct
  val proteinSignal: Signal[BasicResource] = resourcesSignal.map(_.protein).distinct
  val proteinVisibleSignal: Signal[Boolean] = explorationSignal.map(_.proteinDiscovered).distinct
  val nurseryUnlockedSignal: Signal[Boolean] = antsSignal
    .combineWith(nestSignal, explorationSignal)
    .map { case (ants, nest, exploration) =>
      BroodProgression.nurseryAvailable(ants, nest, exploration)
    }
    .distinct

  val waterSignal: Signal[BasicResource] = resourcesSignal.map(_.water).distinct
  val resinSignal: Signal[BasicResource] = resourcesSignal.map(_.resin).distinct
  val tunnelingSpaceSignal: Signal[BasicResource] = resourcesSignal.map(_.tunnelingSpace).distinct
  val knowledgeSignal: Signal[BasicResource] = resourcesSignal.map(_.knowledge).distinct
//  val DNASignal: Signal[BasicResource] = resourcesSignal.map(_.DNA).distinct

  val maxEggs: Signal[Long] = nestAttributesData.signal.map(_.maxEggs).distinct
  val maxAnts: Signal[Long] = nestAttributesData.signal.map(_.maxAnts).distinct

  val buildQueueSignal: Signal[List[BuildTask]] = nestSignal.map(_.buildQueue).distinct
  val maxBuildQueueSignal: Signal[Int] = nestSignal.map(_.maxBuildQueue).distinct

  val messagesSeq: Var[Seq[String]] = Var(Seq.empty)
  val messagesSignal: Signal[Seq[String]] = messagesSeq.signal.distinct

  val eggResourceSignal: Signal[BasicResource] =
    antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
      BasicResource(
        ants.eggsAndLarvae.size.toLong * u,
        (BroodProgression.usablePlaces(nest) + EmergencyRecovery.broodCount(ants)).toLong * u
      )
    }

  val workersResourceSignal: Signal[BasicResource] =
    antsSignal.map(a => BasicResource(a.workers, 0L))

  val populationResourceSignal: Signal[BasicResource] =
    antAndBroodCount.combineWith(maxAnts).map { case (amount, max) => BasicResource(amount, max) }

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
    pause = true
    setInterval(200)(updateState())
    actionUpdater.writer.onNext(
      _.tap(allData => lastSavedTick.set(allData.world.currentTick))
    )
    SaveLoad.initializeSlots()
    SaveLoad.loadFromLocalStorage() match {
      case Right(loaded) => SaveLoad.restore(loaded, manual = false)
      case Left(error) =>
        pause = false
        if (error != SaveLoad.Missing) SaveLoad.showError(error)
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
        val wallTime = System.currentTimeMillis()
        val monotonicTime = dom.window.performance.now().toLong
        val elapsed = liveClock match {
          case Some((previous, remainder)) => Math.max(0L, monotonicTime - previous) + remainder
          case None                        => world.elapsedSinceSave(wallTime)
        }
        liveClock = Some(monotonicTime -> (elapsed % Constants.millsPerTick))
        worldData.set(world.creditElapsed(elapsed, wallTime))
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
            def loop(
                ticksLeft: Long,
                data: AllData,
                scheduledSave: Option[AllData]
            ): (AllData, Option[AllData]) =
              if (ticksLeft <= 0) (data, scheduledSave)
              else {
                val ticked = TickUpdater.updateAllData(data)
                val save = scheduledSave.orElse(
                  Option.when(nextSaveTick.exists(ticked.currentTick >= _))(ticked)
                )
                loop(ticksLeft - 1, ticked, save)
              }

            val ticksToCatchUp: Long =
              if (useCatchupTicks)
                Math.min(ticksToUpdate, Constants.MaxTicksCatchUpMult)
              else
                1

            val (updated, scheduledSave) = loop(ticksToCatchUp, allData, None)
            updated.updateVars()
            scheduledSave.foreach { snapshot =>
              lastSavedTick.set(snapshot.currentTick)
              SaveLoad.withWriteLock {
                SaveLoad.saveToLocalStorage(snapshot).left.foreach(SaveLoad.showError)
              }
            }
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
          GameTransition.afterAction(actionFunc(allData)).updateVars()
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

  val snapshotSignal: Signal[AllData] = allDataSignals.map {
    case (world, ants, basic, nest, unlocks, upgrades, exploration, statistics) =>
      AllData.simple(
        world,
        ants,
        basic,
        nest,
        unlocks,
        upgrades,
        exploration,
        statistics,
        Seq.empty
      )
  }

  def canPurchaseUpgradeSignal(upgrade: UpgradesData.UpgradeType): Signal[Boolean] =
    allDataSignals.map {
      case (world, ants, basic, nest, unlocks, upgrades, exploration, statistics) =>
        UpgradeResearch.canPurchase(
          AllData.simple(
            world,
            ants,
            basic,
            nest,
            unlocks,
            upgrades,
            exploration,
            statistics,
            Seq.empty
          ),
          upgrade
        )
    }.distinct

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
