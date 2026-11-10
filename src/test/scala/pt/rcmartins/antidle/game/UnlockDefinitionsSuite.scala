package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.balance.UpgradeBalance
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._
import zio.json._

class UnlockDefinitionsSuite extends FunSuite {
  def ready: AllData = {
    val d = AllData.initial
    d.copy(ants = d.ants.copy(smallWorkers = 5L * u, ages = d.ants.ages.copy(active = true)),
      nestAttributes = d.nestAttributes.copy(dryClearingOutpost = true, chambers = d.nestAttributes.chambers.copy(
        nestChamber = d.nestAttributes.chambers.nestChamber.copy(level = 1),
        nurseryChamber = d.nestAttributes.chambers.nurseryChamber.copy(level = 1))),
      unlocks = d.unlocks.copy(tabs = d.unlocks.tabs.copy(exploreTabUnlocked = true)),
      upgrades = UpgradeType.all.foldLeft(d.upgrades)((upgrades, t) => upgrades.unlock(t)),
      exploration = d.exploration.copy(revealedSites = 6, proteinDiscovered = true,
        surveys = DiscoveryProgression.all.map(_.destination -> 100).toMap))
  }

  test("catalog covers stable upgrade identities and references known research/discoveries") {
    assertEquals(UpgradeBalance.definitions.keySet, UpgradeType.all.toSet)
    assertEquals(UpgradeType.all.map(_.name).distinct.size, UpgradeType.all.size)
    UpgradeBalance.definitions.foreach { case (upgrade, definition) =>
      assert(definition.requirements.upgrades.forall(UpgradeType.all.contains), upgrade.name)
      assert(!definition.requirements.upgrades.contains(upgrade), upgrade.name)
      assert(definition.requirements.discovery.forall(id => DiscoveryProgression.all.exists(_.id == id)), upgrade.name)
      assert(definition.requirements.minimumRevealedSites >= 0)
      assert(definition.requirements.minimumRevealedSites <= ExplorationMap.sites.size)
    }
    val saved = AllDataForSave.fromAllData(ready).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assert(UpgradeType.all.forall(saved.upgrades(_).unlocked))
  }

  test("every upgrade's declared prerequisites control its reveal boundary") {
    UpgradeType.all.foreach { upgrade =>
      val d = ready
      val r = upgrade.definition.requirements
      assert(upgrade.shouldShow(d), upgrade.name)
      if (r.minimumRevealedSites > 0) {
        val before = d.copy(exploration = d.exploration.copy(revealedSites = r.minimumRevealedSites - 1))
        assert(!upgrade.shouldShow(before), upgrade.name)
        assert(upgrade.shouldShow(before.copy(exploration = before.exploration.copy(revealedSites = before.exploration.revealedSites + 1))), upgrade.name)
      }
      r.upgrades.foreach { required =>
        assert(!upgrade.shouldShow(d.copy(upgrades = d.upgrades.modify(required)(_.copy(unlocked = false)))), upgrade.name)
      }
      r.discovery.foreach { id =>
        val discovery = DiscoveryProgression.all.find(_.id == id).get
        assert(!upgrade.shouldShow(d.copy(exploration = d.exploration.copy(surveys = d.exploration.surveys.updated(discovery.destination, discovery.trips - 1)))), upgrade.name)
      }
      if (r.nursery) assert(!upgrade.shouldShow(d.copy(nestAttributes = d.nestAttributes.copy(chambers = d.nestAttributes.chambers.copy(nurseryChamber = d.nestAttributes.chambers.nurseryChamber.copy(level = 0))))), upgrade.name)
      if (r.outpost) assert(!upgrade.shouldShow(d.copy(nestAttributes = d.nestAttributes.copy(dryClearingOutpost = false))), upgrade.name)
      if (r.aging) assert(!upgrade.shouldShow(d.copy(ants = d.ants.copy(ages = d.ants.ages.copy(active = false)))), upgrade.name)
      if (r.proteinDiscovered) assert(!upgrade.shouldShow(d.copy(exploration = d.exploration.copy(proteinDiscovered = false))), upgrade.name)
      if (r.minimumSmallWorkers > 0) {
        assert(!upgrade.shouldShow(d.copy(ants = d.ants.copy(smallWorkers = (r.minimumSmallWorkers - 1L) * u))), upgrade.name)
        assert(!upgrade.shouldShow(d.copy(ants = d.ants.copy(standardWorkersUnlocked = true))), upgrade.name)
      }
    }
  }

