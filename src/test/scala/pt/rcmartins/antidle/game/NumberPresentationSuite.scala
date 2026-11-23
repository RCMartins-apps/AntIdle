package pt.rcmartins.antidle.game
import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
class NumberPresentationSuite extends FunSuite {
  test("compact values consistently use K, M, B and preserve signs at boundaries") {
    assertEquals(UINumbersUtils.prettyNumberStr(999 * u), "999")
    assertEquals(UINumbersUtils.prettyNumberStr(1000 * u), "1K")
    assertEquals(UINumbersUtils.prettyNumberStr(1000000 * u), "1M")
    assertEquals(UINumbersUtils.prettyNumberStr(1250000 * u), "1.25M")
    assertEquals(UINumbersUtils.prettyNumberStr(-1250000 * u), "-1.25M")
    assertEquals(UINumbersUtils.prettyNumberStr(Long.MinValue), "-922.34T")
    assertEquals(UINumbersUtils.prettyNumberStr(Long.MaxValue), "922.34T")
  }
  test("small values and negative rates never lose their sign or become a misleading zero") {
    assertEquals(UINumbersUtils.prettyNumberStr(1), "<0.01")
    assertEquals(UINumbersUtils.prettyNumberStr(150L), "0.02")
    assertEquals(UINumbersUtils.prettyNumberStr(1000L * u - 1L), "1K")
    assertEquals(UINumbersUtils.preciseRateStr(-1), ">−0.01")
    assertEquals(UINumbersUtils.resourceRateStr(-50), ">−0.01")
    assertEquals(UINumbersUtils.resourceRateStr(4000), "0.4")
  }
}
