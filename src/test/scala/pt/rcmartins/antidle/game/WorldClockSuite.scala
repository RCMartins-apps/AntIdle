package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.model.WorldData

class WorldClockSuite extends FunSuite {
  private val world = WorldData(42L, 10L, 30L, 1000L)

  test("an older wall time rebases the saved clock and preserves tick credit") {
    val rebased = world.creditElapsed(world.elapsedSinceSave(850L), 850L)
    assertEquals(rebased, world.copy(lastUpdateTime = 850L))
    assertEquals(rebased.creditElapsed(rebased.elapsedSinceSave(1050L), 1050L).targetTick, 31L)
  }

  test("fractional ticks survive small updates and the saved time anchor") {
    val first = world.creditElapsed(world.elapsedSinceSave(1150L), 1150L)
    assertEquals(first, world)
    val next = first.creditElapsed(first.elapsedSinceSave(1260L), 1260L)
    assertEquals(next, world.copy(targetTick = 31L, lastUpdateTime = 1200L))
  }

  test("large forward elapsed time still uses the stored tick cap") {
    val future = 7201037L
    val credited = world.creditElapsed(world.elapsedSinceSave(future), future)
    assertEquals(credited.targetTick, world.currentTick + Constants.MaxTicksStored + 1L)
    assertEquals(credited.lastUpdateTime, 7201000L)
  }
}