  test("research purchase validates visibility and price, applies effects once, and rejects repeat charges") {
    val fresh = AllData.initial
    val rich = fresh.copy(basicResources = fresh.basicResources.copy(sugar = fresh.basicResources.sugar.copy(amount = 1000L * u)))
    assertEquals(UpgradeResearch.purchase(rich, UnlockExplorerTask), rich)
    val eligible = rich.copy(upgrades = rich.upgrades.show(UnlockQueensChamber))
    assert(UpgradeResearch.canPurchase(eligible, UnlockQueensChamber))
    val purchased = UpgradeResearch.purchase(eligible, UnlockQueensChamber)
    assertEquals(purchased.basicResources.sugar.amount, eligible.basicResources.sugar.amount - UnlockQueensChamber.cost.sugar)
    assert(purchased.upgrades(UnlockQueensChamber).unlocked)
    assertEquals(UpgradeResearch.purchase(purchased, UnlockQueensChamber), purchased)
    val poor = eligible.copy(basicResources = eligible.basicResources.copy(sugar = eligible.basicResources.sugar.copy(amount = UnlockQueensChamber.cost.sugar - 1L)))
    assert(!UpgradeResearch.canPurchase(poor, UnlockQueensChamber))
    assertEquals(UpgradeResearch.purchase(poor, UnlockQueensChamber), poor)
    val stale = rich.copy(upgrades = rich.upgrades.show(DefaultWorkers))
    assert(!UpgradeResearch.isVisible(stale, DefaultWorkers))
    assertEquals(UpgradeResearch.purchase(stale, DefaultWorkers), stale)
  }

  test("earned visibility survives a transient prerequisite loss and save/load") {
    val d = UpgradeVisibility.reconcile(AllData.initial.copy(ants = AllData.initial.ants.copy(smallWorkers = 5L * u)))
    assert(d.upgrades(StandardWorkers).show)
    val spent = d.copy(ants = d.ants.copy(smallWorkers = 4L * u))
    assert(!StandardWorkers.shouldShow(spent))
    assert(UpgradeResearch.isVisible(spent, StandardWorkers))
    assert(AllDataForSave.fromAllData(spent).toAllData.upgrades(StandardWorkers).show)
  }

  test("all destinations share their level/discovery gate between visibility and departure") {
    val d = ready
    val ants = AntsData(workers = 37L * u, scouts = 5L * u)
    ExplorationDestination.all.filter(route => ExplorationMap.siteForRoute(route.id).nonEmpty).foreach { route =>
      val unlocked = d.exploration.copy(revealedSites = 6, explorationParties = Nil)
      assert(route.isUnlocked(unlocked, ants, d.nestAttributes), route.id)
      assert(ExplorationHelper.canDepart(unlocked, ants, d.upgrades, route.minimumWorkers, route), route.id)
      val below = unlocked.copy(revealedSites = 0)
      assert(!route.isUnlocked(below, ants, d.nestAttributes), route.id)
      assert(!ExplorationHelper.canDepart(below, ants, d.upgrades, route.minimumWorkers, route), route.id)
      route.requiredDiscovery.foreach { id =>
        val missing = DiscoveryProgression.all.find(_.id == id).get
        val below = unlocked.copy(surveys = unlocked.surveys.updated(missing.destination, missing.trips - 1))
        assert(!route.isUnlocked(below, ants, d.nestAttributes), route.id)
        assert(!ExplorationHelper.canDepart(below, ants, d.upgrades, route.minimumWorkers, route), route.id)
      }
    }
  }

  test("supplies catalog drives actual price, unlock boundary, and milestone hints") {
    val fresh = AllData.initial
    ExplorationProgression.suppliesUpgrades.zipWithIndex.foreach { case (tier, rank) =>
      val d = fresh.copy(exploration = fresh.exploration.copy(suppliesLevel = rank, revealedSites = tier.requiredSites),
        basicResources = fresh.basicResources.copy(sugar = fresh.basicResources.sugar.copy(amount = tier.cost.sugar),
          knowledge = fresh.basicResources.knowledge.copy(amount = tier.cost.knowledge)))
      assertEquals(ExplorationProgression.suppliesCost(d.exploration), tier.cost)
      val before = d.copy(exploration = d.exploration.copy(revealedSites = d.exploration.revealedSites - 1))
      assertEquals(ExplorationProgression.upgradeSupplies(before), before)
      val upgraded = ExplorationProgression.upgradeSupplies(d)
      assertEquals(upgraded.exploration.suppliesLevel, rank + 1)
      assertEquals(upgraded.basicResources.sugar.amount, 0L)
      assertEquals(upgraded.basicResources.knowledge.amount, 0L)
    }
  }
}
