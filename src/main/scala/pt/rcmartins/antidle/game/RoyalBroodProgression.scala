package pt.rcmartins.antidle.game

import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

object RoyalBroodProgression {
  val MaxNurses: Long = 5L
  val TotalCare: Long = MaxNurses * 600L * TicksPerSecond
  val EggCare: Long = TotalCare / 10L
  val LarvaEnd: Long = TotalCare - EggCare
  val startCost: ActionCost = ActionCost(sugar = 500 * u, protein = 50 * u, water = 20 * u)
  val larvaUnitCost: ActionCost = ActionCost(sugar = 5000L / TicksPerSecond,
    protein = 500L / TicksPerSecond, water = 100L / TicksPerSecond)
  val tendingUnitCost: ActionCost = ActionCost(water = 100L / TicksPerSecond)

  def capacity(nest: NestAttributes): Long = nest.royalBroodSlots.toLong * MaxNurses * u
  def nurses(ants: AntsData, nest: NestAttributes): Long =
    Math.min(capacity(nest), Math.max(0L, ants.taskWorkers(AntTask.RoyalNurse))) / u

  def freeSlots(ants: AntsData, nest: NestAttributes): Long =
    Math.max(0L, nest.royalBroodSlots.toLong - ants.royalBrood.size - ants.daughterQueens / u)

  def stage(brood: RoyalBrood): String =
    if (brood.careUnits < EggCare) "Royal egg" else if (brood.careUnits < LarvaEnd) "Royal larva" else "Royal pupa"

  def stageEnd(brood: RoyalBrood): Long =
    if (brood.careUnits < EggCare) EggCare else if (brood.careUnits < LarvaEnd) LarvaEnd else TotalCare

  def unitCost(brood: RoyalBrood): ActionCost =
    if (brood.careUnits >= EggCare && brood.careUnits < LarvaEnd) larvaUnitCost else tendingUnitCost

  def scaledCost(cost: ActionCost, units: Long): ActionCost =
    ActionCost(sugar = cost.sugar * units, protein = cost.protein * units, water = cost.water * units)

  val rearingCost: ActionCost = ActionCost(
    sugar = larvaUnitCost.sugar * (LarvaEnd - EggCare),
    protein = larvaUnitCost.protein * (LarvaEnd - EggCare),
    water = tendingUnitCost.water * TotalCare)

  case class Care(units: Long = 0L, cost: ActionCost = ActionCost.empty)

  def care(ants: AntsData, nest: NestAttributes, resources: BasicResources): Care = ants.royalBrood match {
    case Some(brood) if !brood.paused && nest.royalBroodSlots > ants.daughterQueens / u && brood.careUnits < TotalCare =>
      val cost = unitCost(brood)
      def limit(available: Long, price: Long): Long = if (price == 0L) Long.MaxValue else Math.max(0L, available) / price
      val units = List(nurses(ants, nest), stageEnd(brood) - Math.max(0L, brood.careUnits),
        limit(resources.sugar.amount, cost.sugar), limit(resources.protein.amount, cost.protein),
        limit(resources.water.amount, cost.water)).min
      Care(units, scaledCost(cost, units))
    case _ => Care()
  }

  // Forecast the same steady-state production and ordinary upkeep that precede royal care.
  // Unstored sugar production can feed brood directly, just as it feeds adult ants.
  def forecastResources(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData,
      exploration: ExplorationData, resources: BasicResources): BasicResources = {
    val sugar = Foraging.production(ants, upgrades) +
      AphidHusbandry.productionPerTick(ants, nest, upgrades, resources.protein.amount)
    val feeding = AphidHusbandry.proteinPerTick(ants, nest, upgrades, resources.protein.amount)
    val harvest = OutpostProgression.harvestPerTick(ants, nest, upgrades, resources)
    val upkeep = ScoutProgression.upkeepPerTick(ants, exploration) - CarcassProgression.foodOffset(exploration, ants)
    resources.copy(sugar = resources.sugar.copy(amount = Math.max(0L, resources.sugar.amount + PheromoneTrails.production(ants, exploration, resources) + sugar - upkeep)),
      protein = (resources.protein - feeding) + (harvest.protein + BroodProgression.scavenged(ants, resources.protein.freeSpace - harvest.protein + feeding)), water = resources.water - harvest.water)
  }

  def forecast(ants: AntsData, nest: NestAttributes, upgrades: UpgradesData,
      exploration: ExplorationData, resources: BasicResources): Care =
    care(ants, nest, forecastResources(ants, nest, upgrades, exploration, resources))

