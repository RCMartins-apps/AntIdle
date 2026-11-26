package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.model._

class MapFeedbackSuite extends FunSuite {
  test("hidden hexes do not reveal names and idle study differs from active study") {
    assertEquals(MapFeedback.state(ExplorationData.initial, "woodland-edge", 0).kind, "unknown")
    assertEquals(
      MapFeedback.state(ExplorationData(revealedSites = 1), "woodland-edge", 0).kind,
      "study"
    )
    val data = ExplorationData(
      revealedSites = 1,
      explorationParties =
        List(ExplorationParty(0, 10, 110, destination = "woodland-edge", mission = "study"))
    )
    assertEquals(
      MapFeedback.state(data, "woodland-edge", 60),
      MapFeedback.State("studying", "Studying 50%", Some(50))
    )
    assertEquals(MapFeedback.state(data, "woodland-edge", 200).percent, Some(100))
    assertEquals(MapFeedback.state(data, "woodland-edge", 0).percent, Some(0))
  }

  test("reveal progress belongs to nest, resource routes belong to their site") {
    val data = ExplorationData(
      revealedSites = 4,
      explorationParties = List(
        ExplorationParty(0, 0, 100, mission = "reveal"),
        ExplorationParty(50000, 0, 100, destination = "scavenge-water")
      )
    )
    assertEquals(MapFeedback.state(data, "surroundings", 25).kind, "scouting")
    assertEquals(MapFeedback.state(data, "dry-clearing", 25).kind, "collecting")
  }

}
