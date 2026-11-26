package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model._
import zio.json._

class DrySeasonChallengeSuite extends FunSuite {
  private val eligible =
    Lineage(3, 0L, sharedPaths = true, beyondWoodland = true, satelliteNests = true)

  private val ready = AllData.initial.copy(ants = AntsData(daughterQueens = u, lineage = eligible))

  test("challenge entry is opt-in, validates eligibility and survives save and load") {
    val tooEarly = ready.copy(ants = ready.ants.copy(lineage = eligible.copy(foundings = 2)))
    assertEquals(Prestige.found(tooEarly, drySeason = true), tooEarly)
    assert(!Prestige.found(ready).ants.lineage.drySeasonActive)
    val active = Prestige.found(ready, drySeason = true)
    assert(active.ants.lineage.drySeasonActive)
    assertEquals(active.ants.lineage.dna, Prestige.DnaReward)
    val loaded =
      AllDataForSave.fromAllData(active).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assert(loaded.ants.lineage.drySeasonActive)
    val staffed =
      loaded.ants.copy(workers = 5L * u, tasks = Seq(AntTask.WaterCollector -> (5L * u)))
    assertEquals(
      OutpostProgression.dewPerTick(staffed),
      OutpostProgression.dewPerTick(staffed.copy(lineage = eligible)) / 2L
    )
  }

  test("reward requires a ready daughter, an outpost and two live repletes") {
    val active = ready.copy(
      ants = ready.ants.copy(lineage = eligible.copy(drySeasonActive = true), repletes = 2L * u),
      nestAttributes = ready.nestAttributes.copy(dryClearingOutpost = true)
    )
    val noQueen = active.copy(ants = active.ants.copy(daughterQueens = 0L))
    assertEquals(Prestige.found(noQueen), noQueen)
    assertEquals(Prestige.found(active, drySeason = true), active)
    val noOutpost = active.copy(nestAttributes = NestAttributes.initial)
    val oneReplete = active.copy(ants = active.ants.copy(repletes = u))
    for (incomplete <- Seq(noOutpost, oneReplete)) {
      val exited = Prestige.found(incomplete)
      assert(!exited.ants.lineage.drySeasonCompleted)
      assert(!exited.ants.lineage.drySeasonActive)
      assert(DrySeasonChallenge.canEnter(exited.ants.lineage))
    }
    val completed = Prestige.found(active)
    assert(completed.ants.lineage.drySeasonCompleted)
    assert(!completed.ants.lineage.drySeasonActive)
    assert(!DrySeasonChallenge.canEnter(completed.ants.lineage))
    val later = Prestige.found(completed.copy(ants = completed.ants.copy(daughterQueens = u)))
    val promoted = later.copy(ants = later.ants.copy(repletes = 2L * u))
    assertEquals(
      RepleteProgression.capacity(promoted),
      BasicResources.initial.water.maxAmount + 250L * u
    )
  }

}
