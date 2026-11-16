package pt.rcmartins.antidle.game

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.model._

class ActionBlockersSuite extends FunSuite {
  private val initial = AllData.initial
  private def reason(cost: ActionCost = ActionCost.empty, requirements: List[ActionBlockers.Requirement] = Nil,
      enabled: Boolean = false, specific: Option[String] = None) =
    ActionBlockers.reason(enabled, cost, initial.basicResources, initial.ants, requirements, specific)

  test("queued and enabled actions never report missing resources") {
    assertEquals(reason(ActionCost(sugar = 9999 * u), enabled = true, specific = Some("Needs sugar")), None)
  }
  test("egg orders explain reserved housing before their future sugar cost") {
    assertEquals(reason(ActionCost(sugar = 10 * u), List(ActionBlockers.Requirement("colony slots", 0L))), Some("No free colony slots"))
  }
  test("unreachable storage prices differ from ordinary shortages and unlimited knowledge") {
    assertEquals(reason(ActionCost(water = initial.basicResources.water.maxAmount + u)), Some("Water capacity too low"))
    assertEquals(reason(ActionCost(protein = u)), Some("Needs protein"))
    assertEquals(reason(ActionCost(knowledge = u)), Some("Needs knowledge"))
  }
  test("retained water above a reduced capacity is still spendable") {
    val resources = initial.basicResources.copy(water = pt.rcmartins.antidle.model.BasicResources.BasicResource(200 * u, 100 * u))
    assertEquals(ActionBlockers.reason(false, ActionCost(water = 150 * u, idleWorkers = u), resources,
      initial.ants, Nil), Some("Needs idle workers"))
  }
  test("queue and busy-state explanations override affordability") {
    assertEquals(reason(ActionCost(sugar = u), specific = Some("Build queue full")), Some("Build queue full"))
    assertEquals(reason(requirements = List(ActionBlockers.Requirement("scouts", 0L)), specific = Some("Study in progress")), Some("Study in progress"))
  }
  test("resource and crew blockers clear when their displayed requirements are met") {
    val req = ActionBlockers.Requirement("scouts", u, u)
    assertEquals(reason(requirements = List(req)), None)
    assertEquals(reason(requirements = List(req.copy(available = 0L))), Some("Needs scouts"))
    assertEquals(reason(ActionCost(idleWorkers = u)), Some("Needs idle workers"))
    val pool = initial.ants.copy(workers = u, smallWorkers = 0L,
      tasks = List(AntTask.SugarCollector -> u), defaultTask = Some(AntTask.SugarCollector))
    assertEquals(ActionBlockers.reason(false, ActionCost(idleWorkers = 2 * u), initial.basicResources,
      pool, Nil), Some("Needs Sugar Collector workers"))
  }
  test("default task assignment is automatic and limited jobs identify full capacity") {
    val ants = initial.ants.copy(workers = 10 * u, smallWorkers = 0L, defaultTask = Some(AntTask.SugarCollector), tasks = List(AntTask.SugarCollector -> 0L))
    assertEquals(ActionBlockers.assignment(ants, initial.nestAttributes, AntTask.SugarCollector), Some("Assigned automatically"))
    val full = ants.copy(tasks = List(AntTask.SugarCollector -> 0L, AntTask.Scavenger -> (2 * u)))
    assertEquals(ActionBlockers.assignment(full, initial.nestAttributes, AntTask.Scavenger), Some("No free task places"))
  }
}
