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
              DataBSDismissAttr("modal"),
            ),
          ),
          div(
            className := "modal-body",
            p("Paste a colony save or simulator checkpoint."),
            textArea(
              className := "form-control",
              placeholder := "Save data",
              rows := 10,
              cols := 50,
              value <-- dataVar.signal,
            ).amendThis { elem =>
              onInput.mapTo(elem.ref.value) --> dataVar.writer
            }
          ),
          div(
            className := "modal-footer",
            button(
              typ := "button",
              className := "btn btn-secondary",
              DataBSDismissAttr("modal"),
              onClick --> { _ =>
                SaveLoad
                  .decode(dataVar.now())
                  .flatMap(data => SaveLoad.saveToLocalStorage(data).map(_ => data))
                  .fold(SaveLoad.showError, data => SaveLoad.reloadDataFromLoadedSave(data))
                dataVar.set("")
              },
              "Import",
            ),
            button(
              typ := "button",
              className := "btn btn-primary",
              DataBSDismissAttr("modal"),
              onClick --> { _ =>
                val raw = dataVar.now()
                Utils.useSignalValue[AllData](
                  Utils.snapshotSignal,
                  current => SaveLoad.openCheckpoint(raw, current).left.foreach(SaveLoad.showError)
                )
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
                SaveLoad
                  .saveToLocalStorage(newAllData)
                  .fold(
                    SaveLoad.showError,
                    _ => SaveLoad.reloadDataFromLoadedSave(newAllData, Some("New Game Started!"))
                  )
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
