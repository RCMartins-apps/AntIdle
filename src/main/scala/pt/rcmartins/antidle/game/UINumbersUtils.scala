package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement
import pt.rcmartins.antidle.game.Constants._

import scala.annotation.tailrec

object UINumbersUtils {

  private val Suffixes: IndexedSeq[String] = IndexedSeq("", "K", "M", "B", "T", "Q")

  def prettyNumberFixedSize(num: Long): ReactiveHtmlElement[HTMLSpanElement] =
    prettyNumberFixedSize("", num, 0)

  @tailrec
  private def prettyNumberFixedSize(
      prefix: String,
      num: Long,
      suffixNum: Int,
  ): ReactiveHtmlElement[HTMLSpanElement] =
    if (num <= 9999 * u)
      if (num % u == 0) {
        if (suffixNum == 0)
          span(prefix, (num / u).toString, span(opacity := 0, ".00K"))
        else
          span(prefix, (num / u).toString, span(opacity := 0, ".00"), Suffixes(suffixNum))
      } else {
        val shortValue = num / 100
        span(
          prefix,
          (shortValue / 100).toString,
          ".",
          f"${Math.abs(shortValue) % 100}%02d",
          if (suffixNum == 0) span(opacity := 0, "K") else Suffixes(suffixNum),
        )
      }
    else
      prettyNumberFixedSize(prefix, num / 1000, suffixNum + 1)

  def preciseRateStr(amount: Long): String = {
    val fraction = f"${Math.abs(amount % u)}%04d".reverse.dropWhile(_ == '0').reverse
    if (fraction.isEmpty) (amount / u).toString else s"${amount / u}.$fraction"
  }

  def prettyNumberStr(num: Long): String =
    prettyNumberStr("", num, 0)

  @tailrec
  def prettyNumberStr(
      prefix: String,
      num: Long,
      suffix: Int,
  ): String =
    if (num <= 9999 * u)
      if (num % u == 0)
        prefix + (num / u).toString + Suffixes(suffix)
      else {
        val shortValue = num / 100
        prefix +
          (shortValue / 100).toString +
          "." +
          f"${Math.abs(shortValue) % 100}%02d" +
          Suffixes(suffix)
      }
    else
      prettyNumberStr(prefix, num / 1000, suffix + 1)

  def prettyNumberSimple(
      prefix: String,
      num: Long,
      suffixStr: String,
  ): ReactiveHtmlElement[HTMLSpanElement] =
    prettyNumberSimple(prefix, num, 0, suffixStr)

  def prettyNumberSimple(num: Long): ReactiveHtmlElement[HTMLSpanElement] =
    prettyNumberSimple("", num, 0, "")

  @tailrec
  private def prettyNumberSimple(
      prefix: String,
      num: Long,
      suffixNum: Int,
      suffixStr: String,
  ): ReactiveHtmlElement[HTMLSpanElement] =
    if (num <= 9999 * u)
      if (num % u == 0)
        span(prefix, (num / u).toString, Suffixes(suffixNum), suffixStr)
      else {
        val shortValue = num / 100
        span(
          prefix,
          (shortValue / 100).toString,
          ".",
          f"${Math.abs(shortValue) % 100}%02d",
          Suffixes(suffixNum),
          suffixStr,
        )
      }
    else
      prettyNumberSimple(prefix, num / 1000, suffixNum + 1, suffixStr)

  def prettyNumberInt(num: Long): ReactiveHtmlElement[HTMLSpanElement] =
    span((num / u).toString)

  def prettyTimeFromTicks(ticks: Long): String = {
    val dSeconds = (ticks % TicksPerSecond) * 2
    val seconds = ticks / TicksPerSecond
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    if (years != 0)
      if (months > 0) s"${years}y ${months % 12}m" else s"${years}y"
    else if (months != 0)
      if (weeks > 0) s"${months}m ${weeks % 4}w" else s"${months}m"
    else if (weeks != 0)
      if (days > 0) s"${weeks}w ${days % 7}d" else s"${weeks}w"
    else if (days != 0)
      if (hours > 0) s"${days}d ${hours % 24}h" else s"${days}d"
    else if (hours != 0)
      if (minutes > 0) s"${hours}h ${minutes % 60}m" else s"${hours}h"
    else if (minutes != 0)
      if (seconds > 0) s"${minutes}m ${seconds % 60}s" else s"${minutes}m"
    else
      s"$seconds.${dSeconds}s"
  }

  def plural(seconds: Long): String = if (seconds != 1) "s" else ""

}
