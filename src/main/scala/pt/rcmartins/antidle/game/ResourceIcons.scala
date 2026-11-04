package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement

object ResourceIcons {
  sealed abstract class ResourceIcon(val id: String, val path: String)

  case object Sugar extends ResourceIcon("sugar",
    "M12 2 21 7v10l-9 5-9-5V7l9-5ZM3 7l9 5 9-5M12 12v10M7.5 4.5l9 5")
  case object Protein extends ResourceIcon("protein",
    "M8 4C3 5 2 10 4 15s7 7 12 4 7-9 3-13-7-4-11-2ZM14 8a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z")
  case object Knowledge extends ResourceIcon("knowledge",
    "M12 6C9 4 6 4 3 5v15c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1ZM12 6v15M6 9h3M15 9h3M6 12h3M15 12h3")
  case object Tunneling extends ResourceIcon("tunneling",
    "M3 21 16 8M5 6c7-6 14-4 17 4l-6-2-4-5M5 6l7-3")
  case object Workers extends ResourceIcon("workers",
    "M12 3a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5ZM12 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 14a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7ZM10 4 8 2M14 4l2-2M10 10 5 8l-2 2M14 10l5-2 2 2M10 12l-5 1-2 3M14 12l5 1 2 3M9 17l-3 1v3M15 17l3 1v3")
  case object Scouts extends ResourceIcon("scouts",
    "M7 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM7 11a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM7 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 7 3 4M6 12l-4-1M8 13l4 1M5 17l-3 2M9 18l3 3M18 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10ZM20 5l-1 3-3 1 1-3 3-1Z")
  case object Eggs extends ResourceIcon("eggs",
    "M20 14c0 5-3.6 8-8 8s-8-3-8-8S8 2 12 2s8 7 8 12ZM9 7l-2 4")
  case object Colony extends ResourceIcon("colony",
    "M2 21 9 7h6l7 14H2ZM9 21v-3a3 3 0 0 1 6 0v3M10 7V3h4v4M8 12h3M15 12h1")

  def forLabel(label: String): Option[ResourceIcon] = label.toLowerCase match {
    case "sugar" => Some(Sugar)
    case "protein" => Some(Protein)
    case "knowledge" => Some(Knowledge)
    case "tunneling space" => Some(Tunneling)
    case "scouts" => Some(Scouts)
    case "eggs" | "egg slots" => Some(Eggs)
    case "ants" | "colony slots" => Some(Colony)
    case "workers" | "idle workers" | "party workers" => Some(Workers)
    case resourceLabel if resourceLabel.endsWith(" workers") => Some(Workers)
    case _ => None
  }

  def icon(resource: ResourceIcon): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := s"resource-icon resource-icon-${resource.id}",
      aria.hidden := true,
      svg.svg(svg.viewBox := "0 0 24 24", svg.path(svg.d := resource.path)),
    )
}
