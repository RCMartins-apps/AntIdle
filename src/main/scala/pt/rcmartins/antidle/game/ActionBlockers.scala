package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

/** Presentation only: the action's engine predicate remains authoritative. */
object ActionBlockers {
  case class Requirement(
      label: String,
      available: Long,
      required: Long = u,
      maximum: Option[Long] = None,
      isCost: Boolean = false
  )

  def missing(requirement: Requirement): Option[String] =
    Option.when(requirement.available < requirement.required) {
      requirement.label match {
        case "colony slots"       => "No free colony slots"
        case "mission slots"      => "No free mission slots"
        case "carcass slots"      => "Carcass storage full"
        case "royal brood places" => "No free royal brood places"
        case "replete slots"      => "No free replete slots"
        case "aphid places"       => "Needs aphid housing"
        case "party workers"      => "Needs a larger worker crew"
        case label                => s"Needs $label"
      }
    }

  def assignment(ants: AntsData, nest: NestAttributes, task: AntTask): Option[String] =
    if (ants.effectiveDefaultTask.contains(task)) Some("Assigned automatically")
    else if (ants.taskWorkers(task) >= DefaultWorkerAssignment.availableCapacity(ants, task, nest))
      Some("No free task places")
    else Option.when(ants.availableWorkersCount < u)(s"Needs ${ants.availableWorkersLabel}")

  def reason(
      enabled: Boolean,
      cost: ActionCost,
      resources: BasicResources,
      ants: AntsData,
      requirements: List[Requirement],
      specific: Option[String] = None
  ): Option[String] = {
    if (enabled) None
    else
      specific.orElse(requirements.iterator.flatMap(missing).take(1).toList.headOption).orElse {
        val costs = List(
          Requirement("sugar", resources.sugar.amount, cost.sugar, Some(resources.sugar.maxAmount)),
          Requirement(
            "protein",
            resources.protein.amount,
            cost.protein,
            Some(resources.protein.maxAmount)
          ),
          Requirement("water", resources.water.amount, cost.water, Some(resources.water.maxAmount)),
          Requirement("resin", resources.resin.amount, cost.resin, Some(resources.resin.maxAmount)),
          Requirement(
            "tunneling space",
            resources.tunnelingSpace.amount,
            cost.tunnelingSpace,
            Some(resources.tunnelingSpace.maxAmount)
          ),
          Requirement("knowledge", resources.knowledge.amount, cost.knowledge),
          Requirement(ants.availableWorkersLabel, ants.availableWorkersCount, cost.idleWorkers),
        )
        costs
          .find(r => r.available < r.required && r.maximum.exists(_ < r.required))
          .map(r => s"${r.label.capitalize} capacity too low")
          .orElse(costs.iterator.flatMap(missing).take(1).toList.headOption)
      }
  }

}
