package pt.rcmartins.antidle.game.saves

import org.scalajs.dom.window.localStorage
import pt.rcmartins.antidle.game.Utils.{actionUpdater, lastSavedTick}
import pt.rcmartins.antidle.game.Utils
import pt.rcmartins.antidle.main.MainForm.currentGlobalAlert
import pt.rcmartins.antidle.model.AllData
import zio.json._

import scala.util.Try

object SaveLoad {

  private lazy val SaveGameName =
    BrowserStorage.key("save", org.scalajs.dom.window.location.pathname)

  sealed abstract class SaveError(val message: String)
  case object Missing extends SaveError("No local colony save was found.")
  case object Malformed extends SaveError("The colony save is malformed.")
  case object Incompatible extends SaveError("The colony save uses an incompatible version.")
  case object Unreadable extends SaveError("Local storage could not be read.")
  case object Unwritable extends SaveError("The colony could not be written to local storage.")

  final case class Loaded(data: AllData, recovered: Boolean)

  private lazy val BackupName = s"$SaveGameName:backup"

  def saveToLocalStorage(allData: AllData): Either[SaveError, Unit] =
    Try {
      writeSave(
        allData,
        SaveGameName,
        BackupName,
        localStorage.getItem(_),
        localStorage.setItem(_, _)
      )
    }.toEither.left.map(_ => Unwritable)

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

  def loadFromLocalStorage(): Either[SaveError, Loaded] =
    readSave(SaveGameName, BackupName, localStorage.getItem(_))

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
