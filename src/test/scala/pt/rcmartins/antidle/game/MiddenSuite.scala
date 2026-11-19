package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

class MiddenSuite extends FunSuite {
  test("a staffed midden clears refuse and improves ordinary brood care only when clean") {
    val fresh = AllData.initial
    val nest = fresh.nestAttributes.copy(middenBuilt = true, nurseryRefuse = 30L * u)
    val brood = AntBrood.Developing(0L, "worker")
    val a = AntsData(workers = u, tasks = Seq(AntTask.RefuseCarrier -> u), eggsAndLarvae = Seq(brood))
    assert(!Midden.clean(a, nest))
    assert(Midden.nextRefuse(a, nest) < nest.nurseryRefuse)
    val clean = fresh.copy(ants = a, nestAttributes = nest.copy(nurseryRefuse = 0L))
    val unstaffed = clean.copy(ants = a.copy(tasks = Nil))
    def care(d: AllData): Long = BroodProgression.tick(d).ants.eggsAndLarvae.head.asInstanceOf[AntBrood.Developing].care
    assertEquals(care(clean) * 100L, care(unstaffed) * (100L + Midden.CareBonusPercent))
  }
}
