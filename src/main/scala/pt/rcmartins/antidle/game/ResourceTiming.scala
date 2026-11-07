package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.BasicResources.BasicResource

object ResourceTiming {
  case class Estimate(toEmpty: Boolean, ticks: Long) {
    def text: String = {
      val destination = if (toEmpty) "Empty" else "Full"
      if (ticks == 0L) destination
      else s"$destination in ~${UINumbersUtils.prettyTimeFromTicks(ticks)}"
    }
  }

  // Round up: a remaining fraction of one tick must not appear as already empty/full.
  private def ticks(amount: Long, rate: BigInt): Long =
    ((BigInt(Math.max(0L, amount)) + rate - 1L) / rate).toLong

  def estimate(resource: BasicResource, netPerTick: Long): Option[Estimate] = {
    if (netPerTick < 0L) Some(Estimate(toEmpty = true, ticks(resource.amount, -BigInt(netPerTick))))
    else if (resource.atCapacity) Some(Estimate(toEmpty = false, 0L))
    else if (netPerTick > 0L && resource.isLimited && resource.maxAmount > 0L)
      Some(Estimate(toEmpty = false, ticks(resource.freeSpace, BigInt(netPerTick))))
    else None
  }
}
