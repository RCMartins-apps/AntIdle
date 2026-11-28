package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.EventBus
import org.scalajs.dom
import scala.util.Try
import scala.scalajs.js

/** Browser preferences are separate from colony saves and tolerate unavailable storage. */
object UiPreferences {
  private val prefix = pt.rcmartins.antidle.game.saves.BrowserStorage
    .key("antidle.ui", dom.window.location.pathname) + "."

  private val resetBus = new EventBus[Unit]
  val resets = resetBus.events
  private var mountedDetails = Set.empty[() => Unit]

  def reset(): Unit = {
    Try {
      val storage = dom.window.localStorage
      val keys = (0 until storage.length).flatMap(index => Option(storage.key(index)))
      keys.filter(_.startsWith(prefix)).foreach(storage.removeItem)
    }
    mountedDetails.foreach(restore => restore())
    resetBus.writer.onNext(())
  }

  def remove(key: String): Unit = {
    Try(dom.window.localStorage.removeItem(prefix + key))
    ()
  }

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
    def restoreDefault(): Unit =
      if (defaultOpen) element.setAttribute("open", "") else element.removeAttribute("open")
    restoreDefault()
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
    val restore: () => Unit = () => {
      restoreDefault()
      observer.takeRecords()
      ()
    }
    mountedDetails += restore
    () => {
      mountedDetails -= restore
      observer.disconnect()
    }
  }

}
