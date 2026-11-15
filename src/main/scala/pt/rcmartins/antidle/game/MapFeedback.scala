package pt.rcmartins.antidle.game
import pt.rcmartins.antidle.model._
object MapFeedback {
  case class State(kind: String, label: String, percent: Option[Int] = None)
  def state(data: ExplorationData, id: String, tick: Long): State = {
    val routes = if (id == "surroundings") Nil else ExplorationMap.site(id).routes
    val parties = data.explorationParties.filter(p =>
      (id == "surroundings" && p.mission == "reveal") ||
        (id != "surroundings" && (p.destination == id || routes.contains(p.destination))))
    if (id != "surroundings" && !ExplorationMap.revealed(data, id)) State("unknown", "Unexplored")
    else parties.sortBy(_.targetTick).headOption match {
      case Some(p) =>
        val percent = (BigInt(Math.max(0L, tick - p.startingTick)) * 100L / Math.max(1L, p.targetTick - p.startingTick)).min(BigInt(100)).toInt
        val (kind, title) = p.mission match {
          case "reveal" => "scouting" -> "Exploring"
          case "study" => "studying" -> "Studying"
          case "aphids" => "collecting" -> "Relocating"
          case _ => "collecting" -> "Collecting"
        }
        State(kind, s"$title $percent%" + (if (parties.size > 1) s" +${parties.size - 1}" else ""), Some(percent))
      case None if id == "surroundings" => State("nest", "Nest")
      case None if ExplorationMap.exhausted(data, id) => State("exhausted", "✓ Exhausted")
      case None if data.collectionTrails.exists(t => routes.contains(t.destination) && t.workers > 0L) => State("trail", "Trail assigned")
      case None if ExplorationMap.knowledgeRemaining(data, id) > 0L => State("study", "Study")
      case None if id == "woodland-edge" && ExplorationMap.remaining(data, id) == 0L => State("aphids", "Aphids")
      case _ => State("collect", "Collect")
    }
  }
}
