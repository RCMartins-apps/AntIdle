package pt.rcmartins.antidle.game.saves

import org.scalajs.dom.window.localStorage
import pt.rcmartins.antidle.game.Utils.{actionUpdater, lastSavedTick}
import pt.rcmartins.antidle.game.Utils
import pt.rcmartins.antidle.main.MainForm.currentGlobalAlert
import pt.rcmartins.antidle.model.AllData
import zio.json._

import scala.util.Try

object SaveLoad {

  private val SlotParameter = "playtest"

  lazy val inPlaytest: Boolean =
    new org.scalajs.dom.URL(org.scalajs.dom.window.location.href).searchParams
      .get(SlotParameter) == "1"

  private def saveKey(playtest: Boolean): String =
    BrowserStorage.key(
      if (playtest) "save:playtest" else "save",
      org.scalajs.dom.window.location.pathname
    )

  private def navigateToSlot(playtest: Boolean): Unit = {
    val url = new org.scalajs.dom.URL(org.scalajs.dom.window.location.href)
    if (playtest) url.searchParams.set(SlotParameter, "1")
    else url.searchParams.delete(SlotParameter)
    org.scalajs.dom.window.location.assign(url.href)
  }

  sealed abstract class SaveError(val message: String)
  case object Missing extends SaveError("No local colony save was found.")
  case object Malformed extends SaveError("The colony save is malformed.")
  case object Incompatible extends SaveError("The colony save uses an incompatible version.")
  case object Unreadable extends SaveError("Local storage could not be read.")
  case object Unwritable extends SaveError("The colony could not be written to local storage.")

  final case class Loaded(data: AllData, recovered: Boolean)

  def saveToLocalStorage(allData: AllData): Either[SaveError, Unit] =
    saveToSlot(allData, inPlaytest)

  private def saveToSlot(allData: AllData, playtest: Boolean): Either[SaveError, Unit] =
    Try {
      val key = saveKey(playtest)
      writeSave(allData, key, s"$key:backup", localStorage.getItem(_), localStorage.setItem(_, _))
    }.toEither.left.map(_ => Unwritable)

  def openCheckpoint(raw: String, current: AllData): Either[SaveError, Unit] =
    decode(raw).flatMap { checkpoint =>
      val preserve = if (inPlaytest) Right(()) else saveToLocalStorage(current)
      preserve.flatMap(_ => saveToSlot(checkpoint, playtest = true)).map { _ =>
        if (inPlaytest) reloadDataFromLoadedSave(checkpoint, Some("Playtest checkpoint loaded."))
        else navigateToSlot(playtest = true)
      }
    }

  def returnToRegularColony(current: AllData): Either[SaveError, Unit] =
    saveToLocalStorage(current).map(_ => navigateToSlot(playtest = false))

  private[saves] def writeSave(
      allData: AllData,
      primary: String,
      backup: String,
      read: String => String,
      write: (String, String) => Unit,
  ): Unit = {
    val encoded = saveString(allData)
    Option(read(primary))
      .filter(decode(_).isRight)
      .filterNot(_ == encoded)
      .foreach(write(backup, _))
    write(primary, encoded)
  }

  def saveString(allData: AllData): String =
    java.util.Base64.getEncoder.encodeToString(AllDataForSave.fromAllData(allData).toJson.getBytes)

  private[saves] def readSave(
      primary: String,
      backup: String,
      read: String => String,
  ): Either[SaveError, Loaded] = {
    def readData(key: String) =
      Option(read(key)).toRight(Missing).flatMap(decode)

    Try {
      readData(primary) match {
        case Right(data) => Right(Loaded(data, recovered = false))
        case Left(error) => readData(backup).map(Loaded(_, recovered = true)).left.map(_ => error)
      }
    }.toEither.left.map(_ => Unreadable).flatten
  }

  def loadFromLocalStorage(): Either[SaveError, Loaded] = {
    val key = saveKey(inPlaytest)
    readSave(key, s"$key:backup", localStorage.getItem(_))
  }

  def loadString(str: String): Option[AllData] = decode(str).toOption

  def decode(str: String): Either[SaveError, AllData] = {
    val trimmed = str.trim
    val json = Try {
      if (trimmed.startsWith("{")) trimmed
      else new String(java.util.Base64.getDecoder.decode(trimmed.getBytes))
    }.toEither.left.map(_ => Malformed)

    json.flatMap { raw =>
      import zio.json.ast.Json
      raw.fromJson[Json.Obj].left.map(_ => Malformed).flatMap { obj =>
        obj.get("version") match {
          case Some(Json.Num(version))
              if version
                .compareTo(java.math.BigDecimal.valueOf(AllData.CurrentVersion.toLong)) == 0 =>
            Try(raw.fromJson[AllDataForSave].map(_.toAllData)).toEither.left
              .map(_ => Malformed)
              .flatMap(_.left.map(_ => Malformed))
          case Some(Json.Num(_)) => Left(Incompatible)
          case _                 => Left(Malformed)
        }
      }
    }
  }

  def showError(error: SaveError): Unit = currentGlobalAlert.set(Some(error.message))

  def restore(loaded: Loaded, manual: Boolean): Unit = {
    val message =
      if (loaded.recovered) Some("Recovered the last known-good colony save.")
      else Option.when(manual)("Game Loaded!")
    reloadDataFromLoadedSave(loaded.data, message)
  }

  def reloadDataFromLoadedSave(
      loadedAllData: AllData,
      messageAlert: Option[String] = Some("Game Loaded!"),
  ): Unit = {
    Utils.pause = true
    actionUpdater.writer.onNext(_ => loadedAllData)
    lastSavedTick.set(loadedAllData.world.currentTick)
    Utils.pause = false
    currentGlobalAlert.set(messageAlert)
  }

}
