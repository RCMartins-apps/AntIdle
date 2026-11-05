package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.BasicResources.BasicResource

object ResourceWait {
  def ticks(required: Long, resource: BasicResource, income: Long): Long = {
    val remaining = required - resource.amount
    if (remaining <= 0L) 0L
    else if (income <= 0L || (resource.isLimited && required > resource.maxAmount)) -1L
    else 1L + (remaining - 1L) / income
  }
}
