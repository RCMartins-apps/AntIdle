package pt.rcmartins.antidle.game
import munit.FunSuite
import pt.rcmartins.antidle.model._
import pt.rcmartins.antidle.game.saves.AllDataForSave
import zio.json._
class EventLogSuite extends FunSuite {
  test("routine floods do not erase significant events and retention stays bounded") {
    val start = AllData.initial.copy(messages = Nil).addMessage("Discovery: Wild Aphids!").addMessage("An ant died of starvation.")
    val flooded = (1 to 100).foldLeft(start)((d, n) => d.addMessage(s"Nectar Meadow: collected $n sugar."))
    assert(flooded.messages.contains("Discovery: Wild Aphids!"))
    assert(flooded.messages.contains("An ant died of starvation."))
    assertEquals(flooded.messages.count(EventLog.kind(_) == "routine"), 30)
    val entries = EventLog.routine(flooded.messages)
    assertEquals(entries.size, 1)
    assertEquals(entries.head.reports, 30)
    assertEquals(entries.head.text, "Nectar Meadow: collected 100 sugar.")
    val loaded = AllDataForSave.fromAllData(flooded).toJson.fromJson[AllDataForSave].toOption.get.toAllData
    assertEquals(loaded.messages, flooded.messages)
  }
  test("collections group by source and resource without conflating Knowledge or losses") {
    val messages = Seq("Meadow: collected 10 sugar.", "Meadow: collected 2 protein.", "Meadow: +10 knowledge.", "Aging: 3 workers lost in the last minute.")
    assertEquals(EventLog.routine(messages).size, 3)
    assertEquals(EventLog.kind(messages.last), "loss")
    assertEquals(EventLog.kind("Research completed: Brood Care."), "important")
    assertEquals(EventLog.kind("Legacy message"), "notice")
  }
}
