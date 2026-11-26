package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.{ActionCost, BasicResources}

/** Sequential forecast at current rates; queued chamber bonuses are not predicted. */
object ConstructionWait {
  def ticks(costs: Seq[ActionCost], resources: BasicResources, rates: ActionCost): Seq[Long] = {
    val initial = Vector(
      resources.sugar,
      resources.protein,
      resources.water,
      resources.resin,
      resources.knowledge,
      resources.tunnelingSpace
    )
    def values(c: ActionCost): Vector[Long] =
      Vector(c.sugar, c.protein, c.water, c.resin, c.knowledge, c.tunnelingSpace)
    val income = values(rates)
    var stored = initial
    var elapsed = 0L
    costs.map { cost =>
      val required = values(cost)
      val waits = required.indices.map(i => ResourceWait.ticks(required(i), stored(i), income(i)))
      if (elapsed < 0L || waits.exists(_ < 0L)) elapsed = -1L
      else {
        val wait = waits.max
        val projected = stored.indices.map { i =>
          val amount = BigInt(stored(i).amount) + BigInt(income(i)) * wait
          // Retained resources above capacity must not disappear in the forecast.
          val ceiling = Math.max(stored(i).amount, stored(i).maxAmount)
          stored(i).copy(amount = amount.max(BigInt(0)).min(BigInt(ceiling)).toLong)
        }.toVector
        if (required.indices.exists(i => projected(i).amount < required(i))) elapsed = -1L
        else {
          elapsed = (BigInt(elapsed) + wait).min(BigInt(Long.MaxValue)).toLong
          stored = projected.indices
            .map(i => projected(i).copy(amount = projected(i).amount - required(i)))
            .toVector
        }
      }
      elapsed
    }
  }

}
