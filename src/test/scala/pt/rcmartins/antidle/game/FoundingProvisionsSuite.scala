package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

class FoundingProvisionsSuite extends FunSuite {
  test("the fourth Understanding adds three eggs’ worth of provisions on each new colony") {
    val ready = AllData.initial.copy(ants =
      AntsData(
        daughterQueens = u,
        lineage = Lineage(3, 0L, sharedPaths = true, beyondWoodland = true, satelliteNests = true)
      )
    )
    val founded = Prestige.found(ready, Seq(Prestige.FoundingProvisions.id))
    assert(founded.ants.lineage.foundingProvisions)
    assertEquals(
      founded.basicResources.sugar.amount,
      AllData.initial.basicResources.sugar.amount + 3L * BroodProgression.eggCost(founded.ants)
    )
    assertEquals(founded.statistics.sugarU.total, ready.statistics.sugarU.total)
    assertEquals(Prestige.found(ready).basicResources, AllData.initial.basicResources)
    val again = Prestige.found(founded.copy(ants = founded.ants.copy(daughterQueens = u)))
    assertEquals(again.basicResources.sugar.amount, founded.basicResources.sugar.amount)
  }

}
