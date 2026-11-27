package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement

/** Decorative map symbols; each map button retains a complete accessible name. */
object MapIcons {
  private val paths = Map(
    "study" -> "M16 8C11 4 6 4 2 6v20c5-2 10-1 14 2 4-3 9-4 14-2V6c-4-2-9-2-14 2ZM16 8v20M6 11l6 1M6 16l6 1M20 12l6-1M20 17l6-1",
    "collect" -> "M5 11h22v18H5V11ZM10 11V4h12v7M5 17h22M16 11v18M11 7h10",
    "scouting" -> "M20 20l10 10M13 3a10 10 0 1 0 0 20 10 10 0 1 0 0-20ZM13 8v10M8 13h10",
    "surroundings" -> "M3 20h26M5 20a11 11 0 0 1 22 0M12 20v-4a4 4 0 0 1 8 0v4M16 8V3m-4 3 4-3 4 3M8 24h16M11 28h10",
    "exhausted" -> "M7 16l6 6L25 9",
    "idle" -> "M9 16h14",
    "fog" -> "M10 11a6 6 0 1 1 10 4l-4 3v3m0 5v1",
  )

  def apply(id: String): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "map-terrain-icon",
      aria.hidden := true,
      svg.svg(svg.viewBox := "0 0 32 32", svg.path(svg.d := paths.getOrElse(id, paths("fog"))))
    )

}
