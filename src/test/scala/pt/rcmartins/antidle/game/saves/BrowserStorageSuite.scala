package pt.rcmartins.antidle.game.saves

import munit.FunSuite

class BrowserStorageSuite extends FunSuite {
  test("production and development keys remain unchanged") {
    Seq("/", "/AntIdle/", "/AntIdle/index.html", "/pr-preview/pr-no/").foreach { path =>
      assertEquals(BrowserStorage.key("save", path), "save")
    }
  }
  test("PR paths isolate and canonicalize saves and preferences") {
    val first = BrowserStorage.key("save", "/AntIdle/pr-preview/pr-9/")
    assertEquals(first, "save:pr-preview:9")
    assertEquals(BrowserStorage.key("save", "/AntIdle/pr-preview/pr-9/index.html"), first)
    assertEquals(BrowserStorage.key("save", "/AntIdle/pr-preview/pr-9"), first)
    assertNotEquals(BrowserStorage.key("save", "/AntIdle/pr-preview/pr-10/"), first)
    assertEquals(BrowserStorage.key("antidle.ui", "/AntIdle/pr-preview/pr-9/"), "antidle.ui:pr-preview:9")
  }
}
