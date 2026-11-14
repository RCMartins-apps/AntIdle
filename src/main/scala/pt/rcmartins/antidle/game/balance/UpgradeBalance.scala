package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.game.DiscoveryProgression
import pt.rcmartins.antidle.game.Constants.{FoodStorageChamberBonusMaxSugar, u}
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

/** Conditions for earning visibility. Once earned, visibility normally persists.
  * These are progression prerequisites, not affordability checks.
  */
case class UpgradeRequirements(
    upgrades: List[UpgradeType] = Nil,
    minimumRevealedSites: Int = 0,
    explorationTab: Boolean = false,
    explorationAvailable: Boolean = false,
    discovery: Option[String] = None,
    nursery: Boolean = false,
    outpost: Boolean = false,
    aging: Boolean = false,
    proteinDiscovered: Boolean = false,
    minimumSmallWorkers: Int = 0,
    earlyResearch: Boolean = false,
) {
  def met(data: AllData): Boolean =
    upgrades.forall(data.upgrades(_).unlocked) &&
      data.exploration.revealedSites >= minimumRevealedSites &&
      (!explorationTab || data.unlocks.tabs.exploreTabUnlocked) &&
      (!explorationAvailable || UpgradeBalance.explorationAvailable(data)) &&
      discovery.forall(DiscoveryProgression.has(data.exploration, _)) &&
      (!nursery || data.nestAttributes.chambers.nurseryChamber.level > 0) &&
      (!outpost || data.nestAttributes.dryClearingOutpost) &&
      (!aging || data.ants.ages.active) &&
      (!proteinDiscovered || data.exploration.proteinDiscovered) &&
      (minimumSmallWorkers == 0 || (!data.ants.standardWorkersUnlocked && data.ants.smallWorkers >= minimumSmallWorkers.toLong * u)) &&
      (!earlyResearch || data.nestAttributes.chambers.nestChamber.level > 0 || data.unlocks.tabs.upgradesTabUnlocked ||
        data.unlocks.resources.showKnowledge || data.basicResources.knowledge.amount > 0L)
}

case class UpgradeDefinition(title: String, cost: ActionCost, requirements: UpgradeRequirements, description: String)

/** Edit upgrade prices and reveal prerequisites here. Keep saved UpgradeType names stable.
  * Effect implementations remain in their mechanic modules; see docs/BALANCING.md.
  */
object UpgradeBalance {
  def explorationAvailable(data: AllData): Boolean =
    data.unlocks.tabs.exploreTabUnlocked || data.upgrades(UnlockExplorerTask).unlocked

  def apply(upgrade: UpgradeType): UpgradeDefinition = definitions(upgrade)

