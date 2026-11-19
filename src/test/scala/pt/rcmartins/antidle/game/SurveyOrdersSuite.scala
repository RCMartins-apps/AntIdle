package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

class SurveyOrdersSuite extends FunSuite {
  test("a survey order pays each trip, waits if supplies run out and stops at discovery") {
    val id = "woodland-edge"
    val fresh = AllData.initial
    val ready = fresh.copy(ants = AntsData(scouts = u), exploration = ExplorationData(revealedSites = 1),
      basicResources = fresh.basicResources.copy(sugar = BasicResources.BasicResource(10L * u, 100L * u)),
      unlocks = fresh.unlocks.copy(tabs = fresh.unlocks.tabs.copy(exploreTabUnlocked = true)))
    def finish(data: AllData): AllData = {
      val party = data.exploration.explorationParties.head
      ExplorationMap.finishScout(data.copy(world = data.world.copy(currentTick = party.targetTick)), party)
    }
    val started = SurveyOrders.start(ready, id, 1)
    assertEquals(started.exploration.surveyOrder.map(_.target), Some(3))
    val returned = finish(started)
    assertEquals(SurveyOrders.tick(returned), returned)
    val supplied = returned.copy(basicResources = ready.basicResources.copy(sugar = BasicResources.BasicResource(20L * u, 100L * u)))
    val done = SurveyOrders.tick(finish(SurveyOrders.tick(finish(SurveyOrders.tick(supplied)))))
    assertEquals(done.exploration.surveyOrder, None)
    assertEquals(ExplorationMap.studies(done.exploration, id), 3)
    assertEquals(done.basicResources.sugar.amount, 0L)
  }
}
