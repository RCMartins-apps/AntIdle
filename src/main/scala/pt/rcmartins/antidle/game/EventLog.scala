package pt.rcmartins.antidle.game

/** Compatible with existing string-based saves; retain significant events separately. */
object EventLog {
  case class Entry(text: String, reports: Int, kind: String)
  def kind(text: String): String = {
    val lower = text.toLowerCase
    if (
      lower.startsWith("aging:") || lower.contains("starvation") || lower.contains(
        "casualties"
      ) || lower.contains("combat losses") || lower.contains("abandoned")
    ) "loss"
    else if (
      lower.startsWith("discovery:") || lower.startsWith("scouts revealed") || lower.startsWith(
        "research completed:"
      ) || lower.contains("queen has matured") || lower.contains("outpost established") || lower
        .contains("outpost expanded")
    ) "important"
    else if (
      lower.contains(": collected ") || lower
        .endsWith(" knowledge.") || lower.startsWith("carcass returned:")
    ) "routine"
    else "notice"
  }

  def append(messages: Seq[String], text: String): Seq[String] = {
    var significant = 0
    var routine = 0
    var notices = 0
    (text +: messages).filter { message =>
      kind(message) match {
        case "loss" | "important" => significant += 1; significant <= 20
        case "routine"            => routine += 1; routine <= 30
        case _                    => notices += 1; notices <= 10
      }
    }
  }

  def routine(messages: Seq[String]): Seq[Entry] = {
    val reports = messages.filter(kind(_) == "routine")
    def key(text: String): String = text.takeWhile(_ != ':') + ":" +
      (if (text.contains(": collected ")) text.split(' ').lastOption.getOrElse("")
       else if (text.endsWith(" knowledge.")) "knowledge"
       else "carcass")
    reports.map(key).distinct.map { id =>
      val group = reports.filter(key(_) == id)
      Entry(group.head, group.size, "routine")
    }
  }

}
