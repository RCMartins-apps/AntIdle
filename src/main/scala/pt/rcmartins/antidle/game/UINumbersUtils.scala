package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLSpanElement
import pt.rcmartins.antidle.game.Constants._


object UINumbersUtils {

  private val Suffixes: IndexedSeq[String] = IndexedSeq("", "K", "M", "B", "T", "Q")

  private def compact(num: Long, initialSuffix: Int = 0): String = {
    var magnitude = BigInt(num).abs
    var suffix = Math.max(0, Math.min(initialSuffix, Suffixes.size - 1))
    while (magnitude >= BigInt(1000L * u) && suffix < Suffixes.size - 1) {
      magnitude /= 1000
      suffix += 1
    }
    val whole = magnitude / u
    val fraction = magnitude % u
    val decimals = if (fraction == 0) ""
      else if (magnitude < 100L && suffix == 0) "." + f"${fraction.toInt}%04d".reverse.dropWhile(_ == '0').reverse
      else "." + f"${(fraction / 100).toInt}%02d"
    (if (num < 0L) "-" else "") + whole.toString + decimals + Suffixes(suffix)
  }

  def prettyNumberFixedSize(num: Long): ReactiveHtmlElement[HTMLSpanElement] =
    span(className := "numeric-value", compact(num))

  def preciseRateStr(amount: Long): String = {
    val magnitude = BigInt(amount).abs
    val fraction = f"${(magnitude % u).toInt}%04d".reverse.dropWhile(_ == '0').reverse
    (if (amount < 0L) "-" else "") + (magnitude / u).toString + (if (fraction.isEmpty) "" else "." + fraction)
  }

  def resourceRateStr(amount: Long): String =
    if (BigInt(amount).abs < u && amount % 100L != 0L) preciseRateStr(amount) else prettyNumberStr(amount)

  def prettyNumberStr(num: Long): String = compact(num)
  def prettyNumberStr(prefix: String, num: Long, suffix: Int): String = prefix + compact(num, suffix)

  def prettyNumberSimple(prefix: String, num: Long, suffixStr: String): ReactiveHtmlElement[HTMLSpanElement] =
    span(className := "numeric-value", prefix + compact(num) + suffixStr)
  def prettyNumberSimple(num: Long): ReactiveHtmlElement[HTMLSpanElement] =
    prettyNumberSimple("", num, "")

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
