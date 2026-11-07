package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

object BroodProgression {
  def capacity(nest: NestAttributes): Int = nest.maxEggsCount + Math.max(0, nest.chambers.nurseryChamber.level) * 3
  def eggCost(ants: AntsData): Long = if (ants.standardWorkersUnlocked) LayEggSugarCost else LayEggSugarCost / 2L
  def interval(ants: AntsData, nest: NestAttributes): Long = {
    val base = (if (ants.standardWorkersUnlocked) 20L else 5L) * TicksPerSecond
    (base * 100L + 99L + nest.chambers.queenChamber.level * 25L) / (100L + nest.chambers.queenChamber.level * 25L)
  }
  def incoming(ants: AntsData): Int = ants.eggsAndLarvae.count {
    case b: AntBrood.Developing => b.kind != "scout"
    case _: AntBrood.Egg => true
    case _ => false
  }
  def orderAmount(ants: AntsData, nest: NestAttributes, sugar: Long, requested: Int): Int =
    Math.max(0L, Math.min(Math.max(0, requested).toLong,
      Math.min(nest.maxAntsCount.toLong - ants.populationCount - ants.eggOrders, sugar / eggCost(ants)))).toInt
  def order(data: AllData, requested: Int): AllData = {
    val amount = orderAmount(data.ants, data.nestAttributes, data.basicResources.sugar.amount, requested)
    data.copy(ants = data.ants.copy(eggOrders = data.ants.eggOrders + amount))
  }
  def cancel(data: AllData): AllData = data.copy(ants = data.ants.copy(eggOrders = 0))
  def canLay(ants: AntsData, nest: NestAttributes, sugar: Long, reserve: Long = 0L): Boolean =
    ants.layingCooldown <= 0L && ants.eggsAndLarvae.size < capacity(nest) &&
      ants.populationCount < nest.maxAntsCount && sugar - reserve >= eggCost(ants)
  def lay(data: AllData, reserve: Long = 0L): AllData = {
    if (!canLay(data.ants, data.nestAttributes, data.basicResources.sugar.amount, reserve)) data
    else data.spendResources(ActionCost(sugar = eggCost(data.ants))).copy(
      ants = data.ants.copy(layingCooldown = interval(data.ants, data.nestAttributes),
        eggsAndLarvae = data.ants.eggsAndLarvae :+ AntBrood.Developing(data.currentTick,
          if (data.ants.standardWorkersUnlocked) "worker" else "small")),
      statistics = data.statistics.copy(eggsCount = data.statistics.eggsCount.addValue(1L)))
  }
  def layOrdered(data: AllData): AllData = {
    if (data.ants.eggOrders <= 0) data
    else {
      val laid = lay(data)
      if (laid.ants.eggsAndLarvae.size == data.ants.eggsAndLarvae.size) laid
      else laid.copy(ants = laid.ants.copy(eggOrders = laid.ants.eggOrders - 1))
    }
  }
  def scavenged(ants: AntsData, room: Long): Long =
    Math.min(Math.max(0L, room), (ants.taskWorkers(AntTask.Scavenger) / u * 2L + ants.smallScavengers / u) * 50L)

