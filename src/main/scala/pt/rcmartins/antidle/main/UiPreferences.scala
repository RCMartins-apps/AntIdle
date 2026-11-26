package pt.rcmartins.antidle.main

import org.scalajs.dom
import scala.util.Try
import scala.scalajs.js

/** Browser preferences are separate from colony saves and tolerate unavailable storage. */
object UiPreferences {
  private val prefix = pt.rcmartins.antidle.game.saves.BrowserStorage
    .key("antidle.ui", dom.window.location.pathname) + "."

  def get(key: String): Option[String] = Try(
    Option(dom.window.localStorage.getItem(prefix + key))
  ).toOption.flatten

  def set(key: String, value: String): Unit = {
    Try(dom.window.localStorage.setItem(prefix + key, value)); ()
  }

  def bindDetails(
      element: dom.HTMLElement,
      key: String,
      defaultOpen: Boolean = false
  ): () => Unit = {
    if (defaultOpen) element.setAttribute("open", "")
    get("details." + key).foreach {
      case "open"   => element.setAttribute("open", "")
      case "closed" => element.removeAttribute("open")
      case _        => ()
    }
    // Native toggle delivery is deferred/coalesced by the browser. Observe the
    // actual attribute instead so persistence finishes at the microtask checkpoint,
    // including while test/simulation clocks are paused.
    val observer = new dom.MutationObserver((_, _) =>
      set("details." + key, if (element.hasAttribute("open")) "open" else "closed")
    )
    observer.observe(
      element,
      new dom.MutationObserverInit {
        attributes = true
        attributeFilter = js.Array("open")
      }
    )
    () => observer.disconnect()
  }

}
