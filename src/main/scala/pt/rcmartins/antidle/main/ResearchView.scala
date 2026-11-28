package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.UINumbersUtils._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.{ActionBonus, ActionCost}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

private[main] object ResearchView {
  import MainForm.{actionButton, detailsTag, infoDetails, rememberDetails, summaryTag}

  def render(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def createUpgrade(upgradeType: UpgradeType): ReactiveHtmlElement[HTMLDivElement] = {
      val costSignal: Signal[ActionCost] = Val(upgradeType.cost)
      val wait = resourcesSignal
        .combineWith(TickCalculator.constructionResourceRates)
        .map { case (resources, rates) =>
          ConstructionWait.ticks(Seq(upgradeType.cost), resources, rates).head
        }
        .distinct
      actionButton(
        name = upgradeType.title,
        enabledSignal = canPurchaseUpgradeSignal(upgradeType),
        descriptionSignal = Val(Some(div(upgradeType.description))),
        costSignal = costSignal,
        bonusSignal = Val(ActionBonus.empty),
        onClickAction = _ => Actions.unlockUpgrade(upgradeType),
      ).amend(
        child.maybe <-- wait.map(ticks =>
          Option.when(ticks > 0L)(
            span(
              className := "small text-body-secondary text-center upgrade-estimate",
              s"Resources in ~${prettyTimeFromTicks(ticks)}"
            )
          )
        )
      )
    }

    div(
      className := "row upgrades-grid",
      div(
        className := "col-12 milestone-panel",
        children <-- snapshotSignal
          .map { data =>
            MilestoneResearch
              .next(data)
              .map(goal => (goal.upgrade, goal.progress(data), goal.target, goal.label))
          }
          .distinct
          .map {
            case None => Seq.empty
            case Some((upgrade, progress, target, label)) =>
              Seq(
                div(
                  className := "milestone-goal border rounded p-2 mb-2",
                  b("Next research goal: ", upgrade.title),
                  div(className := "milestone-progress small", s"$progress/$target $label"),
                  div(
                    className := "progress",
                    height.px := 6,
                    role := "progressbar",
                    aria.label := upgrade.title,
                    aria.valueMin := 0,
                    aria.valueMax := target,
                    aria.valueNow := progress,
                    div(className := "progress-bar", width.percent := progress * 100 / target)
                  ),
                  div(className := "small mt-1", upgrade.description),
                  div(
                    className := "small text-body-secondary",
                    s"Research: ${prettyNumberStr(upgrade.cost.sugar)} sugar · ${prettyNumberStr(upgrade.cost.knowledge)} knowledge" +
                      (if (upgrade.cost.protein > 0L)
                         s" · ${prettyNumberStr(upgrade.cost.protein)} protein"
                       else "")
                  )
                )
              )
          }
      ),
      children <-- upgradesSignal.map { upgrades =>
        val available = UpgradeType.all.filter(t => upgrades(t).show && !upgrades(t).unlocked)
        if (available.isEmpty)
          Seq(
            div(
              className := "col-12 py-3 upgrades-empty text-body-secondary text-center",
              if (UpgradeType.all.forall(t => upgrades(t).unlocked))
                p(className := "mb-0", "All current upgrades researched.")
              else p(className := "mb-0", "No upgrades available.")
            )
          )
        else available.map(t => createUpgrade(t))
      },
      detailsTag(
        className := "col-12 purchased-upgrades border-top mt-3 pt-2",
        rememberDetails("upgrades.purchased"),
        summaryTag(
          minHeight.px := 44,
          child.text <-- upgradesSignal
            .map(upgrades => s"Purchased upgrades (${UpgradeType.all.count(upgrades(_).unlocked)})")
            .distinct
        ),
        children <-- upgradesSignal
          .map(upgrades => UpgradeType.all.filter(upgrades(_).unlocked))
          .distinct
          .map { purchased =>
            if (purchased.isEmpty)
              Seq(p(className := "small text-body-secondary", "No upgrades purchased yet."))
            else
              purchased.map(upgrade =>
                div(
                  className := "purchased-upgrade d-flex align-items-center justify-content-between gap-2 border-bottom py-1",
                  span(upgrade.title),
                  infoDetails(upgrade.title, div(upgrade.description))
                )
              )
          },
      ),
    )
  }

}
