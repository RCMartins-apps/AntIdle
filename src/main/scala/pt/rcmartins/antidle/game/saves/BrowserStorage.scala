package pt.rcmartins.antidle.game.saves

/** Canonical preview namespace, stable for /, index.html and subsequent PR pushes. */
object BrowserStorage {
  private val Preview = "(?:^|/)pr-preview/pr-([1-9][0-9]*)(?:/|$)".r
  def key(base: String, pathname: String): String =
    Preview.findFirstMatchIn(pathname).fold(base)(m => s"$base:pr-preview:${m.group(1)}")

}
