package pt.rcmartins.antidle.balance

import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

case class Choice(name: String, category: String, apply: AllData => AllData)
case class Decision(choice: Option[Choice], goal: String, missing: List[String] = Nil)

object Strategy {
  import AntTask._
  import ExplorationDestination._
  import UpgradeType._

  def workforceTarget(d: AllData, p: Policy): Int =
    if (!d.ants.standardWorkersUnlocked) 5
    else if (d.upgrades(RoyalNurseryPlanning).unlocked) p.royalPopulation
    else if (d.unlocks.tabs.exploreTabUnlocked) p.population
    else p.earlyPopulation

  private def small(d: AllData, task: AntTask): Long = task match {
    case Tunneler   => d.ants.smallTunnelers
    case Scavenger  => d.ants.smallScavengers
    case BroodNurse => d.ants.smallNurses
    case _          => 0L
  }

  def missing(d: AllData, cost: ActionCost): List[String] = List(
    ("sugar", cost.sugar, d.basicResources.sugar.amount),
    ("protein", cost.protein, d.basicResources.protein.amount),
    ("knowledge", cost.knowledge, d.basicResources.knowledge.amount),
    ("water", cost.water, d.basicResources.water.amount),
    ("resin", cost.resin, d.basicResources.resin.amount),
    ("tunnelling space", cost.tunnelingSpace, d.basicResources.tunnelingSpace.amount),
    ("available workers", cost.idleWorkers, d.ants.availableWorkersCount),
  ).collect { case (name, required, available) if required > available => name }

  private def chamberVisible(d: AllData, kind: ChamberType): Boolean = kind match {
    case ChamberType.Nest        => d.unlocks.actions.canBuildNestUpgrade
    case ChamberType.Queen       => d.unlocks.actions.canBuildQueenChamber
    case ChamberType.FoodStorage => d.unlocks.actions.canBuildFoodStorageChamber
    case ChamberType.Nursery =>
      BroodProgression.nurseryAvailable(d.ants, d.nestAttributes, d.exploration)
    case ChamberType.Aphid        => d.upgrades(AphidTending).unlocked
    case ChamberType.Replete      => d.upgrades(RepleteStorage).unlocked
    case ChamberType.RoyalNursery => d.upgrades(RoyalNurseryPlanning).unlocked
  }

  private def build(d: AllData, kind: ChamberType): Option[Choice] =
    Option.when(
      chamberVisible(d, kind) && d.nestAttributes.buildQueue.isEmpty &&
        Construction.canEnqueue(d.nestAttributes, d.upgrades, kind) &&
        d.hasResources(Construction.nextCost(d.nestAttributes, kind))
    )(Choice(s"Build ${kind.name}", "construction", Construction.enqueue(_, kind)))

