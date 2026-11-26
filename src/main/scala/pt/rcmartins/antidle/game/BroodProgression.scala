package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object BroodProgression {
  def capacity(nest: NestAttributes): Int =
    nest.maxEggsCount + ChamberBonuses.nurseryBroodPlaces(nest)

  def occupiedPlaces(ants: AntsData): Int =
    ants.eggsAndLarvae.size - EmergencyRecovery.broodCount(ants)

  def usablePlaces(nest: NestAttributes): Int =
    capacity(nest) - Midden.blockedBroodPlaces(nest, capacity(nest))

  def openPlaces(ants: AntsData, nest: NestAttributes): Int =
    Math.max(0, usablePlaces(nest) - occupiedPlaces(ants))

  def placeBlocker(ants: AntsData, nest: NestAttributes): Option[String] = {
    val occupied = occupiedPlaces(ants)
    if (occupied >= capacity(nest)) Some("Brood places full")
    else Option.when(occupied >= usablePlaces(nest))("Needs clean brood places")
  }

  // Remembering protein across foundings must not bypass current-colony progression.
  def nurseryAvailable(
      ants: AntsData,
      nest: NestAttributes,
      exploration: ExplorationData
  ): Boolean =
    nest.chambers.nurseryChamber.level > 0 || (ants.standardWorkersUnlocked && exploration.proteinDiscovered)

  def priorityAvailable(nest: NestAttributes): Boolean = nest.chambers.nurseryChamber.level > 0

  def setPriority(data: AllData, priority: BroodPriority): AllData =
    if (priorityAvailable(data.nestAttributes))
      data.copy(ants = data.ants.copy(broodPriority = priority))
    else data

  def eggCost(ants: AntsData): Long =
    if (ants.standardWorkersUnlocked) LayEggSugarCost else LayEggSugarCost / 2L

  def interval(
      ants: AntsData,
      nest: NestAttributes,
      upgrades: UpgradesData = UpgradesData.initial
  ): Long = {
    val base = (if (ants.standardWorkersUnlocked) 20L else 5L) * TicksPerSecond
    val improvements = Math.max(0, nest.chambers.queenChamber.level - 1)
    val percent =
      if (upgrades(UpgradeType.QueensRhythm).unlocked) MilestoneResearch.LayingIntervalPercent
      else 100L
    val denominator = 100L + improvements.toLong * balance.ChamberPricing.layingSpeedPerLevel
    (base * percent + denominator - 1L) / denominator
  }

  def incoming(ants: AntsData): Int = ants.eggsAndLarvae.count {
    case b: AntBrood.Developing => b.kind != "scout"
    case _: AntBrood.Egg        => true
    case _                      => false
  }

  def availableColonySlots(ants: AntsData, nest: NestAttributes): Long =
    Math.max(0L, nest.maxAnts - (ants.populationCount.toLong + ants.queuedEggs.toLong) * u)

  def orderAmount(ants: AntsData, nest: NestAttributes, requested: Int): Int =
    Math
      .max(0L, Math.min(Math.max(0, requested).toLong, availableColonySlots(ants, nest) / u))
      .toInt

  def order(data: AllData, requested: Int): AllData = {
    val amount = orderAmount(data.ants, data.nestAttributes, requested)
    data.copy(ants = data.ants.copy(eggOrders = data.ants.eggOrders + amount))
  }

  def cancel(data: AllData): AllData =
    data.copy(ants = data.ants.copy(eggOrders = 0, scoutEggOrders = Seq.empty))

  def canLay(ants: AntsData, nest: NestAttributes, sugar: Long, reserve: Long = 0L): Boolean =
    ants.layingCooldown <= 0L && openPlaces(ants, nest) > 0 &&
      ants.populationCount < nest.maxAntsCount && sugar - reserve >= eggCost(ants)

  def beginLaying(data: AllData, kind: String, ticks: Long): AllData =
    data.copy(ants =
      data.ants.copy(
        layingCooldown = ticks,
        eggsAndLarvae =
          data.ants.eggsAndLarvae :+ AntBrood.Developing(data.currentTick, kind, laying = true)
      )
    )

  def lay(data: AllData, reserve: Long = 0L): AllData = {
    if (!canLay(data.ants, data.nestAttributes, data.basicResources.sugar.amount, reserve)) data
    else
      beginLaying(
        data.spendResources(ActionCost(sugar = eggCost(data.ants))),
        if (data.ants.standardWorkersUnlocked) "worker" else "small",
        interval(data.ants, data.nestAttributes, data.upgrades)
      )
  }

  def layOrdered(data: AllData): AllData = {
    if (data.ants.scoutEggOrders.headOption.contains(0)) {
      val laid = ScoutProgression.lay(data)
      if (laid.ants.eggsAndLarvae.size == data.ants.eggsAndLarvae.size) laid
      else laid.copy(ants = laid.ants.copy(scoutEggOrders = laid.ants.scoutEggOrders.tail))
    } else if (data.ants.eggOrders <= 0) data
    else {
      val laid = lay(data)
      if (laid.ants.eggsAndLarvae.size == data.ants.eggsAndLarvae.size) laid
      else
        laid.copy(ants =
          laid.ants.copy(
            eggOrders = laid.ants.eggOrders - 1,
            scoutEggOrders = laid.ants.scoutEggOrders.map(ahead => Math.max(0, ahead - 1))
          )
        )
    }
  }

  def scavenged(ants: AntsData, room: Long, upgrades: UpgradesData = UpgradesData.initial): Long = {
    val normal = Math.min(2L, Math.max(0L, ants.taskWorkers(AntTask.Scavenger) / u))
    val small = Math.min(2L - normal, Math.max(0L, ants.smallScavengers / u))
    Math.min(
      Math.max(0L, room),
      balance.UpgradeBalance.production((normal * 2L + small) * 50L, "protein", upgrades)
    )
  }

  def assignSmall(initial: AllData, task: AntTask, delta: Long): AllData = {
    val data = AgingJobReplacement.cancel(initial, task)
    val a = data.ants
    val current = task match {
      case AntTask.Tunneler   => a.smallTunnelers
      case AntTask.Scavenger  => a.smallScavengers
      case AntTask.BroodNurse => a.smallNurses
      case _                  => 0L
    }
    val limit = task match {
      case AntTask.Tunneler => Long.MaxValue
      case AntTask.Scavenger if a.standardWorkersUnlocked =>
        Math.max(0L, 2L * u - a.taskWorkers(task))
      case AntTask.BroodNurse =>
        Math.max(
          0L,
          ChamberBonuses.nurseryNurseCapacity(data.nestAttributes) - a.taskWorkers(task)
        )
      case _ => 0L
    }
    val moved =
      if (delta > 0L)
        Math.min(delta / u * u, Math.min(a.smallCollectors, Math.max(0L, limit - current)))
      else -Math.min(current, -delta / u * u)
    val ants = task match {
      case AntTask.Tunneler   => a.copy(smallTunnelers = current + moved)
      case AntTask.Scavenger  => a.copy(smallScavengers = current + moved)
      case AntTask.BroodNurse => a.copy(smallNurses = current + moved)
      case _                  => a
    }
    reconcile(data.copy(ants = ants))
  }

  def reconcile(initial: AllData): AllData = {
    val standard =
      initial.ants.standardWorkersUnlocked || initial.upgrades(UpgradeType.StandardWorkers).unlocked
    val a = initial.ants
    val tunnelers = Math.min(Math.max(0L, a.smallTunnelers), a.smallWorkers)
    val scavengers =
      if (!standard) 0L
      else
        Math.min(
          Math.max(0L, a.smallScavengers),
          Math.min(
            a.smallWorkers - tunnelers,
            Math.max(0L, 2L * u - a.taskWorkers(AntTask.Scavenger))
          )
        )
    val nurses = Math.min(
      Math.max(0L, a.smallNurses),
      Math.min(
        a.smallWorkers - tunnelers - scavengers,
        Math.max(
          0L,
          ChamberBonuses.nurseryNurseCapacity(initial.nestAttributes) - a.taskWorkers(
            AntTask.BroodNurse
          )
        )
      )
    )
    val ants = a.copy(
      standardWorkersUnlocked = standard,
      smallTunnelers = tunnelers,
      smallScavengers = scavengers,
      smallNurses = nurses,
      layingCooldown = Math.max(0L, a.layingCooldown),
      eggOrders = Math.max(0, a.eggOrders),
      scoutEggOrders =
        a.scoutEggOrders.map(ahead => Math.max(0, Math.min(ahead, Math.max(0, a.eggOrders)))).sorted
    )
    val unlocked = if (standard) ants.unlockTask(AntTask.Scavenger) else ants
    val nursed =
      if (initial.nestAttributes.chambers.nurseryChamber.level > 0)
        unlocked.unlockTask(AntTask.BroodNurse)
      else unlocked
    val withCarriers =
      if (Midden.visible(initial.nestAttributes)) nursed.unlockTask(AntTask.RefuseCarrier)
      else nursed
    val working =
      if (a.totalWorkers > 0L) withCarriers.unlockTask(AntTask.SugarCollector) else withCarriers
    val nest = initial.nestAttributes.copy(nurseryRefuse = Midden.stored(initial.nestAttributes))
    DefaultWorkerAssignment.normalize(
      initial.copy(
        ants = working,
        nestAttributes = nest,
        exploration = initial.exploration
          .copy(proteinDiscovered = initial.exploration.proteinDiscovered || standard),
        unlocks = initial.unlocks.copy(tabs =
          initial.unlocks.tabs.copy(
            antTasksUnlocked = initial.unlocks.tabs.antTasksUnlocked || a.totalWorkers > 0L,
            upgradesTabUnlocked =
              initial.unlocks.tabs.upgradesTabUnlocked || a.smallWorkers >= 5L * u
          )
        )
      )
    )
  }

  private def feeding(brood: AntBrood.Developing, progress: Long): ActionCost = {
    if (brood.small) ActionCost.empty
    else {
      val larvalCare =
        Math.max(0L, Math.min(brood.larvaEnd - brood.eggEnd, progress - brood.eggEnd))
      ActionCost(
        sugar = 20L * u * larvalCare / (brood.larvaEnd - brood.eggEnd),
        protein = 2L * u * larvalCare / (brood.larvaEnd - brood.eggEnd)
      )
    }
  }

  def carePerNurse(upgrades: UpgradesData): Long =
    2L * u * (if (upgrades(UpgradeType.NurseTraining).unlocked) MilestoneResearch.NurseCarePercent
              else 100L) / 100L

  def nurseContribution(workers: Long, upgrades: UpgradesData, small: Boolean): Long =
    workers / u * carePerNurse(upgrades) / (if (small) 2L else 1L)

  def nursePower(ants: AntsData, upgrades: UpgradesData = UpgradesData.initial): Long =
    nurseContribution(ants.taskWorkers(AntTask.BroodNurse), upgrades, small = false) +
      nurseContribution(ants.smallNurses, upgrades, small = true)

  private def tend(
      a: AntsData,
      nest: NestAttributes,
      upgrades: UpgradesData,
      initialResources: BasicResources
  ): (Vector[AntBrood], BasicResources) = {
    val original = a.eggsAndLarvae.toVector
    val count = original.size
    if (count == 0) return original -> initialResources

    val boost = if (upgrades(UpgradeType.BroodCare).unlocked) 125L else 100L
    val nurses = nursePower(a, upgrades)
    var budget = (3L * u + nurses) * boost / 100L
    val perBrood = (if (nurses > 0L) 2L * u else u) * boost / 100L
    var resources = initialResources
    var broods = original
    val order = (0 until count).map(offset => (Math.max(0, a.broodCursor) % count + offset) % count)

    def tendGroup(indices: Seq[Int], allowance: Long): Unit = {
      var remaining = Math.min(budget, allowance)
      indices.iterator.takeWhile(_ => remaining > 0L).foreach { index =>
        (original(index), broods(index)) match {
          case (b: AntBrood.Developing, current: AntBrood.Developing) if !b.laying =>
            val stageEnd = if (b.isEgg) b.eggEnd else if (b.isLarvae) b.larvaEnd else b.totalCare
            // A second pass can fill the unused share, within the original stage and per-tick cap.
            val end = Math.min(stageEnd, b.care + perBrood)
            val care = Math.max(0L, Math.min(remaining, end - current.care))
            if (care > 0L) {
              val before = feeding(b, current.care)
              val after = feeding(b, current.care + care)
              val cost = ActionCost(
                sugar = after.sugar - before.sugar,
                protein = after.protein - before.protein
              )
              if (resources.hasResources(cost)) {
                resources = resources.spendResources(cost)
                broods = broods.updated(index, current.copy(care = current.care + care))
                remaining -= care
                budget -= care
              }
            }
          case _ =>
        }
      }
    }

    val (recovery, ordinary) = order.partition { index =>
      original(index) match {
        case b: AntBrood.Developing => b.kind == EmergencyRecovery.Kind
        case _                      => false
      }
    }
    tendGroup(recovery, budget)

    val priority = if (priorityAvailable(nest)) a.broodPriority else BroodPriority.Balanced
    if (priority == BroodPriority.Balanced) tendGroup(ordinary, budget)
    else {
      val (preferred, others) = ordinary
        .filter { index =>
          original(index) match {
            case b: AntBrood.Developing => !b.laying
            case _                      => false
          }
        }
        .partition { index =>
          original(index) match {
            case b: AntBrood.Developing => priority.prefers(b)
            case _                      => false
          }
        }
      if (preferred.nonEmpty && others.nonEmpty) {
        // A preferred brood has twice the weight of another brood when care is scarce.
        val preferredWeight = preferred.size.toLong * 2L
        val totalWeight = preferredWeight + others.size
        tendGroup(preferred, (BigInt(budget) * preferredWeight / totalWeight).toLong)
        tendGroup(others, budget)
        tendGroup(preferred, budget)
      } else tendGroup(ordinary, budget)
    }
    broods -> resources
  }

  def forecastCost(
      ants: AntsData,
      nest: NestAttributes,
      upgrades: UpgradesData,
      exploration: ExplorationData,
      resources: BasicResources,
      stats: StatisticsData = StatisticsData.initial
  ): ActionCost = {
    val projected =
      RoyalBroodProgression.forecastResources(ants, nest, upgrades, exploration, resources, stats)
    val royal = RoyalBroodProgression.care(ants, nest, projected)
    val afterRoyal = projected.spendResources(royal.cost)
    val available = afterRoyal
    val after = tend(ants, nest, upgrades, available)._2
    ActionCost(
      sugar = available.sugar.amount - after.sugar.amount,
      protein = available.protein.amount - after.protein.amount
    )
  }

  def tick(initial: AllData): AllData = {
    val data = reconcile(initial)
    val a = data.ants
    val count = a.eggsAndLarvae.size
    val (broods, resources) = tend(a, data.nestAttributes, data.upgrades, data.basicResources)
    val larvalSugar = data.basicResources.sugar.amount - resources.sugar.amount
    val remaining = Math.max(0L, a.layingCooldown - 1L)
    val laid = if (remaining == 0L) broods.count {
      case b: AntBrood.Developing => b.laying
      case _                      => false
    }
    else 0
    val developing =
      if (laid == 0) broods
      else
        broods.map {
          case b: AntBrood.Developing if b.laying =>
            b.copy(initialTick = data.currentTick, laying = false)
          case b => b
        }
    val (adults, growing) = developing.partition {
      case b: AntBrood.Developing => b.care >= b.totalCare
      case _                      => false
    }
    val small = adults.count { case b: AntBrood.Developing => b.small; case _ => false }.toLong
    val scouts = adults.count {
      case b: AntBrood.Developing => b.kind == "scout"; case _ => false
    }.toLong
    val workers = adults.size.toLong - small - scouts
    val ants = a.copy(
      smallWorkers = a.smallWorkers + small * u,
      workers = a.workers + workers * u,
      scouts = a.scouts + scouts * u,
      eggsAndLarvae = growing,
      layingCooldown = remaining,
      broodCursor = if (count == 0) 0 else (a.broodCursor + 1) % count
    )
    DefaultWorkerAssignment.normalize(
      PopulationAging.reconcile(
        data.copy(
          ants = ants,
          basicResources = resources,
          nestAttributes = data.nestAttributes
            .copy(nurseryRefuse = Midden.nextRefuse(a, data.nestAttributes, larvalSugar)),
          statistics = data.statistics.copy(
            eggsCount = data.statistics.eggsCount.addValue(laid),
            workersMatured = data.statistics.workersMatured.addValue(small + workers),
            smallWorkersMatured = data.statistics.smallWorkersMatured.addValue(small),
            scoutsMatured = data.statistics.scoutsMatured.addValue(scouts),
            maxAliveWorkers = data.statistics.maxAliveWorkers.max(ants.totalWorkersCount)
          )
        )
      )
    )
  }

  def layingStatus(
      ants: AntsData,
      nest: NestAttributes,
      resources: BasicResources,
      upgrades: UpgradesData = UpgradesData.initial,
      exploration: ExplorationData = ExplorationData.initial,
      stats: StatisticsData = StatisticsData.initial
  ): String = {
    val projected =
      RoyalBroodProgression.forecastResources(ants, nest, upgrades, exploration, resources, stats)
    val royal = RoyalBroodProgression.care(ants, nest, projected)
    val feedingResources = projected.spendResources(royal.cost)
    val nextScout = ants.scoutEggOrders.headOption.contains(0)
    val spaceBlocker = if (ants.queuedEggs > 0) placeBlocker(ants, nest) else None
    val nextCost = if (nextScout) ScoutProgression.eggCost else ActionCost(sugar = eggCost(ants))
    val waiting =
      if (ants.layingBrood.nonEmpty)
        s"Laying egg · ${UINumbersUtils.prettyTimeFromTicks(ants.layingCooldown)}"
      else if (EmergencyRecovery.broodCount(ants) > 0) "Rearing a recovery worker"
      else if (EmergencyRecovery.needed(ants) && resources.sugar.amount < EmergencyRecovery.EggCost)
        EmergencyRecovery.status(ants)
      else if (ants.queuedEggs > 0 && resources.sugar.amount < nextCost.sugar) "Needs sugar"
      else if (nextScout && resources.protein.amount < nextCost.protein)
        "Needs protein for scout egg"
      else if (spaceBlocker.nonEmpty) spaceBlocker.get
      else if (ants.queuedEggs > 0 && ants.populationCount >= nest.maxAntsCount) "Colony slots full"
      else if (ants.layingCooldown > 0L) {
        val next = if (ants.queuedEggs > 0) "Next egg" else "Queen ready"
        s"$next in ${UINumbersUtils.prettyTimeFromTicks(ants.layingCooldown)}"
      } else "Queen ready"
    val larvae = ants.eggsAndLarvae.collect {
      case b: AntBrood.Developing if b.isLarvae && !b.small => b
    }
    val hungry =
      if (larvae.nonEmpty && feedingResources.protein.amount < 50L) " · Needs protein for larvae"
      else if (larvae.nonEmpty && feedingResources.sugar.amount < 500L) " · Needs sugar for larvae"
      else ""
    s"$waiting$hungry"
  }

  def status(
      ants: AntsData,
      nest: NestAttributes,
      resources: BasicResources,
      upgrades: UpgradesData = UpgradesData.initial,
      exploration: ExplorationData = ExplorationData.initial,
      stats: StatisticsData = StatisticsData.initial
  ): String = {
    val emergency = EmergencyRecovery.broodCount(ants)
    val extra = if (emergency > 0) " · 1 queen-tended recovery brood" else ""
    val orders =
      if (ants.scoutEggOrders.isEmpty) s"${ants.eggOrders} eggs ordered"
      else s"${ants.eggOrders} worker · ${ants.scoutEggOrders.size} scout eggs ordered"
    s"$orders · ${occupiedPlaces(ants)}/${usablePlaces(nest)} brood places$extra · ${layingStatus(ants, nest, resources, upgrades, exploration, stats)}"
  }

}