  def assignSmall(data: AllData, task: AntTask, delta: Long): AllData = {
    val a = data.ants
    val current = task match {
      case AntTask.Tunneler => a.smallTunnelers
      case AntTask.Scavenger => a.smallScavengers
      case AntTask.BroodNurse => a.smallNurses
      case _ => 0L
    }
    val limit = task match {
      case AntTask.Tunneler => Long.MaxValue
      case AntTask.Scavenger if a.standardWorkersUnlocked => Math.max(0L, 2L * u - a.taskWorkers(task))
      case AntTask.BroodNurse => Math.max(0L, data.nestAttributes.chambers.nurseryChamber.level.toLong * 5L * u - a.taskWorkers(task))
      case _ => 0L
    }
    val moved = if (delta > 0L) Math.min(delta / u * u, Math.min(a.smallCollectors, Math.max(0L, limit - current)))
      else -Math.min(current, -delta / u * u)
    val ants = task match {
      case AntTask.Tunneler => a.copy(smallTunnelers = current + moved)
      case AntTask.Scavenger => a.copy(smallScavengers = current + moved)
      case AntTask.BroodNurse => a.copy(smallNurses = current + moved)
      case _ => a
    }
    reconcile(data.copy(ants = ants))
  }
  def reconcile(initial: AllData): AllData = {
    val standard = initial.ants.standardWorkersUnlocked || initial.upgrades(UpgradeType.StandardWorkers).unlocked
    val a = initial.ants
    val tunnelers = Math.min(Math.max(0L, a.smallTunnelers), a.smallWorkers)
    val scavengers = if (!standard) 0L else Math.min(Math.max(0L, a.smallScavengers),
      Math.min(a.smallWorkers - tunnelers, Math.max(0L, 2L * u - a.taskWorkers(AntTask.Scavenger))))
    val nurses = Math.min(Math.max(0L, a.smallNurses), Math.min(a.smallWorkers - tunnelers - scavengers,
      Math.max(0L, initial.nestAttributes.chambers.nurseryChamber.level.toLong * 5L * u - a.taskWorkers(AntTask.BroodNurse))))
    val ants = a.copy(standardWorkersUnlocked = standard, smallTunnelers = tunnelers, smallScavengers = scavengers,
      smallNurses = nurses, layingCooldown = Math.max(0L, a.layingCooldown), eggOrders = Math.max(0, a.eggOrders))
    val unlocked = if (standard) ants.unlockTask(AntTask.Scavenger) else ants
    val nursed = if (initial.nestAttributes.chambers.nurseryChamber.level > 0) unlocked.unlockTask(AntTask.BroodNurse) else unlocked
    initial.copy(ants = nursed, exploration = initial.exploration.copy(proteinDiscovered = initial.exploration.proteinDiscovered || standard),
      unlocks = initial.unlocks.copy(tabs = initial.unlocks.tabs.copy(upgradesTabUnlocked = initial.unlocks.tabs.upgradesTabUnlocked || a.smallWorkers >= 5L * u)))
  }
  private def feeding(brood: AntBrood.Developing, progress: Long): ActionCost = {
    if (brood.small) ActionCost.empty
    else {
      val larvalCare = Math.max(0L, Math.min(brood.larvaEnd - brood.eggEnd, progress - brood.eggEnd))
      ActionCost(sugar = 20L * u * larvalCare / (brood.larvaEnd - brood.eggEnd),
        protein = 2L * u * larvalCare / (brood.larvaEnd - brood.eggEnd))
    }
  }
  def nursePower(ants: AntsData): Long = (ants.taskWorkers(AntTask.BroodNurse) / u * 2L + ants.smallNurses / u) * u
  private def tend(a: AntsData, upgrades: UpgradesData, initialResources: BasicResources): (Vector[AntBrood], BasicResources) = {
    val count = a.eggsAndLarvae.size
    val boost = if (upgrades(UpgradeType.BroodCare).unlocked) 125L else 100L
    var budget = (3L * u + nursePower(a)) * boost / 100L
    val perBrood = (if (nursePower(a) > 0L) 2L * u else u) * boost / 100L
    var resources = initialResources
    var broods = a.eggsAndLarvae.toVector
    if (count > 0) (0 until count).foreach { offset =>
      val index = (Math.max(0, a.broodCursor) % count + offset) % count
      broods(index) match {
        case b: AntBrood.Developing =>
          val end = if (b.isEgg) b.eggEnd else if (b.isLarvae) b.larvaEnd else b.totalCare
          val care = Math.max(0L, Math.min(budget, Math.min(perBrood, end - b.care)))
          val before = feeding(b, b.care)
          val after = feeding(b, b.care + care)
          val cost = ActionCost(sugar = after.sugar - before.sugar, protein = after.protein - before.protein)
          if (care > 0L && resources.hasResources(cost)) {
            resources = resources.spendResources(cost)
            broods = broods.updated(index, b.copy(care = b.care + care))
            budget -= care
          }
        case _ =>
      }
    }
    broods -> resources
  }
  def forecastCost(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData,
      exploration: ExplorationData, resources: BasicResources): ActionCost = {
    val projected = RoyalBroodProgression.forecastResources(ants, nest, upgrades, exploration, resources)
    val royal = RoyalBroodProgression.care(ants, nest, projected)
    val afterRoyal = projected.spendResources(royal.cost)
    val available = afterRoyal.copy(sugar = afterRoyal.sugar.copy(amount = Math.min(afterRoyal.sugar.amount, afterRoyal.sugar.maxAmount)))
    val after = tend(ants, upgrades, available)._2
    ActionCost(sugar = available.sugar.amount - after.sugar.amount, protein = available.protein.amount - after.protein.amount)
  }
  def tick(initial: AllData): AllData = {
    val data = reconcile(initial)
    val a = data.ants
    val count = a.eggsAndLarvae.size
    val (broods, resources) = tend(a, data.upgrades, data.basicResources)
    val (adults, growing) = broods.partition {
      case b: AntBrood.Developing => b.care >= b.totalCare
      case _ => false
    }
    val small = adults.count { case b: AntBrood.Developing => b.small; case _ => false }.toLong
    val scouts = adults.count { case b: AntBrood.Developing => b.kind == "scout"; case _ => false }.toLong
    val workers = adults.size.toLong - small - scouts
    val ants = a.copy(smallWorkers = a.smallWorkers + small * u, workers = a.workers + workers * u,
      scouts = a.scouts + scouts * u, eggsAndLarvae = growing, layingCooldown = Math.max(0L, a.layingCooldown - 1L),
      broodCursor = if (count == 0) 0 else (a.broodCursor + 1) % count)
    DefaultWorkerAssignment.normalize(PopulationAging.reconcile(data.copy(ants = ants, basicResources = resources,
      statistics = data.statistics.copy(workersMatured = data.statistics.workersMatured.addValue(small + workers),
        smallWorkersMatured = data.statistics.smallWorkersMatured.addValue(small), scoutsMatured = data.statistics.scoutsMatured.addValue(scouts),
        maxAliveWorkers = data.statistics.maxAliveWorkers.max(ants.totalWorkersCount)))))
  }
  def status(ants: AntsData, nest: NestAttributes, resources: BasicResources): String = {
    val queue = s"${ants.eggOrders} eggs ordered · ${ants.eggsAndLarvae.size}/${capacity(nest)} brood places"
    val waiting = if (ants.eggOrders > 0 && resources.sugar.amount < eggCost(ants)) "Needs sugar"
      else if (ants.eggOrders > 0 && ants.eggsAndLarvae.size >= capacity(nest)) "Brood places full"
      else if (ants.layingCooldown > 0L) s"Next egg in ${UINumbersUtils.prettyTimeFromTicks(ants.layingCooldown)}"
      else "Queen ready"
    val larvae = ants.eggsAndLarvae.collect { case b: AntBrood.Developing if b.isLarvae && !b.small => b }
    val hungry = if (larvae.nonEmpty && resources.protein.amount < 50L) " · Needs protein for larvae"
      else if (larvae.nonEmpty && resources.sugar.amount < 500L) " · Needs sugar for larvae" else ""
    s"$queue · $waiting$hungry"
  }
}
