package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement

/** Decorative terrain symbols; each map button retains a complete accessible name. */
object MapIcons {
  private val paths = Map(
    "surroundings" -> "M3 20h26M5 20a11 11 0 0 1 22 0M12 20v-4a4 4 0 0 1 8 0v4M16 8V3m-4 3 4-3 4 3M8 24h16M11 28h10",
    "woodland-edge" -> "M16 29V13M16 22 9 15M16 18l7-6M5 5c15-4 24 1 23 12-12 5-23-1-23-12Z",
    "nectar-meadow" -> "M16 29V17m0 7c-8 0-10-5-10-5 7-1 10 5 10 5Zm0 0c8 0 10-5 10-5-7-1-10 5-10 5ZM13 8c-6-9 12-9 6 0 10-3 10 12 1 9 0 9-13 9-13 0-10 3-10-12 0-9ZM12 12a4 4 0 1 0 8 0 4 4 0 1 0-8 0Z",
    "rotten-log" -> "M7 10h18c7 0 7 16 0 16H7c-7 0-7-16 0-16ZM7 10c7 0 7 16 0 16M6 14c4 0 4 8 0 8M16 14h7M16 21h6M19 10V5m0 3 5-3",
    "dry-clearing" -> "M3 27h26M4 22l7-4 6 4 7-5 5 4M22 5a4 4 0 1 0 0 8 4 4 0 1 0 0-8ZM22 1v1m0 14v1M14 9h-1m18 0h-1M8 4C5 9 3 11 3 14a5 5 0 0 0 10 0c0-3-2-5-5-10Z",
    "fallen-pine" -> "M13 2 5 12h5L3 22h9v7h3v-7h8l-7-10h5L13 2ZM25 15c-2 4-4 6-4 9a4 4 0 0 0 8 0c0-3-2-5-4-9Z",
    "rival-food-cache" -> "M16 3 2 27h28L16 3ZM16 12v7m0 4v1M9 27l-3 4m17-4 3 4",
    "aphid-thicket" -> "M16 29V14M16 21 8 14M16 17l7-7M5 4c15-3 23 2 22 12-11 4-23-1-22-12ZM6 22a3 3 0 1 0 6 0 3 3 0 1 0-6 0ZM23 22a3 3 0 1 0 6 0 3 3 0 1 0-6 0Z",
    "fallen-oak" -> "M5 12h22v14H5V12ZM9 12V5m0 3 6-4M8 18h16M8 22h12",
    "root-hollow" -> "M3 26h26M7 26V12a9 9 0 0 1 18 0v14M12 26v-7a4 4 0 0 1 8 0v7M11 9l5 3 5-3",
    "fog" -> "M10 11a6 6 0 1 1 10 4l-4 3v3m0 5v1",
  )
  def apply(id: String): ReactiveHtmlElement[HTMLSpanElement] =
    span(className := "map-terrain-icon", aria.hidden := true,
      svg.svg(svg.viewBox := "0 0 32 32", svg.path(svg.d := paths.getOrElse(id, paths("fog")))))
}