  def canStart(ants: AntsData, nest: NestAttributes, resources: BasicResources): Boolean =
    ants.royalBrood.isEmpty && freeSlots(ants, nest) > 0L && BroodProgression.availableColonySlots(ants, nest) >= u &&
      resources.hasResources(startCost)

  def canStart(data: AllData): Boolean = canStart(data.ants, data.nestAttributes, data.basicResources)

  def start(data: AllData): AllData = {
    if (!canStart(data)) data
    else {
      val ready = DefaultWorkerAssignment.normalize(PopulationAging.tick(data))
      if (!canStart(ready)) ready
      else ResourceVisibility.reconcile(ready.spendResources(startCost)
        .modify(_.ants.royalBrood).setTo(Some(RoyalBrood()))
        .modify(_.statistics.eggsCount).using(_.addValue(1))
        .modify(_.statistics.royalEggsRaised).using(_.addValue(1)))
    }
  }

  def setPaused(data: AllData, paused: Boolean): AllData =
    data.modify(_.ants.royalBrood).using(_.map(_.copy(paused = paused)))

  def reconcile(data: AllData): AllData = {
    val ready = if (data.nestAttributes.royalBroodSlots > 0 && !data.ants.tasks.exists(_._1 == AntTask.RoyalNurse))
      data.modify(_.ants).using(_.unlockTask(AntTask.RoyalNurse)) else data
    ready.ants.royalBrood match {
      case Some(brood) if brood.careUnits < 0L || brood.careUnits > TotalCare =>
        ready.modify(_.ants.royalBrood).setTo(Some(brood.copy(careUnits = Math.max(0L, Math.min(TotalCare, brood.careUnits)))))
      case _ => ready
    }
  }

  def tick(data: AllData): AllData = {
    val ready = reconcile(data)
    val tending = care(ready.ants, ready.nestAttributes, ready.basicResources)
    val updated = if (tending.units == 0L) ready else ready.spendResources(tending.cost)
      .modify(_.ants.royalBrood).using(_.map(b => b.copy(careUnits = b.careUnits + tending.units)))
    if (updated.ants.royalBrood.exists(_.careUnits >= TotalCare) &&
      updated.ants.daughterQueens / u < updated.nestAttributes.royalBroodSlots)
      ResourceVisibility.reconcile(updated.modify(_.ants).using(a => a.copy(royalBrood = None, daughterQueens = a.daughterQueens + u))
        .modify(_.statistics.daughterQueensMatured).using(_.addValue(1))
        .addMessage("A daughter queen has matured!"))
    else updated
  }

  def remainingTicks(brood: RoyalBrood, nurseCount: Long): Long = {
    if (nurseCount <= 0L) -1L
    else List(EggCare, LarvaEnd, TotalCare).foldLeft((Math.max(0L, brood.careUnits), 0L)) {
      case ((done, ticks), end) =>
        val remaining = Math.max(0L, end - done)
        (Math.max(done, end), ticks + (remaining + nurseCount - 1L) / nurseCount)
    }._2
  }

  def status(ants: AntsData, nest: NestAttributes, resources: BasicResources): String = ants.royalBrood match {
    case None => if (ants.daughterQueens > 0L) "Daughter queen mature" else "No royal brood to tend"
    case Some(brood) if brood.paused => "Care paused"
    case Some(_) if nest.royalBroodSlots == 0 => "Needs Royal Nursery"
    case Some(_) if ants.daughterQueens / u >= nest.royalBroodSlots => "Royal nursery full"
    case Some(_) if nurses(ants, nest) == 0L => "Needs Royal Nurses"
    case Some(brood) =>
      val planned = care(ants, nest, resources)
      val wanted = Math.min(nurses(ants, nest), stageEnd(brood) - brood.careUnits)
      val costs = scaledCost(unitCost(brood), if (planned.units == 0L) 1L else wanted)
      val missing = List("sugar" -> (resources.sugar.amount < costs.sugar),
        "protein" -> (resources.protein.amount < costs.protein), "water" -> (resources.water.amount < costs.water))
        .collect { case (name, true) => name }.mkString(" · ")
      if (missing.nonEmpty) (if (planned.units == 0L) "Needs " else "Limited care · ") + missing
      else if (brood.careUnits >= TotalCare) "Royal nursery full"
      else s"Tending ${stage(brood).toLowerCase}"
  }
}
