package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.codecs.StringAsIsCodec
import com.raquo.laminar.modifiers.EventListener
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{html, HTMLDivElement, HTMLElement, HTMLSpanElement, MouseEvent}
import pt.rcmartins.antidle.main.MainForm.currentGlobalAlert

import scala.scalajs.js.timers.setTimeout

object UIUtils {

  val DataBSDismissAttr: HtmlAttr[String] = htmlAttr("data-bs-dismiss", StringAsIsCodec)
  val MinAttr: HtmlAttr[String] = htmlAttr("min", StringAsIsCodec)
  val MaxAttr: HtmlAttr[String] = htmlAttr("max", StringAsIsCodec)
  val tooltipContent: Var[ReactiveHtmlElement[HTMLElement]] = Var(div())
  val tooltipTarget: Var[ReactiveHtmlElement[HTMLElement]] = Var(div())
  val tooltipVisible: Var[Boolean] = Var(false)

  def setTooltipOpt(
      elem: ReactiveHtmlElement[HTMLDivElement],
      contentOpt: Option[ReactiveHtmlElement[HTMLDivElement]],
  ): EventListener[MouseEvent, MouseEvent] =
    onMouseEnter --> { _ =>
      contentOpt.filter(_ => org.scalajs.dom.window.innerWidth > 767).foreach { content =>
        Var.set(
          tooltipVisible -> true,
          tooltipTarget -> elem,
          tooltipContent -> content,
        )
      }
    }

  def setTooltip(
      elem: ReactiveHtmlElement[HTMLElement],
      content: ReactiveHtmlElement[HTMLElement],
  ): EventListener[MouseEvent, MouseEvent] =
    onMouseEnter --> { _ =>
      if (org.scalajs.dom.window.innerWidth > 767) Var.set(
        tooltipVisible -> true,
        tooltipTarget -> elem,
        tooltipContent -> content,
      )
    }

  def createShowGlobalAlertsDiv(): ReactiveHtmlElement[html.Div] = {
    def alertDiv(
        customClassName: String,
        content: ReactiveHtmlElement[HTMLSpanElement],
    ): ReactiveHtmlElement[HTMLDivElement] =
      div(
        className := "alert alert-dismissible fade show",
        className := "position-absolute top-0 start-50 translate-middle-x",
        className := customClassName,
        role := "alert",
        content,
        button(
          className := "btn-close",
          onClick.mapTo(None) --> currentGlobalAlert
        )
      )

    div(
      child.maybe <-- currentGlobalAlert.signal.map {
        case Some(updateText) =>
          val validTimeout: Var[Boolean] = Var(true)
          Some(
            alertDiv(
              customClassName = "alert-success",
              content = span(className := "px-3", updateText),
            ).amend(
              onMountCallback(_ =>
                setTimeout(Constants.AlertTimeout) {
                  if (validTimeout.now())
                    currentGlobalAlert.set(None)
                }
              ),
              onUnmountCallback(_ => validTimeout.set(false))
            )
          )
        case _ =>
          None
      }
    )
  }

  def getMouseEventMultiplier(event: MouseEvent): Int =
    getMouseEventMultiplier(event.ctrlKey, event.shiftKey)

  def getMouseEventMultiplier(ctrlKey: Boolean, shiftKey: Boolean): Int =
    (if (ctrlKey) 10 else 1) *
      (if (shiftKey) 25 else 1)

}
