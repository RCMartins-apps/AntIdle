package pt.rcmartins.antidle.main

import org.scalajs.dom
import scala.util.Try

/** Browser preferences are separate from colony saves and tolerate unavailable storage. */
object UiPreferences {
  private val prefix = "antidle.ui."
  def get(key: String): Option[String] = Try(Option(dom.window.localStorage.getItem(prefix + key))).toOption.flatten
  def set(key: String, value: String): Unit = { Try(dom.window.localStorage.setItem(prefix + key, value)); () }
  def bindDetails(element: dom.HTMLElement, key: String, defaultOpen: Boolean = false): Unit = {
    if (defaultOpen) element.setAttribute("open", "")
    get("details." + key).foreach {
      case "open" => element.setAttribute("open", "")
      case "closed" => element.removeAttribute("open")
      case _ => ()
    }
    element.addEventListener("toggle", (_: dom.Event) =>
      set("details." + key, if (element.hasAttribute("open")) "open" else "closed"))
  }
}
