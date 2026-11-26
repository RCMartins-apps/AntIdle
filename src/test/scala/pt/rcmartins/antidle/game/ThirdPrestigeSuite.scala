package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import zio.json._

class ThirdPrestigeSuite extends FunSuite {
  private val lineage = Lineage(2, 0L, sharedPaths = true, beyondWoodland = true)
  private val unlock = Seq(Prestige.SatelliteNests.id)

  private def colony: AllData = {
    val fresh = AllData.initial
    fresh.copy(
      ants = AntsData(
        scouts = 3L * u,
        daughterQueens = u,
        lineage = lineage.copy(foundings = 3, satelliteNests = true)
      ),
      nestAttributes = fresh.nestAttributes.copy(dryClearingOutpost = true),
      exploration = ExplorationData(revealedSites = 6, surveys = Map("dry-clearing" -> 3)),
      unlocks = fresh.unlocks.copy(tabs = fresh.unlocks.tabs.copy(exploreTabUnlocked = true)),
      basicResources = fresh.basicResources.copy(
        sugar = BasicResource(2000L * u, 3000L * u),
        knowledge = BasicResource(150L * u, Long.MaxValue),
        protein = BasicResource(40L * u, 100L * u),
        water = BasicResource(150L * u, 200L * u),
        tunnelingSpace = BasicResource(750L * u, 1000L * u)
      )
    )
  }

  test("the third founding inherits the Understanding and resets the station") {
    val ready = colony.copy(ants = colony.ants.copy(lineage = lineage))
    val early = ready.copy(ants = ready.ants.copy(lineage = lineage.copy(foundings = 1)))
    assertEquals(Prestige.found(early, unlock), early)
    val missing = ready.copy(ants = ready.ants.copy(lineage = lineage.copy(beyondWoodland = false)))
    assertEquals(Prestige.found(missing, unlock), missing)
    val founded = Prestige.found(ready, unlock)
    assertEquals(founded.ants.lineage, lineage.copy(foundings = 3, satelliteNests = true))
    assertEquals(founded.nestAttributes, NestAttributes.initial)
    val legacy = """{"foundings":3,"dna":1,"sharedPaths":true,"beyondWoodland":true}"""
      .fromJson[Lineage]
      .toOption
      .get
    assert(!legacy.satelliteNests)
  }

  test("establishment discounts sugar and Knowledge, and a station choice survives save and load") {
    val ready = colony.copy(nestAttributes = NestAttributes.initial)
    assert(!OutpostProgression.canEstablish(ready.copy(ants = ready.ants.copy(lineage = lineage))))
    val established = OutpostProgression.establish(ready)
    assert(established.nestAttributes.dryClearingOutpost)
    assertEquals(
      established.basicResources,
      ready.basicResources.spendResources(
        OutpostProgression.cost.copy(sugar = 1500L * u, knowledge = 150L * u)
      )
    )
    val station = OutpostProgression.specialize(established, OutpostProgression.SeedStation)
    assertEquals(OutpostProgression.specialize(station, OutpostProgression.ScoutStation), station)
    val loaded =
      AllDataForSave.fromAllData(station).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(
      OutpostProgression.specialization(loaded.nestAttributes, loaded.ants.lineage),
      OutpostProgression.SeedStation
    )
    val staffed = station.ants.copy(workers = 5L * u, tasks = Seq(AntTask.SeedForager -> (5L * u)))
    val harvest = OutpostProgression.harvestPerTick(
      staffed,
      station.nestAttributes,
      station.upgrades,
      ready.basicResources
    )
    assertEquals(harvest, OutpostProgression.Harvest(protein = 600L, water = 250L))
  }

  test("Scout Station trades water for faster studies with two or three scouts") {
    val station = OutpostProgression.specialize(colony, OutpostProgression.ScoutStation)
    val id = "woodland-edge"
    assert(!ExplorationMap.supportedStudy(station, "surroundings", 2))
    val sent = ExplorationMap.scout(station, id, 2)
    val party = sent.exploration.explorationParties.head
    assertEquals(
      party.targetTick - party.startingTick,
      ExplorationMap.scoutDuration(2, false) * 3L / 5L
    )
    assertEquals(sent.basicResources.water.amount, station.basicResources.water.amount - 5L * u)
    val dry = station.copy(basicResources =
      station.basicResources.copy(water = BasicResource(0L, 200L * u))
    )
    assertEquals(ExplorationMap.scout(dry, id, 2), dry)
    assert(ExplorationMap.canScout(dry, id, 1))
  }

}
