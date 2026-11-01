package pt.rcmartins.antidle.game.saves

import com.softwaremill.quicklens.ModifyPimp
import org.scalajs.dom.window.localStorage
import pt.rcmartins.antidle.game.Utils.{actionUpdater, lastSavedTick, unlocksOwner}
import pt.rcmartins.antidle.game.{Constants, UnlockUtils, Utils}
import pt.rcmartins.antidle.main.MainForm.currentGlobalAlert
import pt.rcmartins.antidle.model.AllData
import zio.json._

import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps

// TODO check for save/load/import/export errors?
object SaveLoad {

  private val SaveGameName = "save"

  def saveToLocalStorage(allData: AllData): Either[Throwable, Unit] =
    Try {
      localStorage.setItem(SaveGameName, saveString(allData))
    }.toEither

  def saveString(allData: AllData): String =
    jsonToBase64(saveToJson(allData))

  private def saveToJson(allData: AllData): String =
    AllDataForSave.fromAllData(allData).toJson

  private def jsonToBase64(str: String): String =
    java.util.Base64.getEncoder.encodeToString(str.getBytes)

  def loadFromLocalStorage(): Option[AllData] =
    Option(localStorage.getItem(SaveGameName))
      .flatMap(loadString)
      .filter(_.version == AllData.CurrentVersion)

  def loadString(str: String): Option[AllData] = {
    val strTrimmed = str.trim
    if (strTrimmed.startsWith("{"))
      loadFromJson(strTrimmed)
    else
      loadFromJson(base64ToJson(strTrimmed))
  }

  private def loadFromJson(str: String): Option[AllData] =
    str.fromJson[AllDataForSave].toOption.map(_.toAllData).map(migrateSave)

  private def migrateSave(allData: AllData): AllData =
    if (allData.version == 1)
      allData
        .modify(_.basicResources.colonyPoints.maxAmount)
        .using(_ + allData.nestAttributes.chambers.nestChamber.level.toLong *
          Constants.NestUpgradeBonusMaxColonyPoints)
        .modify(_.version)
        .setTo(2)
    else
      allData

  private def base64ToJson(str: String): String =
    new String(java.util.Base64.getDecoder.decode(str.getBytes))

  def reloadDataFromLoadedSave(
      loadedAllData: AllData,
      messageAlert: Option[String] = Some("Game Loaded!"),
  ): Unit = {
    Utils.pause = true
    unlocksOwner.killAll()
    actionUpdater.writer.onNext(_ => loadedAllData)
    actionUpdater.writer.onNext(_.tap(UnlockUtils.checkUnlocks(_)(unlocksOwner)))
    lastSavedTick.set(loadedAllData.world.currentTick)
    Utils.pause = false
    currentGlobalAlert.set(messageAlert)
  }

}