  private def jobs(d: AllData, p: Policy): Option[Choice] = {
    val a = d.ants
    val n = (a.workers / u).toInt
    val spaceGoal =
      if (!d.unlocks.tabs.exploreTabUnlocked) 120L * u
      else Math.min(1500L * u, d.basicResources.tunnelingSpace.maxAmount)
    val digging = d.basicResources.tunnelingSpace.amount < spaceGoal
    val smallTargets = List(
      Tunneler -> (if (
                     digging && n < 6 && a.smallWorkers >= 3 * u && d.basicResources.sugar.amount >= 10 * u
                   ) u
                   else 0L),
      Scavenger -> (if (a.standardWorkersUnlocked && n < 5 && a.smallWorkers >= 2 * u) u else 0L),
      BroodNurse -> 0L
    )
    smallTargets
      .collectFirst {
        case (task, target) if small(d, task) != target =>
          Choice(
            s"Small ${task.name}: ${target / u}",
            "assignment",
            BroodProgression.assignSmall(_, task, target - small(d, task))
          )
      }
      .orElse {
        val reserved = a.tasks.filterNot(_._1.showButtons).map(_._2).sum / u
        val home = Math.max(0, n - reserved.toInt)
        val care = a.eggsAndLarvae.nonEmpty || a.queuedEggs > 0
        val protein = d.basicResources.protein.amount
        val targets = List(
          Scavenger -> (if (n >= 6) 2 else if (n >= 1) 1 else 0),
          Tunneler -> (if (digging) Math.min(p.tunnelers, n / 6) else 0),
          BroodNurse -> (if (care) Math.min(p.nurses, n / 10) else 0),
          WaterCollector -> (if (
                               a.tasks.exists(
                                 _._1 == WaterCollector
                               ) && d.basicResources.water.amount < 170L * u
                             ) Math.min(3, n / 10)
                             else 0),
          SeedForager -> (if (
                            d.nestAttributes.dryClearingOutpost && d.basicResources.water.amount > 5 * u
                          ) Math.min(5, n / 5)
                          else 0),
          AphidTender -> (if (protein >= (if (a.taskWorkers(AphidTender) > 0L) 5L else 15L) * u)
                            Math.min(p.aphids, d.nestAttributes.chambers.aphidChamber.level)
                          else 0),
          RoyalNurse -> (if (d.nestAttributes.royalBroodSlots > 0) Math.min(5, n / 5) else 0),
        ).map { case (task, count) =>
          task -> (if (a.tasks.exists(_._1 == task))
                     Math
                       .min(
                         count.toLong,
                         DefaultWorkerAssignment.availableCapacity(a, task, d.nestAttributes) / u
                       )
                       .toInt
                   else 0)
        }
        // Keep reserved workers productive until an actual departure is affordable.
        val preparing = d.unlocks.tabs.exploreTabUnlocked && !d.upgrades(DefaultWorkers).unlocked &&
          d.basicResources.sugar.amount >= 150L * u && ScoutProgression.available(
            a,
            d.exploration
          ) >= u &&
          d.exploration.explorationParties.isEmpty
        val idle = if (preparing) Math.min(5, home / 3) else 0
        val all = targets :+ (SugarCollector -> Math.max(0, home - targets.map(_._2).sum - idle))
        val reductions = all.collectFirst {
          case (task, count)
              if task.showButtons && !a.effectiveDefaultTask
                .contains(task) && a.taskWorkers(task) > count.toLong * u =>
            Choice(
              s"${task.name}: $count",
              "assignment",
              x =>
                x.copy(ants = x.ants.removeFromTask(task, a.taskWorkers(task) - count.toLong * u))
            )
        }
        reductions.orElse(all.collectFirst {
          case (task, count)
              if !a.effectiveDefaultTask.contains(task) && a.tasks.exists(_._1 == task) &&
                a.taskWorkers(task) < count.toLong * u && a.availableWorkersCount > 0L =>
            Choice(
              s"${task.name}: $count",
              "assignment",
              DefaultWorkerAssignment.assign(_, task, count.toLong * u - a.taskWorkers(task))
            )
        })
      }
  }

