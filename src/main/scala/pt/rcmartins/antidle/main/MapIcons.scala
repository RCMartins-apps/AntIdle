package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement
import pt.rcmartins.antidle.game.ResourceIcons

/** Decorative map symbols; each map button retains a complete accessible name. */
object MapIcons {
  private val paths = Map(
    "nest" -> ResourceIcons.Colony.path,
    "study" -> "M12 21a4 5 0 1 0 8 0 4 5 0 1 0-8 0Zm2-4L9 11 5 12m13 5 5-6 4 1M12 7h1m3-3h1m3 3h1",
    "collect" -> "M3 21a5 4 0 1 0 10 0 5 4 0 1 0-10 0Zm10 0h4m0 0a4 4 0 1 0 8 0 4 4 0 1 0-8 0ZM10 24l-3 5m7-7 1 7m3-5 4 5m1-12 3-4M21 15c-4-7 1-12 9-12 0 8-3 12-9 12Z",
    "exhausted" -> "M7 16l6 6L25 9",
    "idle" -> "M9 16h14",
    "fog" -> "M10 11a6 6 0 1 1 10 4l-4 3v3m0 5v1",
  )

  def apply(id: String): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "map-terrain-icon",
      aria.hidden := true,
      svg.svg(
        svg.viewBox := (if (id == "nest") "0 0 24 24" else "0 0 32 32"),
        svg.path(svg.d := paths.getOrElse(id, paths("fog")))
      )
    )

}
