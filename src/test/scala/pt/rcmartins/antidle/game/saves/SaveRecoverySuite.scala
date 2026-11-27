package pt.rcmartins.antidle.game.saves

import munit.FunSuite
import pt.rcmartins.antidle.model.AllData
import zio.json._

class SaveRecoverySuite extends FunSuite {
  private val initial = AllData.initial
  private val later = initial.copy(world = initial.world.copy(currentTick = 100L))

  test("decode distinguishes invalid Base64, malformed JSON and incompatible versions") {
    assertEquals(SaveLoad.decode("%%%"), Left(SaveLoad.Malformed))
    assertEquals(SaveLoad.decode("""{"version":"""), Left(SaveLoad.Malformed))
    val old = AllDataForSave.fromAllData(initial).copy(version = AllData.CurrentVersion - 1)
    assertEquals(SaveLoad.decode(old.toJson), Left(SaveLoad.Incompatible))
    assert(SaveLoad.decode(SaveLoad.saveString(initial)).isRight)
  }

  test("recover from malformed primary without overwriting the backup") {
    val data = scala.collection.mutable.Map.empty[String, String]
    def read(key: String): String = data.getOrElse(key, null)
    def write(key: String, value: String): Unit = data.update(key, value)

    assertEquals(SaveLoad.readSave("save", "backup", read), Left(SaveLoad.Missing))
    SaveLoad.writeSave(initial, "save", "backup", read, write)
    SaveLoad.writeSave(later, "save", "backup", read, write)
    val previous = data("backup")
    data("save") = "%%%"

    val recovered = SaveLoad.readSave("save", "backup", read).toOption.get
    assert(recovered.recovered)
    assertEquals(recovered.data.world.currentTick, initial.world.currentTick)
    SaveLoad.writeSave(later, "save", "backup", read, write)
    assertEquals(data("backup"), previous)
    assert(!SaveLoad.readSave("save", "backup", read).toOption.get.recovered)
  }

  test("a failed replacement leaves the previously stored colony readable") {
    val original = SaveLoad.saveString(initial)
    val data = scala.collection.mutable.Map("save" -> original)
    def read(key: String): String = data.getOrElse(key, null)
    def write(key: String, value: String): Unit =
      if (key == "save") throw new IllegalStateException("Storage write failed")
      else data.update(key, value)

    intercept[IllegalStateException] {
      SaveLoad.writeSave(later, "save", "backup", read, write)
    }
    assertEquals(data("save"), original)
    assertEquals(data("backup"), original)
    assertEquals(
      SaveLoad.readSave("save", "backup", read).toOption.get.data.world.currentTick,
      initial.world.currentTick
    )
  }

}