  lazy val definitions: Map[UpgradeType, UpgradeDefinition] = Map(
    StandardWorkers -> UpgradeDefinition(
      title = "Standard Worker Development",
      cost = ActionCost(sugar = 75 * u, tunnelingSpace = 10 * u),
      requirements = UpgradeRequirements(minimumSmallWorkers = 5),
      description = "Future worker eggs: full-sized workers (2× small-worker output). Brood requires protein. Unlocks Scraps Gatherers. Existing ants and brood keep their caste.",
    ),
    UnlockQueensChamber -> UpgradeDefinition(
      title = "Unlock Queen's Chamber",
      cost = ActionCost(sugar = 100 * u),
      requirements = UpgradeRequirements(earlyResearch = true),
      description = "Chamber upgrades: more brood places and faster laying.",
    ),
    ImproveSugarCollectorTask1 -> UpgradeDefinition(
      title = "Foraging Techniques 1",
      cost = ActionCost(sugar = 150 * u),
      requirements = UpgradeRequirements(earlyResearch = true),
      description = "+15% sugar collection.",
    ),
    ImproveSugarCollectorTask2 -> UpgradeDefinition(
      title = "Foraging Techniques 2",
      cost = ActionCost(sugar = 750 * u, knowledge = 75 * u),
      requirements = UpgradeRequirements(explorationAvailable = true, upgrades = List(ImproveSugarCollectorTask1)),
      description = "+15% sugar collection.",
    ),
    ImproveSugarCollectorTask3 -> UpgradeDefinition(
      title = "Foraging Techniques 3",
      cost = ActionCost(sugar = 2500 * u, knowledge = 300 * u),
      requirements = UpgradeRequirements(upgrades = List(ImproveSugarCollectorTask2)),
      description = "+15% sugar collection.",
    ),
    UnlockFoodStorageChamber -> UpgradeDefinition(
      title = "Unlock Food Storage Chamber",
      cost = ActionCost(sugar = 400 * u),
      requirements = UpgradeRequirements(upgrades = List(UnlockQueensChamber, ImproveSugarCollectorTask1)),
      description = s"Food Storage Chamber: +${FoodStorageChamberBonusMaxSugar / u} sugar capacity per level.",
    ),
    UnlockExplorerTask -> UpgradeDefinition(
      title = "Local Exploration",
      cost = ActionCost(sugar = 750 * u),
      requirements = UpgradeRequirements(upgrades = List(UnlockFoodStorageChamber)),
      description = "Unlocks the map: scouts reveal and study sites; workers collect resources.",
    ),
    DefaultWorkers -> UpgradeDefinition(
      title = "Default Workers",
      cost = ActionCost(sugar = 500 * u, knowledge = 50 * u),
      requirements = UpgradeRequirements(explorationAvailable = true),
      description = "Idle workers join a chosen default task and remain available for colony actions.",
    ),
    AdditionalExpeditionSlot -> UpgradeDefinition(
      title = "Expedition Coordination",
      cost = ActionCost(sugar = 1000 * u, knowledge = 100 * u),
      requirements = UpgradeRequirements(explorationTab = true, minimumRevealedSites = 2),
      description = "+1 expedition slot.",
    ),
    ExpeditionSupplies1 -> UpgradeDefinition(
      title = "Expedition Supplies 1",
      cost = ActionCost(sugar = 500L * u, knowledge = 20L * u),
      requirements = UpgradeRequirements(explorationAvailable = true, minimumRevealedSites = 1),
      description = "+20% base expedition haul capacity.",
    ),
    ExpeditionSupplies2 -> UpgradeDefinition(
      title = "Expedition Supplies 2",
      cost = ActionCost(sugar = 1500L * u, knowledge = 80L * u),
      requirements = UpgradeRequirements(upgrades = List(ExpeditionSupplies1), minimumRevealedSites = 3),
      description = "+20% base expedition haul capacity (40% total).",
    ),
    ExpeditionSupplies3 -> UpgradeDefinition(
      title = "Expedition Supplies 3",
      cost = ActionCost(sugar = 4500L * u, knowledge = 200L * u),
      requirements = UpgradeRequirements(upgrades = List(ExpeditionSupplies2), minimumRevealedSites = 5),
      description = "+20% base expedition haul capacity (60% total).",
    ),
    EstablishedTrails -> UpgradeDefinition(
      title = "Established Trails",
      cost = ActionCost(sugar = 600 * u, knowledge = 60 * u),
      requirements = UpgradeRequirements(explorationTab = true, minimumRevealedSites = 1),
      description = "−20% sugar-yield loss per added collector.",
    ),
    ReinforcedMandibles -> UpgradeDefinition(
      title = "Reinforced Mandibles",
      cost = ActionCost(sugar = 1200 * u, protein = 20 * u, knowledge = 100 * u),
      requirements = UpgradeRequirements(explorationTab = true, minimumRevealedSites = 2),
      description = "+50% Tunneler output.",
    ),
    BroodCare -> UpgradeDefinition(
      title = "Brood Care",
      cost = ActionCost(sugar = 2000 * u, protein = 40 * u, knowledge = 180 * u),
      requirements = UpgradeRequirements(explorationTab = true, nursery = true, minimumRevealedSites = 3),
      description = "−20% worker/scout development time; includes growing brood.",
    ),
    AphidTending -> UpgradeDefinition(
      title = "Aphid Tending",
      cost = ActionCost(sugar = 1500 * u, protein = 20 * u, knowledge = 100 * u),
      requirements = UpgradeRequirements(explorationTab = true, discovery = Some("wild-aphids")),
      description = "Unlocks Aphid Chamber: +10 aphid places per level. One tender per 10 aphids: +0.8 sugar/s · −0.01 protein/s.",
    ),
    ProteinStorage -> UpgradeDefinition(
      title = "Protein Storage",
      cost = ActionCost(sugar = 1000 * u, protein = 25 * u, knowledge = 75 * u),
      requirements = UpgradeRequirements(explorationTab = true, proteinDiscovered = true, upgrades = List(UnlockFoodStorageChamber)),
      description = "+100 protein capacity per Food Storage Chamber level; includes existing levels.",
    ),
    QueensRoutine -> UpgradeDefinition(
      title = "Queen's Routine",
      cost = ActionCost(sugar = 1200 * u, protein = 20 * u, knowledge = 100 * u),
      requirements = UpgradeRequirements(explorationTab = true, nursery = true, minimumRevealedSites = 2),
      description = "Automatic worker eggs and aging-job replacements. Set a population target and sugar reserve in Brood.",
    ),
    ConstructionPlanning -> UpgradeDefinition(
      title = "Construction Planning",
      cost = ActionCost(sugar = 500 * u, knowledge = 50 * u),
      requirements = UpgradeRequirements(explorationTab = true, minimumRevealedSites = 1),
      description = "+2 construction queue slots (3 total).",
    ),
    ColonyNutrition -> UpgradeDefinition(
      title = "Colony Nutrition",
      cost = ActionCost(sugar = 2500 * u, protein = 60 * u, knowledge = 200 * u),
      requirements = UpgradeRequirements(aging = true, explorationTab = true, minimumRevealedSites = 3),
      description = "New workers and scouts: +20% lifespan.",
    ),
    RepleteStorage -> UpgradeDefinition(
      title = "Replete Storage",
      cost = ActionCost(sugar = 1500 * u, protein = 20 * u, knowledge = 150 * u),
      requirements = UpgradeRequirements(explorationTab = true, discovery = Some("dew-plants")),
      description = "Unlocks Replete Chamber and worker promotion for water storage.",
    ),
    CoveredTrails -> UpgradeDefinition(
      title = "Covered Trails",
      cost = ActionCost(sugar = 3000 * u, protein = 60 * u, knowledge = 300 * u, water = 100 * u),
      requirements = UpgradeRequirements(outpost = true, minimumRevealedSites = 5),
      description = "−25% Seed Forager water use (0.015 water/s each).",
    ),
    SeedProcessing -> UpgradeDefinition(
      title = "Seed Processing",
      cost = ActionCost(sugar = 6000 * u, protein = 100 * u, knowledge = 700 * u, water = 150 * u),
      requirements = UpgradeRequirements(outpost = true, minimumRevealedSites = 5),
      description = "+50% Seed Forager protein output (0.06 protein/s each).",
    ),
    RoyalNurseryPlanning -> UpgradeDefinition(
      title = "Royal Nursery Planning",
      cost = ActionCost(sugar = 5000 * u, protein = 100 * u, knowledge = 700 * u, water = 150 * u),
      requirements = UpgradeRequirements(nursery = true, discovery = Some("resin-seep"), minimumRevealedSites = 5),
      description = "Unlocks Royal Nursery construction.",
    )
  )
}
