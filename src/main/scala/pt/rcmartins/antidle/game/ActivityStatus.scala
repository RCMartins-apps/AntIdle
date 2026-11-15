package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.{ActionCost, BasicResources}

/** Shared presentation states; gameplay remains authoritative. */
object ActivityStatus {
  def kind(label: String): String = {
    val text = label.toLowerCase
    if (text.contains("needs ") || text.contains("limited care")) "blocked"
    else if (text.contains("paused")) "paused"
    else if (text.contains("full") || text.contains("queued") || text.contains(" in ")) "waiting"
    else if (text.contains("no workers") || text.contains("no royal brood")) "idle"
    else "working"
  }
  def construction(cost: ActionCost, resources: BasicResources, first: Boolean): String = {
    if (!first) "Queued"
    else {
      val missing = List("sugar" -> (resources.sugar.amount < cost.sugar),
        "protein" -> (resources.protein.amount < cost.protein), "water" -> (resources.water.amount < cost.water),
        "resin" -> (resources.resin.amount < cost.resin), "knowledge" -> (resources.knowledge.amount < cost.knowledge),
        "space" -> (resources.tunnelingSpace.amount < cost.tunnelingSpace)).collect { case (name, true) => name }
      if (missing.isEmpty) "Ready" else "Needs " + missing.mkString(" · ")
    }
  }
}