  def decide(d: AllData, p: Policy): Decision = {
    val a = d.ants; val nest = d.nestAttributes; val e = d.exploration
    def done(c: Choice) = Decision(Some(c), c.name)
    def when(condition: Boolean)(choice: => Choice): Option[Decision] =
      Option.when(condition)(done(choice))
    def first(candidates: (() => Option[Decision])*): Option[Decision] =
      candidates.iterator.map(_()).collectFirst { case Some(decision) => decision }

    val foundingUnderstanding =
      if (!a.lineage.sharedPaths) Seq(Prestige.SharedPaths.id)
      else if (!a.lineage.beyondWoodland && a.lineage.foundings >= 1)
        Seq(Prestige.BeyondWoodland.id)
      else if (
        Prestige.SatelliteNests.available(
          a.lineage.copy(foundings = a.lineage.foundings + 1)
        ) && !a.lineage.satelliteNests
      )
        Seq(Prestige.SatelliteNests.id)
      else Nil
    val orders = p.upgradeOrder.flatMap(name => UpgradeType.all.find(_.name == name)) ++
      (if (a.lineage.beyondWoodland) Seq(AphidShelters, TrailLogistics, CoordinatedExpeditions)
       else Nil)
    val research = orders.find(t => !d.upgrades(t).unlocked && d.upgrades(t).show)
    val target = workforceTarget(d, p)
    val canGrow = nest.maxAntsCount < target + 5 || !d.unlocks.tabs.upgradesTabUnlocked
    val stockNeeded = research.map(_.cost.sugar).getOrElse(3000L * u)
    val scoutBrood = a.eggsAndLarvae.count {
      case b: AntBrood.Developing => b.kind == "scout"; case _ => false
    }
    val scoutTarget = 2 + p.extraScouts + (if (a.lineage.sharedPaths) 1 else 0)
    val incoming = a.totalWorkersCount + BroodProgression.incoming(a) + a.eggOrders
    val preNurseryLimit =
      if (a.standardWorkersUnlocked && nest.chambers.nurseryChamber.level == 0) 7 else target
    val workerOrderCount = Math.min(5, Math.min(target, preNurseryLimit) - incoming)

    def explorationDecision: Decision = {
      val crew = Math.min(3, (ScoutProgression.available(a, e) / u).toInt)
      val study = ExplorationMap
        .availableSites(a)
        .take(e.revealedSites)
        .find(site => ExplorationMap.canScout(d, site.id, crew))
      val routes =
        List(ScavengeWater, CollectResin, SeedGathering, NectarMeadow, WoodlandEdge, RottenLog)
      val needed = routes.filter { route =>
        if (route == ScavengeWater) d.basicResources.water.amount < 170L * u
        else if (route == CollectResin) d.basicResources.resin.amount < 100L * u
        else if (route == NectarMeadow) {
          val royalConstruction =
            if (d.upgrades(RoyalNurseryPlanning).unlocked && nest.royalBroodSlots == 0)
              RoyalNurseryProgression.buildCost.sugar
            else 0L
          d.basicResources.sugar.amount < Math.max(stockNeeded, royalConstruction) + 200L * u
        } else d.basicResources.protein.amount < 120L * u
      }
      val collection = needed.iterator
        .map { route =>
          val workers = ExplorationProgression.selectedWorkers(
            Math.max(p.partyWorkers.toLong * u, route.minimumWorkers),
            a.availableWorkersCount
          )
          route -> workers
        }
        .collectFirst {
          case (route, workers) if ExplorationHelper.canStart(d, workers, route) =>
            done(
              Choice(
                s"Collect ${route.name}",
                "expedition",
                ExplorationHelper.start(_, workers, route)
              )
            )
        }
      first(
        () =>
          study.map(site =>
            done(Choice(s"Study ${site.name}", "scouting", ExplorationMap.scout(_, site.id, crew)))
          ),
        () =>
          when(ExplorationMap.canScout(d, "surroundings", crew))(
            Choice("Reveal next hexagon", "scouting", ExplorationMap.scout(_, "surroundings", crew))
          ),
        () => collection,
      ).getOrElse(
        Decision(
          None,
          "Maintain colony or await scout/collection returns",
          List("scouts, supplies or storage")
        )
      )
    }

    first(
      () =>
        when(Prestige.canFound(d))(
          Choice("Found daughter colony", "founding", Prestige.found(_, foundingUnderstanding))
        ),
      () =>
        when(a.lineage.foundings > 0 && !a.lineage.sharedPaths && a.lineage.dna >= 1L)(
          Choice("Learn Shared Paths", "research", Prestige.purchase)
        ),
      () =>
        when(EmergencyRecovery.canRaise(a, d.basicResources.sugar.amount))(
          Choice("Raise Emergency Worker", "brood", EmergencyRecovery.raise)
        ),
      () =>
        when(d.upgrades(DefaultWorkers).unlocked && a.defaultTask != Some(SugarCollector))(
          Choice(
            "Default task: Sugar Collector",
            "assignment",
            DefaultWorkerAssignment.select(_, Some(SugarCollector))
          )
        ),
      () => jobs(d, p).map(done),
      () =>
        when(RoyalBroodProgression.canStart(d))(
          Choice("Raise Daughter Queen Egg", "brood", RoyalBroodProgression.start)
        ),
      () =>
        when(
          a.lineage.foundings >= 2 && a.lineage.sharedPaths && !a.lineage.beyondWoodland && a.lineage.dna >= 1L
        )(
          Choice(
            "Learn Beyond the Woodland",
            "research",
            Prestige.purchase(_, Prestige.BeyondWoodland.id)
          )
        ),
      () =>
        research
          .filter(UpgradeResearch.canPurchase(d, _))
          .map(t =>
            done(Choice(s"Research ${t.title}", "research", UpgradeResearch.purchase(_, t)))
          ),
      () =>
        when(
          d.upgrades(QueensRoutine).unlocked &&
            (!a.queenRoutine.enabled || a.queenRoutine.targetWorkers != target)
        )(
          Choice(
            s"Queen's Routine: $target workers",
            "automation",
            AutomaticBrood.configure(_, true, target, 50L * u)
          )
        ),
      () => Option.when(canGrow)(build(d, ChamberType.Nest)).flatten.map(done),
      () =>
        Option
          .when(
            d.upgrades(UnlockQueensChamber).unlocked &&
              nest.chambers.queenChamber.level < (if (a.totalWorkersCount >= 20) 3 else 1)
          )(build(d, ChamberType.Queen))
          .flatten
          .map(done),
      () =>
        Option
          .when(nest.chambers.nurseryChamber.level < (if (a.workers >= 35L * u) 2 else 1))(
            build(d, ChamberType.Nursery)
          )
          .flatten
          .map(done),
      () =>
        Option
          .when(
            d.upgrades(UnlockFoodStorageChamber).unlocked &&
              d.basicResources.sugar.maxAmount < stockNeeded + 200L * u
          )(build(d, ChamberType.FoodStorage))
          .flatten
          .map(done),
      () =>
        Option
          .when(d.upgrades(AphidTending).unlocked && nest.chambers.aphidChamber.level < p.aphids)(
            build(d, ChamberType.Aphid)
          )
          .flatten
          .map(done),
      () =>
        when(
          nest.aphids < p.aphids * AphidHusbandry.AphidsPerTender && AphidRelocation.canStart(d)
        )(Choice("Relocate Aphids", "exploration", AphidRelocation.start)),
      () =>
        Option
          .when(d.upgrades(RepleteStorage).unlocked && nest.chambers.repleteChamber.level < 1)(
            build(d, ChamberType.Replete)
          )
          .flatten
          .map(done),
      () =>
        when(
          d.upgrades(RepleteStorage).unlocked && a.repletes == 0L &&
            nest.chambers.repleteChamber.level > 0 && a.availableWorkersCount >= u && d
              .hasResources(RepleteProgression.cost)
        )(Choice("Promote Replete", "promotion", RepleteProgression.promote)),
      () =>
        when(OutpostProgression.canEstablish(d))(
          Choice("Establish Dry Clearing Outpost", "construction", OutpostProgression.establish)
        ),
      () =>
        when(
          nest.dryClearingOutpost && a.lineage.satelliteNests && nest.dryClearingSpecialization.isEmpty
        )(
          Choice(
            "Choose Seed Station",
            "construction",
            OutpostProgression.specialize(_, OutpostProgression.SeedStation)
          )
        ),
      () =>
        Option
          .when(RoyalNurseryProgression.canPlan(nest, d.upgrades))(
            build(d, ChamberType.RoyalNursery)
          )
          .flatten
          .map(done),
      () =>
        when(
          d.unlocks.tabs.exploreTabUnlocked &&
            a.scouts / u + a.scoutEggOrders.size + scoutBrood < scoutTarget && ScoutProgression
              .canRaise(d)
        )(Choice("Order Scout Egg", "brood", data => ScoutProgression.raise(data))),
      () =>
        when(
          !a.queenRoutine.enabled && incoming < Math.min(target, preNurseryLimit) &&
            d.unlocks.actions.canLayEggs &&
            d.basicResources.sugar.amount >= BroodProgression
              .eggCost(a) + (if (a.standardWorkersUnlocked) 20L * u else 0L) &&
            BroodProgression.orderAmount(a, nest, workerOrderCount) > 0
        )(Choice("Order Worker Eggs", "brood", BroodProgression.order(_, workerOrderCount))),
      () =>
        e.discoveredCarcasses.zipWithIndex
          .find { case (carcass, _) => CarcassProgression.canRetrieve(a, nest, e, carcass) }
          .map { case (_, index) =>
            done(Choice("Retrieve Carcass", "expedition", CarcassProgression.retrieve(_, index)))
          },
      () =>
        when(
          a.lineage.sharedPaths &&
            PheromoneTrails.target(e, ExplorationDestination.NectarMeadow.id) < 10L * u &&
            PheromoneTrails.canAdjust(
              d,
              ExplorationDestination.NectarMeadow.id,
              PheromoneTrails.Step
            )
        )(
          Choice(
            "Assign nectar trail workers",
            "trail",
            PheromoneTrails.adjust(_, ExplorationDestination.NectarMeadow.id, PheromoneTrails.Step)
          )
        ),
      () => Option.when(d.unlocks.tabs.exploreTabUnlocked)(explorationDecision),
    ).getOrElse {
      research
        .map(t => Decision(None, s"Research ${t.title}", missing(d, t.cost)))
        .getOrElse(Decision(None, "Grow founding workforce", List("brood development or housing")))
    }
  }

}
