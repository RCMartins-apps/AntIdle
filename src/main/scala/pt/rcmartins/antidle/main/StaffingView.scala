package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement
import pt.rcmartins.antidle.game.{PheromoneTrails, ResourceIcons, ScoutProgression}
import pt.rcmartins.antidle.game.UINumbersUtils.prettyNumberStr
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.AntTask

private[main] object StaffingView {
  import MainForm.{detailsTag, rememberDetails, summaryTag}

  private def breakdown(rows: Signal[List[(String, Long)]]) =
    dl(
      className := "staffing-breakdown small mb-2",
      children <-- rows.distinct.map(_.collect {
        case (roleName, count) if count > 0L =>
          div(
            className := "d-flex justify-content-between gap-2",
            dt(roleName),
            dd(className := "mb-0", prettyNumberStr(count))
          )
      })
    )

  private val dedicated = Set[AntTask](
    AntTask.Explorer,
    AntTask.CarcassCarrier,
    AntTask.TrailForager,
    AntTask.TerritoryForager
  )

  def workers = breakdown(antsSignal.map { ants =>
    val default = ants.effectiveDefaultTask
    val assigned = ants.tasks.collect {
      case (task, count) if !default.contains(task) && !dedicated(task) => count
    }.sum
    List(
      "Idle" -> ants.idleWorkersCount,
      "Default job" -> default.fold(0L)(ants.taskWorkers),
      "Other nest jobs" -> assigned,
      "Away on expeditions" -> ants.taskWorkers(AntTask.Explorer),
      "Retrieving carcasses" -> ants.taskWorkers(AntTask.CarcassCarrier),
      "Reserved for trails" -> PheromoneTrails.workers(ants),
      "Holding territory" -> ants.taskWorkers(AntTask.TerritoryForager)
    )
  })

  private val scoutBreakdown =
    antsSignal.combineWith(explorationSignal).map { case (ants, exploration) =>
      val home = ScoutProgression.atHome(ants, exploration)
      val free = ScoutProgression.available(ants, exploration)
      List(
        "Free" -> free,
        "Away on expeditions" -> (ants.scouts - home),
        "Guiding trails" -> (home - free)
      )
    }

  def otherCastes: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "d-grid gap-2",
      child.maybe <-- ifUnlockedOpt(_.resources.showScouts) {
        detailsTag(
          className := "scout-staffing border rounded p-2",
          rememberDetails("tasks.scouts"),
          summaryTag(
            className := "standard-workers-summary",
            ResourceIcons.icon(ResourceIcons.Scouts),
            "Scouts · ",
            child.text <-- antsSignal.map(a => prettyNumberStr(a.scouts)).distinct,
            span(
              className := "idle-worker-summary",
              "Free ",
              b(child.text <-- scoutBreakdown.map(rows => prettyNumberStr(rows.head._2)).distinct)
            )
          ),
          breakdown(scoutBreakdown)
        )
      },
      child.maybe <-- ifUnlockedOpt(_.resources.showRepletes) {
        div(
          className := "border rounded p-2",
          ResourceIcons.icon(ResourceIcons.Repletes),
          "Repletes · ",
          b(child.text <-- antsSignal.map(a => prettyNumberStr(a.repletes)).distinct)
        )
      },
      child.maybe <-- ifUnlockedOpt(_.resources.showDaughterQueens) {
        div(
          className := "border rounded p-2",
          ResourceIcons.icon(ResourceIcons.Queens),
          "Daughter queens · ",
          b(child.text <-- antsSignal.map(a => prettyNumberStr(a.daughterQueens)).distinct)
        )
      }
    )

}
