package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.model.BasicResources.BasicResource

class ResourceTimingSuite extends FunSuite {
  test("full and empty estimates round up fractional ticks") {
    val resource = BasicResource(5L, 10L)
    assertEquals(ResourceTiming.estimate(resource, 3L).get.ticks, 2L)
    assertEquals(ResourceTiming.estimate(resource, -3L).get.ticks, 2L)
    assertEquals(ResourceTiming.estimate(resource, -3L).get.toEmpty, true)
    assertEquals(ResourceTiming.estimate(BasicResource(0L, 10L), -3L).get.text, "Empty")
    assertEquals(ResourceTiming.estimate(BasicResource(1L, 10L), -3L).get.text, "Empty in ~0.2s")
  }

  test("full storage keeps its status at zero income but shows depletion when draining") {
    for (rate <- Seq(0L, 3L)) assertEquals(ResourceTiming.estimate(BasicResource(10L, 10L), rate).get.text, "Full")
    val draining = ResourceTiming.estimate(BasicResource(10L, 10L), -2L).get
    assertEquals(draining.text, "Empty in ~1.0s")
    assertEquals(ResourceTiming.estimate(BasicResource(20L, 10L), -2L).get.ticks, 10L)
    assertEquals(ResourceTiming.estimate(BasicResource(20L, 10L), 0L).get.text, "Full")
  }

  test("no false full countdown for unlimited, absent or stationary capacity") {
    assertEquals(ResourceTiming.estimate(BasicResource(10L, Long.MaxValue), 2L), None)
    assertEquals(ResourceTiming.estimate(BasicResource(0L, 0L), 2L), None)
    assertEquals(ResourceTiming.estimate(BasicResource(5L, 10L), 0L), None)
    assertEquals(ResourceTiming.estimate(BasicResource(10L, Long.MaxValue), -2L).get.ticks, 5L)
  }

  test("large reserves and extreme drain rates do not overflow") {
    assertEquals(ResourceTiming.estimate(BasicResource(Long.MaxValue, Long.MaxValue), -1L).get.ticks, Long.MaxValue)
    assertEquals(ResourceTiming.estimate(BasicResource(Long.MaxValue, Long.MaxValue), Long.MinValue).get.ticks, 1L)
  }
}
