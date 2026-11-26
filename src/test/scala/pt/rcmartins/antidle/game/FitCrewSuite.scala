package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

class FitCrewSuite extends FunSuite {
  test("fit uses the smallest useful crew and unavailable cargo cannot be selected") {
    val route = ExplorationDestination.ScavengeWater
    val initial = AllData.initial
    val d = initial.copy(
      ants = AntsData(workers = 30L * u),
      basicResources =
        initial.basicResources.copy(water = BasicResources.BasicResource(0L, 70L * u)),
      exploration = MapFixture.explored
    )
    assertEquals(ExplorationHelper.fitCrew(d, route), Some(10L * u))
    val limited = d.copy(exploration =
      d.exploration.copy(depleted = Map(route.id -> (ExplorationMap.deposits(route.id) - 20L * u)))
    )
    assertEquals(ExplorationHelper.fitCrew(limited, route), Some(5L * u))
    assertEquals(ExplorationHelper.fitCrew(d, ExplorationDestination.RivalFoodCache), None)
  }

}
