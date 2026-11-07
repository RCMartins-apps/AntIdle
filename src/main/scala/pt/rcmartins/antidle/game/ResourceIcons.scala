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
  case object Water extends ResourceIcon("water",
    "M12 2C10 6 5 11 5 16a7 7 0 0 0 14 0c0-5-5-10-7-14ZM8 16a4 4 0 0 0 4 4")
  case object Resin extends ResourceIcon("resin",
    "M7 3h9l5 7-3 10-10 1-6-9 5-9ZM7 3l2 8-7 1M9 11l9 9M9 11l12-1M16 3l-7 8-1 10")
  case object Knowledge extends ResourceIcon("knowledge",
    "M12 6C9 4 6 4 3 5v15c3-1 6-1 9 1 3-2 6-2 9-1V5c-3-1-6-1-9 1ZM12 6v15M6 9h3M15 9h3M6 12h3M15 12h3")
  case object Tunneling extends ResourceIcon("tunneling",
    "M3 21 16 8M5 6c7-6 14-4 17 4l-6-2-4-5M5 6l7-3")
  case object Workers extends ResourceIcon("workers",
    "M12 3a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5ZM12 9a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 14a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7ZM10 4 8 2M14 4l2-2M10 10 5 8l-2 2M14 10l5-2 2 2M10 12l-5 1-2 3M14 12l5 1 2 3M9 17l-3 1v3M15 17l3 1v3")
  case object Repletes extends ResourceIcon("repletes",
    "M12 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 7a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 11a7 5 0 1 0 0 10 7 5 0 0 0 0-10ZM10 4 7 2M14 4l3-2M10 8 5 7M14 8l5-1M6 14l-3-2M18 14l3-2M6 18l-3 2M18 18l3 2")
  case object Scouts extends ResourceIcon("scouts",
    "M7 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM7 11a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM7 15a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM5 7 3 4M6 12l-4-1M8 13l4 1M5 17l-3 2M9 18l3 3M18 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10ZM20 5l-1 3-3 1 1-3 3-1Z")
  case object Queens extends ResourceIcon("queens",
    "M8 3l2 3h4l2-3M12 6a3 3 0 1 0 0 6 3 3 0 0 0 0-6ZM12 13a5 4 0 1 0 0 8 5 4 0 0 0 0-8ZM9 9 4 7M15 9l5-2M8 15l-5-2M16 15l5-2M8 19l-4 3M16 19l4 3")
  case object Eggs extends ResourceIcon("eggs",
    "M20 14c0 5-3.6 8-8 8s-8-3-8-8S8 2 12 2s8 7 8 12ZM9 7l-2 4")
  case object Colony extends ResourceIcon("colony",
    "M2 21 9 7h6l7 14H2ZM9 21v-3a3 3 0 0 1 6 0v3M10 7V3h4v4M8 12h3M15 12h1")

  def forLabel(label: String): Option[ResourceIcon] = label.toLowerCase match {
    case "sugar" => Some(Sugar)
    case "protein" => Some(Protein)
    case "water" => Some(Water)
    case "resin" => Some(Resin)
    case "knowledge" => Some(Knowledge)
    case "tunneling space" => Some(Tunneling)
    case "daughter queens" => Some(Queens)
    case "royal nurses" => Some(Workers)
    case "scouts" => Some(Scouts)
    case "repletes" | "replete slots" => Some(Repletes)
    case "eggs" | "egg slots" | "brood" | "brood places" => Some(Eggs)
    case "ants" | "colony slots" => Some(Colony)
    case "small workers" | "workers" | "idle workers" | "party workers" => Some(Workers)
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
