package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom._
import pt.rcmartins.antidle.game.UIUtils.DataBSDismissAttr
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.{AllData, WorldData}

object Modals {

  def importModal()(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val dataVar: Var[String] = Var("")
    val fileSource = Var(Option.empty[(String, String)])
    val reading = Var(false)
    val fileError = Var(Option.empty[String])
    def readFile(file: File): Unit = {
      val reader = new FileReader()
      reading.set(true)
      fileError.set(None)
      fileSource.set(None)
      dataVar.set("")
      reader.onload = _ => {
        fileSource.set(Some(file.name -> reader.result.asInstanceOf[String]))
        reading.set(false)
      }
      reader.onerror = _ => {
        fileError.set(Some("The backup file could not be read."))
        reading.set(false)
      }
      reader.readAsText(file)
    }
    div(
      className := "modal fade",
      idAttr := Constants.ImportModalId,
      tabIndex(-1),
      div(
        className("modal-dialog modal-lg"),
        div(
          className := "modal-content",
          div(
            className := "modal-header",
            h5(className := "modal-title", "Import"),
            button(
              typ("button"),
              className("btn-close"),
              aria.label := "Close import",
              DataBSDismissAttr("modal"),
              minHeight.px := 44,
              minWidth.px := 44,
            ),
          ),
          div(
            className := "modal-body",
            label(forId := "colony-backup-file", className := "form-label", "Import file"),
            input(
              idAttr := "colony-backup-file",
              typ := "file",
              className := "form-control mb-2",
              disabled <-- reading.signal,
              inContext { element =>
                onChange --> { _ =>
                  val files = element.ref.files
                  if (files != null && files.length > 0) readFile(files(0))
                  element.ref.value = ""
                }
              }
            ),
            child.maybe <-- fileError.signal.map(
              _.map(message => p(className := "text-danger", role := "alert", message))
            ),
            child.maybe <-- fileSource.signal.map(
              _.map { case (name, _) =>
                p(
                  className := "small text-body-secondary text-break",
                  role := "status",
                  s"Selected: $name"
                )
              }
            ),
            p("Or paste a colony save or simulator checkpoint."),
            textArea(
              className := "form-control",
              placeholder := "Save data",
              rows := 10,
              cols := 50,
              value <-- dataVar.signal,
              disabled <-- reading.signal,
            ).amendThis { elem =>
              onInput --> { _ =>
                fileSource.set(None)
                fileError.set(None)
                dataVar.set(elem.ref.value)
              }
            }
          ),
          div(
            className := "modal-footer",
            button(
              typ := "button",
              className := "btn btn-secondary",
              DataBSDismissAttr("modal"),
              minHeight.px := 44,
              disabled <-- reading.signal,
              onClick --> { _ =>
                val raw = fileSource.now().fold(dataVar.now())(_._2)
                SaveLoad.withWriteLock {
                  SaveLoad
                    .decode(raw)
                    .flatMap(data => SaveLoad.saveToLocalStorage(data).map(_ => data))
                    .fold(SaveLoad.showError, data => SaveLoad.reloadDataFromLoadedSave(data))
                }
                fileSource.set(None)
                dataVar.set("")
              },
              "Import",
            ),
            button(
              typ := "button",
              className := "btn btn-primary",
              DataBSDismissAttr("modal"),
              minHeight.px := 44,
              disabled <-- reading.signal,
              onClick --> { _ =>
                val raw = fileSource.now().fold(dataVar.now())(_._2)
                Utils.useSignalValue[AllData](
                  Utils.snapshotSignal,
                  current =>
                    SaveLoad.withWriteLock {
                      SaveLoad.openCheckpoint(raw, current).left.foreach(SaveLoad.showError)
                    }
                )
                fileSource.set(None)
                dataVar.set("")
              },
              "Open in playtest slot",
            )
          )
        )
      )
    )
  }

  def newGameModal(): ReactiveHtmlElement[HTMLDivElement] = {
    val dataVar: Var[String] = Var("")
    div(
      className := "modal fade",
      idAttr := Constants.NewGameModalId,
      tabIndex(-1),
      div(
        className("modal-dialog modal-lg"),
        div(
          className := "modal-content",
          div(
            className := "modal-header",
            h5(className := "modal-title", "Hard Reset"),
            button(
              typ("button"),
              className("btn-close"),
              DataBSDismissAttr("modal"),
            ),
          ),
          div(
            className := "modal-body",
            p(b("Resets EVERYTHING and starts a new game.")),
            p(b("This is not a prestige reset!")),
            p("Are you sure you want to continue?"),
          ),
          div(
            className := "modal-footer",
            button(
              typ := "button",
              className := "btn btn-danger",
              DataBSDismissAttr("modal"),
              onClick --> { _ =>
                val newAllData: AllData =
                  AllData.initial.copy(world = WorldData.initial(System.currentTimeMillis()))
                SaveLoad.withWriteLock {
                  SaveLoad
                    .resetLocalStorage(newAllData)
                    .fold(
                      SaveLoad.showError,
                      _ => SaveLoad.reloadDataFromLoadedSave(newAllData, Some("New Game Started!"))
                    )
                }
                dataVar.set("")
              },
              "Confirm",
            )
          )
        )
      )
    )
  }

}
