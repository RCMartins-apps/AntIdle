package pt.rcmartins.antidle.main

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.modifiers.EventListener
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.querki.jquery
import org.scalajs.dom
import org.scalajs.dom.{svg => _, _}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ActionBlockers.{Requirement => ActionRequirement}
import pt.rcmartins.antidle.game.TickCalculator.prettyResourceEstDiv
import pt.rcmartins.antidle.game.UINumbersUtils._
import pt.rcmartins.antidle.game.UIUtils._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model._

import scala.scalajs.concurrent.JSExecutionContext.queue
import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps

object MainForm {
  private[main] val detailsTag = htmlTag("details")
  private[main] val summaryTag = htmlTag("summary")

  private[main] def rememberDetails(
      key: String,
      defaultOpen: Boolean = false
  ): Modifier[ReactiveHtmlElement[HTMLElement]] = {
    var disconnect: () => Unit = () => ()
    onMountUnmountCallback(
      ctx => disconnect = UiPreferences.bindDetails(ctx.thisNode.ref, key, defaultOpen),
      _ => disconnect(),
    )
  }

  private[main] def workerCasteIcon: ReactiveHtmlElement[HTMLSpanElement] =
    span(
      child <-- antsSignal
        .map(_.standardWorkersUnlocked)
        .distinct
        .map(standard => ResourceIcons.icon(ResourceIcons.forWorker(standard)))
    )

  private[main] def activityStatus(
      label: Signal[String],
      extraClass: String = ""
  ): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := s"activity-status $extraClass",
      dataAttr("state") <-- label.map(ActivityStatus.kind).distinct,
      child.text <-- label.distinct
    )

  private[main] def infoDetails(
      label: String,
      content: ReactiveHtmlElement[HTMLDivElement]
  ): ReactiveHtmlElement[HTMLElement] =
    detailsTag(
      className := "info-details",
      rememberDetails("info." + label),
      summaryTag(
        className := "info-summary",
        aria.label := s"About $label",
        title := s"About $label",
        svg.svg(
          aria.hidden := true,
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 11v6M12 7h.01")
        ),
      ),
      div(className := "info-content", content),
    )

  private[main] def infoHeading(
      label: String,
      content: ReactiveHtmlElement[HTMLDivElement],
      minor: Boolean = false
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "info-heading",
      if (minor) h6(label) else h5(label),
      infoDetails(label, content)
    )

  val currentGlobalAlert: Var[Option[String]] = Var(None)

  private val tooltipX: Var[Int] = Var(0)
  private val tooltipY: Var[Int] = Var(0)

  private val tooltip: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "card p-1 game-tooltip",
      position.absolute,
      top.px <-- tooltipY.signal.distinct.map(_ - 15),
      left.px <-- tooltipX.signal.distinct.map(_ + 50),
      display <-- tooltipVisible.signal.distinct.map(if (_) "block" else "none").distinct,
      child <-- tooltipContent.signal,
    )

  private val ctrlPressedVar = Var(false)
  private val shiftPressedVar = Var(false)

  private val windowWidthVar: Var[Long] = Var(window.innerWidth.toLong)
  private[main] val broodMultiplier = Var(1)
  private val tasksMultiplier = Var(1)

  private[main] val modifiersMultiplierSignal: Signal[Int] =
    ctrlPressedVar.signal
      .combineWith(shiftPressedVar.signal, broodMultiplier.signal, windowWidthVar.signal)
      .map { case (ctrl, shift, mobile, width) =>
        if (width <= 767) mobile else getMouseEventMultiplier(ctrl, shift)
      }

  private val lineageVisibleSignal = antsSignal
    .combineWith(nestSignal)
    .map { case (ants, nest) =>
      nest.royalBroodSlots > 0 || ants.daughterQueens > 0L || ants.lineage.foundings > 0
    }
    .distinct

  // Flat-top regular hexagons: adjacent columns step by 3/4 width and half height.
  private val ResourcesWidth = 500
  private val NestWidth = 800
  private val MessagesWidth = 575

  private val windowWidthLimit: Long =
    ResourcesWidth + NestWidth + MessagesWidth

  private val chambersVisible = unlocksSignal
    .combineWith(nurseryUnlockedSignal)
    .map { case (unlocks, nursery) =>
      val actions = unlocks.actions
      actions.canBuildNestUpgrade || actions.canBuildQueenChamber || actions.canBuildFoodStorageChamber || nursery
    }
    .distinct

  def apply(): ReactiveHtmlElement[HTMLDivElement] = {
    implicit val owner: Owner = new OneTimeOwner(() =>
      println("MainForm owner is being called after dispose")
    )

    dom.window.addEventListener(
      "resize",
      (_: dom.Event) => {
        tooltipVisible.set(false)
        tooltipContent.set(div())
        windowWidthVar.set(window.innerWidth.toLong)
      }
    )

    initialize()

    dom.document.onkeydown = (e: dom.KeyboardEvent) => {
      if (e.ctrlKey) ctrlPressedVar.set(true)
      if (e.shiftKey) shiftPressedVar.set(true)
    }
    dom.document.onkeyup = (e: dom.KeyboardEvent) => {
      if (!e.ctrlKey) ctrlPressedVar.set(false)
      if (!e.shiftKey) shiftPressedVar.set(false)
    }

    val viewChanged = new EventBus[dom.Event]
    val availableTabs = Signal
      .sequence(
        List(
          "tasks" -> antTasksUnlockedSignal,
          "upgrades" -> upgradesTabUnlockedSignal,
          "purchased" -> upgradesTabUnlockedSignal,
          "explore" -> exploreTabUnlockedSignal,
          "territory" -> territoryVisibleSignal,
          "discoveries" -> discoveriesVisibleSignal,
          "lineage" -> lineageVisibleSignal,
          "chambers" -> chambersVisible,
        ).map { case (id, visible) => visible.map(value => Option.when(value)(id)) }
      )
      .map(_.flatten.toSet ++ Set("nest", "settings", "wiki", "milestones"))
      .distinct
    def publishTabs(element: HTMLElement, tabs: Set[String]): Unit = {
      val event = new dom.CustomEvent(
        "antidle-tabs-changed",
        new dom.CustomEventInit {
          detail = js.Array(tabs.toSeq: _*)
        }
      )
      element.dispatchEvent(event)
    }
    val application = Var(Option.empty[HTMLDivElement])
    var currentTabs = Set("nest", "settings", "wiki", "milestones")
    div(
      className := "antidle-app",
      dataAttr("nest-icon-path") := ResourceIcons.Colony.path,
      child.maybe <-- SaveLoad.conflict.signal.map(_.map { _ =>
        div(
          className := "mx-2 my-2 p-2 rounded border bg-body-tertiary",
          role := "alert",
          b("A newer colony save exists"),
          div("Another tab saved this slot. This tab’s progress is still in memory."),
          div(
            className := "d-flex flex-wrap gap-2 mt-2",
            button(
              typ := "button",
              className := "btn btn-outline-primary",
              "Export this colony",
              onClick --> (_ => exportColony())
            ),
            button(
              typ := "button",
              className := "btn btn-outline-primary",
              "Load newer save",
              onClick --> (_ => SaveLoad.loadConflictingSave())
            ),
            button(
              typ := "button",
              className := "btn btn-outline-secondary",
              "Keep playing",
              onClick --> (_ => SaveLoad.conflict.set(None))
            ),
          ),
        )
      }),
      Option.when(SaveLoad.inPlaytest)(
        div(
          className := "mx-2 my-2 p-2 rounded border bg-body-tertiary d-flex align-items-center justify-content-between flex-wrap gap-2",
          span("Playtest colony"),
          button(
            typ := "button",
            className := "btn btn-outline-primary",
            "Return to regular colony",
            onClick --> { _ =>
              useSignalValue[AllData](
                snapshotSignal,
                current =>
                  SaveLoad.withWriteLock {
                    SaveLoad.returnToRegularColony(current).left.foreach(SaveLoad.showError)
                  }
              )
            },
          ),
        )
      ),
      onMountCallback(ctx => application.set(Some(ctx.thisNode.ref))),
      availableTabs.combineWith(application.signal) --> { case (tabs, element) =>
        currentTabs = tabs
        element.foreach(publishTabs(_, tabs))
      },
      eventProp[dom.Event]("antidle-navigation-ready") --> { _ =>
        application.now().foreach(publishTabs(_, currentTabs))
      },
      eventProp[dom.CustomEvent]("antidle-save-for-update") --> { event =>
        val finish = event.detail.asInstanceOf[js.Function1[Boolean, Unit]]
        SaveLoad.withWriteLock(
          actionUpdater.writer.onNext {
            _.tap { data =>
              val saved = SaveLoad.saveToLocalStorage(data)
              saved.left.foreach(SaveLoad.showError)
              if (saved.isRight) Utils.pause = true
              finish(saved.isRight)
            }
          },
          () => finish(false)
        )
      },
      eventProp[dom.Event]("antidle-resume-after-update") --> (_ => Utils.pause = false),
      eventProp[dom.Event]("antidle-view-changed") --> viewChanged.writer,
      div(
        className := "m-2 game-layout",
        className := "d-flex justify-content-start",
        className := "fs-4",
        className("flex-column") <-- windowWidthVar.signal.map(_ < windowWidthLimit),
        div(
          className := "resources-column",
          resourcesDiv(viewChanged.events),
        ),
        mainTabsDiv,
        div(
          className := "activity-column",
          child.maybe <-- ifUnlockedOpt(buildQueueUnlockedSignal)(buildQueueDiv),
          messagesDiv,
        ),
      ),
      div(
        className := "game-build-footer small text-body-secondary text-center py-2",
        buildVersion
      ),
      onMouseMove --> (ev => {
        Var.set(
          tooltipX -> ev.clientX.toInt,
          tooltipY -> ev.clientY.toInt,
          tooltipVisible ->
            tooltipTarget
              .now()
              .ref
              .getBoundingClientRect()
              .pipe { rect =>
                ev.clientX >= rect.left &&
                  ev.clientX <= rect.right &&
                  ev.clientY >= rect.top &&
                  ev.clientY <= rect.bottom
              }
        )
      }),
      tooltip,
      onMountCallback { _ =>
        windowWidthVar.set(window.innerWidth.toLong)
      },
      Modals.importModal(),
      Modals.newGameModal(),
      UIUtils.createShowGlobalAlertsDiv(),
    )
  }

  private def catchUpDiv: ReactiveHtmlElement[HTMLDivElement] = {
    val isBehind: Signal[Boolean] =
      currentTickSignal
        .combineWith(targetTickSignal)
        .map { case (currentTick, targetTick) =>
          targetTick - currentTick > 0
        }
        .distinct

    div(
      className := "catchup-host",
      child <--
        isBehind.map {
          case false =>
            span()
          case true =>
            span(
              className := "catchup-controls",
              span(className := "catchup-label", "Catch up"),
              span(
                className := "catchup-time",
                child.text <-- currentTickSignal.combineWith(targetTickSignal).map {
                  case (currentTick, targetTick) => prettyTimeFromTicks(targetTick - currentTick)
                },
              ),
              button(
                className := "btn btn-sm py-0",
                className("btn-success active") <-- Utils.useCatchupTicksVar,
                className("btn-secondary") <-- Utils.useCatchupTicksVar.signal.map(!_),
                `type` := "button",
                aria.label := "Use stored catch-up time",
                aria.pressed <-- Utils.useCatchupTicksVar.signal.map(_.toString),
                child.text <-- Utils.useCatchupTicksVar.signal.map(if (_) "On" else "Off"),
                onClick --> { _ =>
                  Utils.useCatchupTicksVar.update(!_)
                }
              )
            )
        }
    )
  }

  private def mainTabsDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "card m-1 game-main",
      width.px := NestWidth,
      maxWidth.px := NestWidth,
      div(
        className := "card-header",
        ul(
          className := "nav nav-tabs card-tabs",
          role := "tablist",
          li(
            className := "nav-item",
            a(
              className := "nav-link active",
              idAttr := "nest-tab",
              dataAttr("bs-toggle") := "tab",
              href := "#nestContent",
              role := "tab",
              "Nest"
            )
          ),
          child.maybe <-- ifUnlockedOpt(antTasksUnlockedSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "tasks-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#tasksContent",
                role := "tab",
                "Jobs",
                child <-- availableWorkersCountSignal.map {
                  case 0 => span()
                  case n => span(s" (", prettyNumberInt(n), ")")
                },
              )
            )
          ),
          child.maybe <-- ifUnlockedOpt(upgradesTabUnlockedSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "upgrades-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#upgradesContent",
                role := "tab",
                "Upgrades",
              )
            )
          ),
          child.maybe <-- ifUnlockedOpt(exploreTabUnlockedSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "explore-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#exploreContent",
                role := "tab",
                "Explore",
              )
            )
          ),
          child.maybe <-- ifUnlockedOpt(territoryVisibleSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "territory-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#territoryContent",
                role := "tab",
                "Territory"
              )
            ),
          ),
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "discoveries-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#discoveriesContent",
                role := "tab",
                "Discoveries"
              )
            ),
          ),
          child.maybe <-- ifUnlockedOpt(lineageVisibleSignal)(
            li(
              className := "nav-item",
              a(
                className := "nav-link",
                idAttr := "lineage-tab",
                dataAttr("bs-toggle") := "tab",
                href := "#lineageContent",
                role := "tab",
                "Lineage"
              )
            ),
          ),
          li(
            className := "nav-item",
            a(
              className := "nav-link",
              idAttr := "milestones-tab",
              dataAttr("bs-toggle") := "tab",
              href := "#milestonesContent",
              role := "tab",
              "Milestones"
            )
          ),
          li(
            className := "nav-item",
            a(
              className := "nav-link",
              idAttr := "settings-tab",
              dataAttr("bs-toggle") := "tab",
              href := "#settingsContent",
              role := "tab",
              "Settings"
            )
          ),
          li(
            className := "nav-item",
            a(
              className := "nav-link",
              idAttr := "wiki-tab",
              dataAttr("bs-toggle") := "tab",
              href := "#wikiContent",
              role := "tab",
              "Wiki"
            )
          ),
        )
      ),
      div(
        className := "card-body",
        div(
          className := "tab-content",
          div(
            className := "tab-pane show active",
            idAttr := "nestContent",
            role := "tabpanel",
            nestDiv,
          ),
          child.maybe <-- ifUnlockedOpt(antTasksUnlockedSignal)(
            div(
              className := "tab-pane",
              idAttr := "tasksContent",
              role := "tabpanel",
              tasksDiv,
            )
          ),
          child.maybe <-- ifUnlockedOpt(upgradesTabUnlockedSignal)(
            div(
              className := "tab-pane",
              idAttr := "upgradesContent",
              role := "tabpanel",
              ResearchView.render,
            )
          ),
          child.maybe <-- ifUnlockedOpt(exploreTabUnlockedSignal)(
            div(
              className := "tab-pane",
              idAttr := "exploreContent",
              role := "tabpanel",
              ExploreView.render,
            )
          ),
          child.maybe <-- ifUnlockedOpt(territoryVisibleSignal)(
            div(
              className := "tab-pane",
              idAttr := "territoryContent",
              role := "tabpanel",
              territoryDiv
            ),
          ),
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            div(
              className := "tab-pane",
              idAttr := "discoveriesContent",
              role := "tabpanel",
              discoveriesDiv
            ),
          ),
          child.maybe <-- ifUnlockedOpt(lineageVisibleSignal)(
            div(
              className := "tab-pane",
              idAttr := "lineageContent",
              role := "tabpanel",
              prestigeDiv
            ),
          ),
          div(
            className := "tab-pane",
            idAttr := "milestonesContent",
            role := "tabpanel",
            milestonesDiv
          ),
          div(
            className := "tab-pane",
            idAttr := "settingsContent",
            role := "tabpanel",
            settingsDiv,
          ),
          div(
            className := "tab-pane",
            idAttr := "wikiContent",
            role := "tabpanel",
            aria.labelledBy := "wiki-tab",
            tabIndex := 0,
            WikiView.render,
          ),
        )
      )
    )

  private def milestonesDiv: ReactiveHtmlElement[HTMLDivElement] = div(
    className := "colony-milestones",
    h5(
      child.text <-- statisticsData.signal.map(s =>
        s"Sugar production ×${ColonyMilestones.multiplier(s)}"
      )
    ),
    div(className := "small text-body-secondary mb-3", "×1.01 per milestone · permanent"),
    ColonyMilestones.goals.map { goal =>
      def number(n: Long): String = if (goal.resource) prettyNumberStr(n) else n.toString
      detailsTag(
        className := "achievement-goal border rounded p-2 mb-2",
        rememberDetails("milestone." + goal.id),
        display <-- snapshotSignal
          .map(data => if (ColonyMilestones.visible(goal, data)) "" else "none")
          .distinct,
        summaryTag(
          span(goal.title),
          span(
            className := "d-block small",
            child.text <-- statisticsData.signal.map { stats =>
              val done = goal.completed(stats)
              val earned = s"$done milestone" + (if (done == 1) "" else "s")
              goal
                .next(stats)
                .map(target => s"${number(goal.progress(stats))}/${number(target)} · $earned")
                .getOrElse(s"Complete · $earned") + ColonyMilestones
                .peak(goal, stats)
                .map(p => s" · peak ${number(p)}")
                .getOrElse("")
            }.distinct
          )
        ),
        goal.targets.map { target =>
          div(
            className := "d-flex justify-content-between gap-2 small mt-2",
            span(number(target)),
            span(
              className <-- statisticsData.signal
                .map(s =>
                  if (goal.progress(s) >= target) "text-success fw-bold" else "text-body-secondary"
                )
                .distinct,
              child.text <-- statisticsData.signal
                .map(s => if (goal.progress(s) >= target) "✓ ×1.01" else "×1.01")
                .distinct
            )
          )
        }
      )
    }
  )

  private def statisticsDiv: ReactiveHtmlElement[HTMLElement] =
    detailsTag(
      className := "col-12 colony-statistics mb-3",
      rememberDetails("statistics"),
      summaryTag("Colony statistics"),
      small("Collected resources exclude overflow discarded at full storage."),
      children <-- statisticsData.signal.combineWith(unlocksSignal, explorationSignal).map {
        case (stats, unlocks, exploration) =>
          val rows = List(
            Some("Sugar collected" -> prettyNumberStr(stats.sugarU.thisRun)),
            Option.when(unlocks.resources.showTunnelingSpace)(
              "Tunneling space dug" -> prettyNumberStr(stats.tunnelingSpaceU.thisRun)
            ),
            Option.when(exploration.proteinDiscovered)(
              "Protein collected" -> prettyNumberStr(stats.proteinU.thisRun)
            ),
            Option.when(unlocks.resources.showWater)(
              "Water collected" -> prettyNumberStr(stats.waterU.thisRun)
            ),
            Option.when(unlocks.resources.showResin)(
              "Resin collected" -> prettyNumberStr(stats.resinU.thisRun)
            ),
            Option.when(unlocks.resources.showKnowledge)(
              "Knowledge earned" -> prettyNumberStr(stats.knowledgeU.thisRun)
            ),
            Option.when(unlocks.tabs.exploreTabUnlocked)(
              "Expeditions launched" -> stats.explorationsCount.thisRun.toString
            ),
            Option.when(unlocks.tabs.exploreTabUnlocked)(
              "Expeditions completed" -> stats.explorationsCompleted.thisRun.toString
            ),
            Option.when(unlocks.tabs.exploreTabUnlocked)(
              "Workers sent" -> prettyNumberStr(stats.explorationsWorkersSentU.thisRun)
            ),
            Option
              .when(unlocks.resources.showEggs)("Eggs raised" -> stats.eggsCount.thisRun.toString),
            Option.when(unlocks.resources.showSmallWorkers)(
              "Small workers matured" -> stats.smallWorkersMatured.thisRun.toString
            ),
            Option.when(unlocks.resources.showWorkers)(
              "Workers matured" -> stats.workersMatured.thisRun.toString
            ),
            Option.when(unlocks.resources.showScouts)(
              "Scouts matured" -> stats.scoutsMatured.thisRun.toString
            ),
            Option.when(unlocks.resources.showDaughterQueens)(
              "Royal eggs raised" -> stats.royalEggsRaised.thisRun.toString
            ),
            Option.when(unlocks.resources.showDaughterQueens)(
              "Daughter queens matured" -> stats.daughterQueensMatured.thisRun.toString
            ),
            Option.when(unlocks.resources.showRepletes)(
              "Repletes promoted" -> stats.repletesPromoted.thisRun.toString
            ),
            Option.when(unlocks.resources.showRepletes)(
              "Repletes lost to aging" -> stats.repletesLostToAging.thisRun.toString
            ),
            Option.when(unlocks.resources.showWorkers)(
              "Peak workers" -> stats.maxAliveWorkers.thisRun.toString
            ),
            Option.when(exploration.proteinDiscovered)(
              "Carcasses retrieved" -> stats.carcassesRetrieved.thisRun.toString
            ),
            Option.when(unlocks.resources.showWorkers)(
              "Aging losses" -> (stats.workersLostToAging.thisRun.toLong + stats.scoutsLostToAging.thisRun + stats.repletesLostToAging.thisRun).toString
            ),
            Option.when(stats.territoryDeaths.total > 0)(
              "Territory combat losses" -> stats.territoryDeaths.thisRun.toString
            ),
            Option.when(stats.territoryContests.total > 0)(
              "Territory contests" -> stats.territoryContests.thisRun.toString
            ),
            Option
              .when(stats.raidDeaths.total > 0)("Raid losses" -> stats.raidDeaths.thisRun.toString),
            Option.when(unlocks.resources.showWorkers)(
              "Starvation losses" -> stats.starvationDeaths.thisRun.toString
            ),
          ).flatten
          rows.map { case (label, value) =>
            div(
              className := "statistic-row d-flex flex-wrap justify-content-between gap-3 py-1",
              span(label),
              b(value)
            )
          }
      },
    )

  private def buildVersion: ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "build-version",
      "Build ",
      if (BuildInfo.commit == "development") span("development")
      else
        a(
          href := s"https://github.com/RCMartins-apps/AntIdle/commit/${BuildInfo.commit}",
          target := "_blank",
          rel := "noopener noreferrer",
          className := "text-body-secondary",
          title := (if (BuildInfo.modified) "Local build with uncommitted changes"
                    else "Source commit for this game build"),
          BuildInfo.label,
        ),
    )

  private def exportColony(): Unit =
    actionUpdater.writer.onNext {
      _.tap { allData =>
        val dataStr = SaveLoad.saveString(allData)
        Option(dom.window.navigator.clipboard).foreach(
          _.writeText(dataStr).toFuture.map(_ =>
            currentGlobalAlert.set(Some("Exported to clipboard!"))
          )(queue)
        )
      }
    }

  private def downloadColonyBackup(): Unit =
    actionUpdater.writer.onNext {
      _.tap { allData =>
        val blob = new dom.Blob(js.Array(SaveLoad.saveString(allData)))
        val url = dom.URL.createObjectURL(blob)
        val link = dom.document.createElement("a").asInstanceOf[HTMLAnchorElement]
        link.href = url
        link.download = "antidle-colony.save.txt"
        dom.document.body.appendChild(link)
        link.click()
        link.remove()
        scala.scalajs.js.timers.setTimeout(1000)(dom.URL.revokeObjectURL(url))
      }
    }

  private def settingsDiv: ReactiveHtmlElement[HTMLDivElement] = {
    def settingsButton(
        name: String,
        onClickF: () => Unit,
        buttonClass: String = "btn-primary",
    ): ReactiveHtmlElement[HTMLDivElement] =
      div(
        className := "col-6 px-3 py-2 d-grid",
        button(
          className := "btn fs-4",
          className := buttonClass,
          `type` := "button",
          name,
          onClick --> { _ => onClickF() }
        ),
      )

    div(
      className := "row",
      div(className := "settings-build col-12 px-3 py-2 small text-body-secondary", buildVersion),
      statisticsDiv,
      settingsButton(
        "Manual Save",
        () =>
          SaveLoad.withWriteLock {
            actionUpdater.writer.onNext(
              _.tap(
                SaveLoad
                  .saveToLocalStorage(_)
                  .fold(SaveLoad.showError, _ => currentGlobalAlert.set(Some("Game Saved!")))
              )
            )
          }
      ),
      div(
        className := "col-6 px-3 py-2 d-grid",
        div(
          className := "d-flex justify-content-between align-items-center",
          "Auto-Save:",
          children <--
            autoSaveMode.signal.map { selectedSaveMode =>
              allSaveModes.map { saveMode =>
                button(
                  className := "btn fs-4",
                  className := (if (saveMode == selectedSaveMode) "btn-primary active"
                                else "btn-secondary"),
                  `type` := "button",
                  saveMode match {
                    case 0           => "Off"
                    case t if t < 60 => s"${t}s"
                    case t           => s"${t / 60}m"
                  },
                  onClick --> { _ => autoSaveMode.set(saveMode) }
                )
              }
            },
        ),
      ),
      settingsButton(
        "Load",
        () =>
          SaveLoad
            .loadFromLocalStorage()
            .fold(SaveLoad.showError, SaveLoad.restore(_, manual = true))
      ),
      settingsButton("Export", () => exportColony()),
      settingsButton("Download backup", () => downloadColonyBackup()),
      settingsButton(
        "Reset interface preferences",
        () => {
          UiPreferences.reset()
          currentGlobalAlert.set(Some("Interface preferences reset."))
        },
        buttonClass = "btn-outline-secondary"
      ),
      settingsButton(
        "Import",
        () => {
          jquery.$(s"#${Constants.ImportModalId}").asInstanceOf[js.Dynamic].modal("show")
        },
      ),
      settingsButton(
        "Hard Reset",
        () => {
          jquery.$(s"#${Constants.NewGameModalId}").asInstanceOf[js.Dynamic].modal("show")
        },
        buttonClass = "btn-danger",
      ),
    )
  }

  private def buildQueueDiv: ReactiveHtmlElement[HTMLDivElement] = {
    def buildTaskName(
        buildTask: BuildTask,
        index: Int,
        size: Int
    ): ReactiveHtmlElement[HTMLDivElement] = {
      def moveButton(direction: String, arrow: String, offset: Int) = {
        val to = index + offset
        val label = s"Move ${buildTask.chamberType.name} $direction"
        button(
          className := s"btn btn-outline-secondary build-move move-$direction",
          typ := "button",
          disabled := (to < 0 || to >= size),
          aria.label := label,
          title := label,
          span(aria.hidden := true, arrow),
          onClick --> { _ =>
            Actions.moveBuildTask(index, to)
            // The queue is rendered again with the new level prices.
            queue.execute(() => {
              val row = dom.document.querySelector(s".queued-building[data-queue-index='$to']")
              Option(row).foreach { entry =>
                Option(entry.querySelector(s".move-$direction:enabled"))
                  .orElse(Option(entry.querySelector(".build-move:enabled")))
                  .foreach(_.asInstanceOf[HTMLButtonElement].focus())
              }
            })
          }
        )
      }

      div(
        className := "queued-building border-bottom pb-2 mb-2",
        dataAttr("queue-index") := index.toString,
        span(
          className := "build-task",
          buildTask.chamberType.name,
          " · ",
          child.text <-- nestSignal.map { nest =>
            val from = Construction.level(nest.chambers, buildTask.chamberType) +
              nest.buildQueue.take(index).count(_.chamberType == buildTask.chamberType)
            s"$from → ${from + 1}"
          }.distinct,
        ).amendThis(elem =>
          createTooltipForActionButton(
            elem = elem,
            descriptionSignal = Val(None),
            costSignal = Val(buildTask.actionCost),
            bonusSignal = Actions.getActionBonus(buildTask.chamberType),
          )
        ),
        div(
          className := "build-controls",
          Option.when(size > 1)(moveButton("up", "↑", -1)),
          Option.when(size > 1)(moveButton("down", "↓", 1)),
          button(
            className := "btn btn-outline-danger build-cancel",
            typ := "button",
            aria.label := s"Cancel ${buildTask.chamberType.name}",
            title := s"Cancel ${buildTask.chamberType.name}",
            span(aria.hidden := true, "×"),
            onClick --> (_ => Actions.removeBuildTask(index))
          ),
        ),
        span(
          className := "build-estimate small",
          title := "Estimated simulated time at current net production, including earlier queued costs; future chamber bonuses are not predicted.",
          child <-- resourcesSignal
            .combineWith(nestSignal, TickCalculator.constructionResourceRates)
            .map { case (resources, nest, rates) =>
              ConstructionWait
                .ticks(nest.buildQueue.take(index + 1).map(_.actionCost), resources, rates)
                .lastOption
                .getOrElse(0L)
            }
            .distinct
            .map {
              case ticks if ticks < 0L => span(className := "text-danger", "Needs resources")
              case 0L                  => span()
              case ticks               => span(s"Resources in ~${prettyTimeFromTicks(ticks)}")
            }
        ),
        actionCosts(Val(buildTask.actionCost), Val(Nil)),
      )
    }

    div(
      className := "m-1",
      className := "card game-panel build-queue-panel",
      className := "fs-4",
      width.px := MessagesWidth,
      maxWidth.px := MessagesWidth,
      div(
        className := "card-header",
        div(
          span(
            "Build Queue (",
            child.text <-- buildQueueSignal.map(_.size),
            "/",
            child.text <-- maxBuildQueueSignal,
            ")"
          ),
        )
      ),
      div(
        className := "card-body",
        div(
          className := "d-grid gap-2",
          child <-- buildQueueSignal.map {
            case Seq() =>
              span(className := "queue-empty small text-body-secondary", "No construction queued.")
            case seq =>
              div(seq.zipWithIndex.map { case (buildTask, index) =>
                buildTaskName(buildTask, index, seq.size)
              })
          }
        )
      )
    )
  }

  private def messagesDiv: ReactiveHtmlElement[HTMLDivElement] = {
    val highlights = messagesSignal.map(_.filter(m => EventLog.kind(m) != "routine")).distinct
    val routine = messagesSignal.map(EventLog.routine).distinct
    def row(text: String): ReactiveHtmlElement[HTMLDivElement] =
      div(className := s"log-entry log-${EventLog.kind(text)}", text)
    div(
      className := "m-1 card game-panel event-log",
      width.px := MessagesWidth,
      maxWidth.px := MessagesWidth,
      div(className := "card-header", "Colony log"),
      div(
        className := "card-body",
        div(className := "log-highlights", children <-- highlights.map(_.take(4).map(row))),
        detailsTag(
          rememberDetails("log.history"),
          className := "log-history mt-2",
          display <-- highlights.map(m => if (m.size > 4) "" else "none"),
          summaryTag(
            minHeight.px := 44,
            child.text <-- highlights.map(m => s"Earlier events (${Math.max(0, m.size - 4)})")
          ),
          children <-- highlights.map(_.drop(4).map(row))
        ),
        detailsTag(
          rememberDetails("log.routine"),
          className := "log-routine mt-2",
          display <-- routine.map(m => if (m.nonEmpty) "" else "none"),
          summaryTag(
            minHeight.px := 44,
            child.text <-- routine.map { entries =>
              val count = entries.map(_.reports).sum
              s"Routine activity ($count ${if (count == 1) "report" else "reports"})"
            }
          ),
          div(className := "small text-body-secondary", "Latest report from each source"),
          children <-- routine.map(
            _.map(entry =>
              div(
                className := "log-entry log-routine-entry",
                span(entry.text),
                if (entry.reports > 1) small(s" · ${entry.reports} reports") else emptyNode
              )
            )
          )
        ),
        child.maybe <-- messagesSignal
          .map(_.isEmpty)
          .distinct
          .map(empty =>
            Option.when(empty)(div(className := "small text-body-secondary", "No events yet."))
          )
      )
    )
  }

  private def resourcesDiv(
      viewChanged: EventStream[dom.Event]
  ): Modifier[ReactiveHtmlElement[HTMLDivElement]] = {
    val expanded = Var(UiPreferences.get("resources.expanded").contains("true"))
    val toggle = Var(Option.empty[HTMLButtonElement])
    val details = Var(Option.empty[HTMLDivElement])
    def restoreResourceFocus(): Unit =
      if (details.now().exists(_.contains(document.activeElement)))
        toggle.now().foreach(_.focus())

    def setExpanded(value: Boolean): Unit = {
      if (!value && windowWidthVar.now() <= 767) restoreResourceFocus()
      expanded.set(value)
      if (value) UiPreferences.set("resources.expanded", "true")
      else UiPreferences.remove("resources.expanded")
    }

    div(
      className("resources-expanded") <-- expanded.signal,
      UiPreferences.resets --> (_ => setExpanded(false)),
      viewChanged --> (_ => if (windowWidthVar.now() <= 767 && expanded.now()) setExpanded(false)),
      windowWidthVar.signal.map(_ <= 767).distinct.changes --> { mobile =>
        if (mobile && !expanded.now()) restoreResourceFocus()
      },
      className := "m-1",
      className := "card game-panel",
      className := "fs-5",
      width.px := ResourcesWidth,
      maxWidth.px := ResourcesWidth,
      div(
        className := "card-header",
        span(className := "desktop-resource-heading", "Resources"),
        button(
          typ := "button",
          className := "mobile-resource-toggle",
          aria.controls := "resource-details",
          aria.expanded <-- expanded.signal,
          aria.label <-- expanded.signal.map(
            if (_) "Collapse resource details" else "Expand resource details"
          ),
          onMountCallback(ctx => toggle.set(Some(ctx.thisNode.ref))),
          expanded.signal.combineWith(toggle.signal) --> { case (open, button) =>
            button.foreach { element =>
              if (open) element.removeAttribute("aria-describedby")
              else element.setAttribute("aria-describedby", "compact-resource-summary")
            }
          },
          onClick --> (_ => setExpanded(!expanded.now())),
          span(
            className := "resource-summary-heading",
            span("Resources"),
            span(aria.hidden := true, child.text <-- expanded.signal.map(if (_) "▴" else "▾")),
          ),
          span(
            className := "compact-resources",
            idAttr := "compact-resource-summary",
            compactResource("Sugar", sugarSignal, TickCalculator.displayedSugarRate),
            compactResource(
              "Tunneling Space",
              tunnelingSpaceSignal,
              TickCalculator.tunnelingSpaceTickGain.sum,
              unlocksSignal.map(_.resources.showTunnelingSpace)
            ),
            compactResource(
              "Protein",
              proteinSignal,
              TickCalculator.displayedProteinRate,
              proteinVisibleSignal
            ),
            compactResource(
              "Water",
              waterSignal,
              TickCalculator.waterTickGain.sum,
              unlocksSignal.map(_.resources.showWater)
            ),
            compactResource(
              "Resin",
              resinSignal,
              TickCalculator.resinTickGain.sum,
              unlocksSignal.map(_.resources.showResin)
            ),
            compactResource(
              "Knowledge",
              knowledgeSignal,
              Val(0L),
              unlocksSignal.map(_.resources.showKnowledge),
              hasTiming = false
            ),
            compactResource(
              "Ants",
              populationResourceSignal,
              Val(0L),
              unlocksSignal.map(_.resources.showWorkers),
              hasTiming = false
            ),
          ),
        ),
      ),
      catchUpDiv,
      div(
        className := "card-body",
        idAttr := "resource-details",
        onMountCallback(ctx => details.set(Some(ctx.thisNode.ref))),
        child.maybe <-- createResourceDiv(
          name = "Sugar",
          netRateSignal = Some(TickCalculator.displayedSugarRate),
          resourceSignal = sugarSignal,
          resourceEstimationSignal = Val(
            prettyResourceEstDiv(
              TickCalculator.sugarTickGain,
              sugarSignal,
              Some(TickCalculator.displayedSugarRate),
              showTooltip = false
            )
          ),
          showSignal = Val(true),
          rateDetails = Some(TickCalculator.sugarRateDetails),
        ),
        child.maybe <-- createResourceDiv(
          name = "Tunneling Space",
          netRateSignal = Some(TickCalculator.tunnelingSpaceTickGain.sum),
          resourceSignal = tunnelingSpaceSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.tunnelingSpaceTickGain, tunnelingSpaceSignal)),
          showSignal = unlocksSignal.map(_.resources.showTunnelingSpace),
        ),
        child.maybe <-- createResourceDiv(
          name = "Protein",
          netRateSignal = Some(TickCalculator.displayedProteinRate),
          resourceSignal = proteinSignal,
          resourceEstimationSignal = Val(
            prettyResourceEstDiv(
              TickCalculator.proteinTickGain,
              proteinSignal,
              Some(TickCalculator.displayedProteinRate)
            )
          ),
          showSignal = proteinVisibleSignal,
        ),
        child.maybe <-- createResourceDiv(
          name = "Water",
          netRateSignal = Some(TickCalculator.waterTickGain.sum),
          resourceSignal = waterSignal,
          resourceEstimationSignal = Val(
            div(
              prettyResourceEstDiv(TickCalculator.waterTickGain, waterSignal),
              child.maybe <-- waterSignal.map(w =>
                Option.when(w.amount > w.maxAmount)(
                  div(
                    className := "small",
                    "Collection paused",
                    title := "Stored water is retained above capacity. Collect again once there is free space."
                  )
                )
              )
            )
          ),
          showSignal = unlocksSignal.map(_.resources.showWater),
          tooltipOpt =
            Some(div("Water supplies gathered from discovered sources in the dry area.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Resin",
          netRateSignal = Some(TickCalculator.resinTickGain.sum),
          resourceSignal = resinSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.resinTickGain, resinSignal)),
          showSignal = unlocksSignal.map(_.resources.showResin),
          tooltipOpt =
            Some(div("Collected from woodland resin sources to line protected brood chambers.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Knowledge",
          resourceSignal = knowledgeSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showKnowledge),
        ),
        nbsp,
        child.maybe <-- createResourceDiv(
          name = "Brood",
          resourceSignal = eggResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showEggs),
          tooltipOpt = Some(
            div(
              child <--
                antsSignal.map(_.eggsAndLarvae).map {
                  case Seq() =>
                    "No eggs"
                  case seq =>
                    div(
                      seq.map { antBlood =>
                        div(
                          className := "brood-entry",
                          span(
                            className := "brood-entry-progress",
                            child.text <-- currentTickSignal
                              .combineWith(upgradesSignal)
                              .map { case (currentTick, upgrades) =>
                                val percent = antBlood match {
                                  case brood: AntBrood.Developing =>
                                    brood.care * 100L / brood.totalCare
                                  case _ =>
                                    val duration = Math
                                      .max(1L, ColonyDevelopment.growthTicks(antBlood, upgrades))
                                    val elapsed = Math.max(
                                      0L,
                                      Math.min(duration, currentTick - antBlood.initialTick)
                                    )
                                    elapsed * 100L / duration
                                }
                                f"${percent}%02d%%"
                              }
                              .distinct
                          ),
                          ResourceIcons.icon(ResourceIcons.forBrood(antBlood)),
                          b(antBlood.name),
                        )
                      }
                    )
                }
            )
          ),
        ),
        child.maybe <-- createResourceDiv(
          name = "Ants",
          resourceSignal = populationResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showWorkers),
          tooltipOpt = Some(div("Includes growing brood and ants on expeditions.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Small workers",
          resourceSignal = antsSignal.map(a => BasicResource(a.smallWorkers, 0L)),
          resourceEstimationSignal = Val(div()),
          showSignal = antsSignal.map(_.smallWorkers > 0L),
          tooltipOpt = Some(
            div("Small founding workers: 50% task output and upkeep; live 30–40 simulated minutes.")
          ),
        ),
        child.maybe <-- createResourceDiv(
          name = "Workers",
          resourceSignal = workersResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showWorkers)
        ),
        child.maybe <-- createResourceDiv(
          name = "Repletes",
          resourceSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
            BasicResource(ants.repletes, RepleteProgression.housing(nest) * u)
          },
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showRepletes),
          tooltipOpt = Some(
            div("Promoted workers store 100 extra water each and consume normal ant sugar upkeep.")
          ),
        ),
        child.maybe <-- createResourceDiv(
          name = "Daughter queens",
          resourceSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
            BasicResource(ants.daughterQueens, nest.royalBroodSlots.toLong * u)
          },
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showDaughterQueens),
          tooltipOpt = Some(
            div("Mature daughters consume normal ant sugar upkeep and occupy a royal brood slot.")
          ),
        ),
        child.maybe <-- createResourceDiv(
          name = "Scouts",
          resourceSignal = scoutResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showScouts),
        ),
      )
    )
  }

  private def compactResource(
      name: String,
      resource: Signal[BasicResource],
      rate: Signal[Long],
      visible: Signal[Boolean] = Val(true),
      hasTiming: Boolean = true,
  ): Modifier[ReactiveHtmlElement[HTMLSpanElement]] =
    child.maybe <-- visible.distinct.map { show =>
      Option.when(show) {
        val amounts = resource
          .map(r =>
            if (hasTiming || name == "Knowledge") resourceAmountStr(r.amount)
            else prettyNumberStr(r.amount)
          )
          .distinct
        val timing = resource
          .combineWith(rate)
          .map { case (r, net) =>
            Option.when(hasTiming)(ResourceTiming.estimate(r, net)).flatten
          }
          .distinct
        val description = amounts.combineWith(timing).map { case (amount, estimate) =>
          s"$name: $amount${estimate.fold("")(e => s", ${e.text}")}"
        }
        span(
          className := "compact-resource",
          dataAttr("resource") := name,
          className("at-capacity") <-- resource.map(_.atCapacity).distinct,
          className("running-low") <-- timing.map(_.exists(_.toEmpty)),
          aria.label <-- description,
          title <-- description,
          ResourceIcons.forLabel(name).map(ResourceIcons.icon),
          span(className := "visually-hidden", s"$name: "),
          span(className := "compact-resource-value", child.text <-- amounts),
          small(
            className := "compact-resource-warning",
            child.text <-- timing.map {
              case Some(ResourceTiming.Estimate(true, ticks)) if ticks > 0L =>
                s"~${prettyTimeFromTicks(ticks)} left"
              case Some(ResourceTiming.Estimate(false, ticks)) if ticks > 0L =>
                s"Full ~${prettyTimeFromTicks(ticks)}"
              case Some(estimate) => estimate.text
              case None           => ""
            },
          ),
        )
      }
    }

  private def createResourceDiv(
      name: String,
      resourceSignal: Signal[BasicResource],
      resourceEstimationSignal: Signal[ReactiveHtmlElement[HTMLDivElement]],
      showSignal: Signal[Boolean],
      tooltipOpt: Option[ReactiveHtmlElement[HTMLDivElement]] = None,
      netRateSignal: Option[Signal[Long]] = None,
      rateDetails: Option[ReactiveHtmlElement[HTMLDivElement]] = None,
  ): Signal[Option[ReactiveHtmlElement[HTMLDivElement]]] = {
    val amountSignal: Signal[Long] = resourceSignal.map(_.amount).distinct
    val maxAmountSignal: Signal[Long] = resourceSignal.map(_.maxAmount).distinct
    def resourceName: ReactiveHtmlElement[HTMLSpanElement] =
      span(
        className := "resource-label",
        ResourceIcons.forLabel(name).map(ResourceIcons.icon),
        span(name)
      )

    showSignal.distinct.map {
      case false =>
        None
      case true =>
        Some(
          div(
            className := "row resource-row",
            div(
              className := "col-4 text-nowrap resource-name",
              child <-- windowWidthVar.signal.map(_ <= 767).distinct.map { mobile =>
                tooltipOpt.filter(_ => mobile) match {
                  case Some(content) =>
                    detailsTag(
                      rememberDetails("resource." + resourceName),
                      summaryTag(resourceName),
                      content
                    )
                  case None => resourceName
                }
              },
              netRateSignal.map { rate =>
                div(
                  className := "resource-timing",
                  title := "Estimated simulated time at the current net rate.",
                  child.maybe <-- resourceSignal.combineWith(rate).map { case (resource, net) =>
                    ResourceTiming.estimate(resource, net).map { estimate =>
                      span(
                        className := (if (estimate.toEmpty) "text-danger"
                                      else "text-body-secondary"),
                        estimate.text
                      )
                    }
                  },
                )
              },
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-3 text-end text-nowrap resource-amount",
              className <-- resourceSignal.map(resource =>
                if (resource.atCapacity) "text-primary fw-bold" else ""
              ),
              child.text <-- amountSignal.map(
                if (netRateSignal.isDefined || name == "Knowledge") resourceAmountStr
                else prettyNumberStr
              ),
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-2 text-start text-body-tertiary text-nowrap ps-0",
              child <--
                maxAmountSignal.map { maxValue =>
                  if (maxValue == 0 || maxValue == Long.MaxValue)
                    nbsp
                  else
                    span("/", prettyNumberSimple(maxValue))
                },
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-3 text-end text-nowrap",
              child <-- resourceEstimationSignal
            ),
            rateDetails.map(content =>
              detailsTag(
                className := "resource-rate-disclosure",
                summaryTag(aria.label := s"$name rate details", "Rate details"),
                content,
              )
            ),
          )
        )
    }
  }

  private[main] def actionAmountControl(
      controlId: String,
      amountVar: Var[Int]
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "mobile-only batch-controls",
      label(
        forId := controlId,
        "Action amount",
      ),
      select(
        idAttr := controlId,
        className := "form-select",
        Seq(1, 10, 25, 100).map { amount =>
          option(value := amount.toString, s"×$amount")
        },
        value <-- amountVar.signal.map(_.toString),
        onChange.mapToValue.map(_.toInt) --> amountVar.writer,
      ),
    )

  private def constructionBlockedSignal(kind: ChamberType): Signal[Option[String]] =
    nestSignal
      .combineWith(upgradesSignal)
      .map { case (nest, upgrades) => Construction.blockedReason(nest, upgrades, kind) }
      .distinct

  private def nestDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def standardBuyChamber(
        name: String,
        chamberType: ChamberType,
        visible: Signal[Boolean],
    ): Signal[Option[ReactiveHtmlElement[HTMLDivElement]]] =
      ifUnlockedOpt(visible) {
        val chamberSignal = Actions.getChamber(chamberType)
        val actionCost = Actions.buildChamberCost(chamberType)
        actionButton(
          name = name,
          topRightBadgeSignal =
            chamberSignal.map(_.level).distinct.map(Some(_).filter(_ > 0).map(span(_))),
          enabledSignal = constructionBlockedSignal(chamberType).map(_.isEmpty),
          blockedReasonSignal = constructionBlockedSignal(chamberType),
          descriptionSignal = Val(None),
          costSignal = actionCost,
          bonusSignal = Actions.getActionBonus(chamberType),
          onClickAction = _ =>
            useSignalValue[ActionCost](
              actionCost,
              actionCost => Actions.addBuildTask(BuildTask(chamberType, actionCost))
            ),
        )
      }

    div(
      className := "row",
      BroodView.render,
      child.maybe <-- ifUnlockedOpt(nestSignal.map(_.royalBroodSlots > 0).distinct) {
        div(
          className := "col-12 nest-royal-nursery border-top my-2 py-2",
          infoHeading(
            "Royal brood",
            div(
              "Up to five Royal Nurses tend one daughter queen. Full staffing takes about 10 minutes. Care pauses when supplies run out; progress is kept."
            )
          ),
          royalRearingDiv,
        )
      },
      child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.RepleteStorage).unlocked)) {
        div(
          className := "col-12 nest-repletes border-top my-2 py-2",
          h5("Living water storage"),
          div(
            className := "row",
            actionButton(
              name = "Promote Replete",
              enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal).map {
                case (ants, nest, resources) =>
                  ants.availableWorkersCount >= u && ants.repletes / u < RepleteProgression
                    .housing(nest) && resources
                    .hasResources(RepleteProgression.cost)
              },
              costSignal = Val(RepleteProgression.cost),
              bonusSignal = antsSignal
                .map(a => ActionBonus(maxWater = RepleteProgression.waterPerReplete(a.lineage)))
                .distinct,
              requirementsSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                List(
                  ActionRequirement(
                    "replete slots",
                    Math.max(0L, RepleteProgression.housing(nest) * u - ants.repletes)
                  )
                )
              },
              descriptionSignal = Val(
                Some(
                  div(
                    actionFact("Worker", "Oldest available home worker"),
                    actionFact("Lifespan", "Fresh 90–120 simulated minutes"),
                    actionFact("Upkeep", s"${resourceRateStr(antsSugarUpkeepSecond)} sugar/s"),
                    actionFact("Role", "Water storage; no worker tasks"),
                    actionFact("Storage", "Water kept if capacity falls"),
                  )
                )
              ),
              onClickAction = _ => Actions.promoteReplete(),
            ),
          ),
        )
      },
      child.maybe <-- nestSignal
        .combineWith(explorationSignal)
        .map { case (nest, exploration) =>
          Option.when(nest.dryClearingOutpost || OutpostProgression.visible(exploration))(
            (nest.dryClearingOutpost, OutpostProgression.expansionVisible(nest, exploration))
          )
        }
        .distinct
        .map(_.map { case (established, expansionVisible) =>
          div(
            className := "col-12 nest-outposts border-top my-2 py-2",
            h5("Outposts"),
            if (established)
              div(
                className := "dry-clearing-outpost",
                b(
                  child.text <-- nestSignal
                    .map(nest => s"Dry Clearing Outpost · Level ${OutpostProgression.level(nest)}")
                    .distinct
                ),
                div(
                  child.text <-- nestSignal
                    .map(nest =>
                      s"+${OutpostProgression.housing(nest) / u} colony capacity · ${OutpostProgression
                          .capacity(nest) / u} Seed Forager slots"
                    )
                    .distinct
                ),
                div(
                  child.text <-- antsSignal
                    .combineWith(resourcesSignal, upgradesSignal, nestSignal)
                    .map { case (ants, resources, upgrades, currentNest) =>
                      val harvest =
                        OutpostProgression.harvestPerTick(ants, currentNest, upgrades, resources)
                      val state =
                        if (ants.taskWorkers(AntTask.SeedForager) == 0L) "0 Seed Foragers"
                        else if (resources.protein.atCapacity && harvest.protein == 0L)
                          "Protein full · water use paused"
                        else if (resources.water.amount == 0L) "Needs water · foraging paused"
                        else
                          s"${signedRateStr(harvest.protein * TicksPerSecond, compact = false)} protein/s · ${signedRateStr(-harvest.water * TicksPerSecond, compact = false)} water/s"
                      state
                    }
                ),
                child.maybe <-- antsSignal
                  .map(_.lineage.satelliteNests)
                  .distinct
                  .combineWith(nestSignal.map(_.dryClearingSpecialization).distinct)
                  .map {
                    case (false, _)                              => None
                    case (true, OutpostProgression.SeedStation)  => Some(div(b("Seed Station")))
                    case (true, OutpostProgression.ScoutStation) => Some(div(b("Scout Station")))
                    case (true, _) =>
                      Some(
                        div(
                          className := "mt-2 d-grid gap-2",
                          b("Choose one station for this colony"),
                          button(
                            typ := "button",
                            className := "btn btn-outline-primary",
                            "Seed Station",
                            onClick --> (_ =>
                              Actions.specializeDryClearingOutpost(OutpostProgression.SeedStation)
                            )
                          ),
                          small("+50% protein · +25% water per Seed Forager"),
                          button(
                            typ := "button",
                            className := "btn btn-outline-primary",
                            "Scout Station",
                            onClick --> (_ =>
                              Actions.specializeDryClearingOutpost(OutpostProgression.ScoutStation)
                            )
                          ),
                          small("−40% search time · 2–3 scouts · 5 water per search"),
                        )
                      )
                  },
                if (expansionVisible)
                  div(
                    className := "row mt-2",
                    actionButton(
                      name = "Expand Dry Clearing Outpost",
                      enabledSignal =
                        nestSignal.combineWith(explorationSignal, resourcesSignal).map {
                          case (n, e, r) => OutpostProgression.canExpand(n, e, r)
                        },
                      costSignal = nestSignal.map(OutpostProgression.expansionCost),
                      bonusSignal = Val(
                        ActionBonus(
                          maxAnts = OutpostProgression.ExpansionHousing,
                          seedForagerSlots = 5
                        )
                      ),
                      descriptionSignal = Val(None),
                      onClickAction = _ => Actions.expandDryClearingOutpost(),
                    ),
                  )
                else emptyNode,
              )
            else
              div(
                className := "row",
                actionButton(
                  name = "Establish Dry Clearing Outpost",
                  enabledSignal = snapshotSignal.map(OutpostProgression.canEstablish),
                  costSignal =
                    antsSignal.map(a => OutpostProgression.establishmentCost(a.lineage)).distinct,
                  bonusSignal = Val(
                    ActionBonus(
                      maxAnts = OutpostProgression.Housing,
                      seedForagerSlots = (OutpostProgression.ForagerSlots / u).toInt
                    )
                  ),
                  descriptionSignal = Val(None),
                  onClickAction = _ => Actions.establishDryClearingOutpost(),
                ),
              ),
          )
        }),
      div(
        className := "col-12 nest-chambers",
        display <-- chambersVisible.map(if (_) "block" else "none"),
        h5(className := "mt-3", "Chambers"),
        div(
          className := "row",
          // Progression order: founding infrastructure, brood maintenance, then specializations.
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Nest.name,
            chamberType = ChamberType.Nest,
            visible = unlocksSignal.map(_.actions.canBuildNestUpgrade).distinct,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Queen.name,
            chamberType = ChamberType.Queen,
            visible = unlocksSignal.map(_.actions.canBuildQueenChamber).distinct,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.FoodStorage.name,
            chamberType = ChamberType.FoodStorage,
            visible = unlocksSignal.map(_.actions.canBuildFoodStorageChamber).distinct,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Nursery.name,
            chamberType = ChamberType.Nursery,
            visible = nurseryUnlockedSignal,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Waste.name,
            chamberType = ChamberType.Waste,
            visible = nestSignal.map(Midden.visible).distinct,
          ),
          // Aphid research is reached through earlier exploration than living water storage.
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Aphid.name,
            chamberType = ChamberType.Aphid,
            visible = upgradesSignal.map(_(UpgradeType.AphidTending).unlocked).distinct,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Replete.name,
            chamberType = ChamberType.Replete,
            visible = upgradesSignal.map(_(UpgradeType.RepleteStorage).unlocked).distinct,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.RoyalNursery.name,
            chamberType = ChamberType.RoyalNursery,
            visible = nestSignal
              .combineWith(upgradesSignal)
              .map { case (nest, upgrades) =>
                nest.royalBroodSlots == 0 && upgrades(UpgradeType.RoyalNurseryPlanning).unlocked
              }
              .distinct,
          ),
        ),
      ),
    )
  }

  private def territoryDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val chosen = Var(10)
    val firstGuardOrder = Var(0)
    val guardsUnlocked = upgradesSignal.map(TerritoryProgression.guardsUnlocked).distinct
    val firstGuardLimit = chosen.signal.map(n => TerritoryProgression.maxGuards(n.toLong)).distinct
    val firstGuards = chosen.signal
      .combineWith(firstGuardOrder.signal, guardsUnlocked)
      .map { case (workers, order, unlocked) =>
        if (unlocked) TerritoryProgression.guardCount(workers.toLong, order) else 0
      }
      .distinct
    val claimPlan = chosen.signal.combineWith(firstGuards)
    val siteSignal = explorationSignal.map(_.territory).distinct
    val workerSignal = antsSignal.map(TerritoryProgression.workers).distinct
    val activeCrew = workerSignal.combineWith(siteSignal.map(_.guards).distinct)

    def guardPicker(label: String, count: Signal[Int], limit: Signal[Int])(
        change: Int => Unit
    ): ReactiveHtmlElement[HTMLDivElement] =
      div(
        className := "territory-guard-controls d-flex align-items-center justify-content-between gap-2 mb-2",
        role := "group",
        aria.label := label,
        span(label),
        div(
          className := "d-flex align-items-center gap-2",
          button(
            className := "btn btn-outline-secondary",
            typ := "button",
            minHeight.px := 44,
            minWidth.px := 44,
            aria.label := "Remove guard",
            "−",
            disabled <-- count.map(_ == 0),
            onClick --> (_ => useSignalValue[Int](count, n => change(n - 1)))
          ),
          b(
            className := "territory-guard-count text-center",
            minWidth.px := 24,
            child.text <-- count.map(_.toString)
          ),
          button(
            className := "btn btn-outline-secondary",
            typ := "button",
            minHeight.px := 44,
            minWidth.px := 44,
            aria.label := "Add guard",
            "+",
            disabled <-- count.combineWith(limit).map { case (n, max) => n >= max },
            onClick --> (_ => useSignalValue[Int](count, n => change(n + 1)))
          ),
        ),
      )

    div(
      className := "territory-card",
      infoHeading(
        "Contested Foraging Ground",
        div(
          div(
            "Hauls arrive after each 60-second contest. Reinforcements use the oldest idle/default workers. Withdrawal abandons the unfinished haul."
          ),
          child.maybe <-- guardsUnlocked.map(unlocked =>
            Option.when(unlocked)(
              div(
                className := "mt-2",
                s"One guard per ${TerritoryProgression.WorkersPerGuard} holders. Guards skip gathering and reduce combat losses. Changes start next round."
              )
            )
          ),
        )
      ),
      child <-- siteSignal.map(_.held).distinct.map {
        case false =>
          div(
            label(forId := "territory-workers", "Workers to hold"),
            div(
              className := "d-flex flex-wrap align-items-center gap-2 my-2",
              input(
                idAttr := "territory-workers",
                className := "form-control",
                typ := "number",
                minAttr := "10",
                maxAttr := "37",
                stepAttr := "1",
                width.px := 110,
                minHeight.px := 44,
                value <-- chosen.signal.map(_.toString),
                onChange.mapToValue --> (value =>
                  chosen.set(TerritoryProgression.target(value.toIntOption.getOrElse(10)))
                )
              ),
              List(10, 20, 37).map(n =>
                button(
                  className := "btn btn-outline-secondary",
                  typ := "button",
                  minHeight.px := 44,
                  n.toString,
                  aria.label := s"Hold with $n workers",
                  onClick --> (_ => chosen.set(n))
                )
              ),
            ),
            child.maybe <-- guardsUnlocked.map(unlocked =>
              Option.when(unlocked)(
                guardPicker("Guards", firstGuards, firstGuardLimit)(firstGuardOrder.set)
              )
            ),
            div(
              className := "territory-risk text-danger small mb-2",
              child.text <-- claimPlan.map { case (workers, guards) =>
                TerritoryProgression.risk(workers.toLong, guards)
              }
            ),
            div(
              className := "territory-rate small mb-1",
              child.text <-- claimPlan.map { case (workers, guards) =>
                s"+${resourceRateStr(TerritoryProgression.rate(workers.toLong, guards))} protein/s"
              }
            ),
            div(
              className := "territory-entry-aging text-danger small mb-1",
              child.text <-- antsSignal.combineWith(currentTickSignal, chosen.signal).map {
                case (ants, tick, amount) =>
                  val due = RaidProgression.overdue(
                    ants.ages.workers,
                    amount.toLong,
                    tick + TerritoryProgression.ContestTicks
                  )
                  if (due == 0L) ""
                  else
                    s"$due selected workers expire before the first contest; aging can add losses."
              }
            ),
            div(
              className := "row",
              actionButton(
                name = "Hold Foraging Ground",
                enabledSignal = chosen.signal
                  .combineWith(antsSignal, resourcesSignal, explorationSignal, unlocksSignal)
                  .map { case (n, ants, resources, exploration, unlocks) =>
                    !exploration.territory.held && unlocks.tabs.exploreTabUnlocked && DiscoveryProgression.ContestedGround
                      .found(exploration) &&
                      ants.availableWorkersCount >= n.toLong * u && resources
                        .hasResources(TerritoryProgression.cost(n))
                  },
                costSignal = chosen.signal.map(TerritoryProgression.cost),
                bonusSignal = Val(ActionBonus.empty),
                descriptionSignal = Val(
                  Some(
                    div(
                      "Oldest home workers are assigned first. Combat casualties are random within the holders. Workers consume ordinary sugar upkeep. Full protein storage does not stop contests or casualties. Hauls are delivered only if workers survive the contest and aging."
                    )
                  )
                ),
                onClickAction = _ =>
                  useSignalValue[(Int, Int)](
                    claimPlan,
                    { case (workers, guards) =>
                      Actions.claimTerritory(workers, guards)
                    }
                  ),
              )
            ),
          )
        case true =>
          div(
            className := "territory-held border rounded p-2",
            div(
              className := "d-flex flex-wrap justify-content-between gap-2",
              b(
                child.text <-- workerSignal.combineWith(siteSignal).map { case (count, site) =>
                  s"$count/${site.targetWorkers} workers holding"
                },
                className <-- workerSignal.combineWith(siteSignal).map { case (count, site) =>
                  if (count < site.targetWorkers) "text-danger" else ""
                }
              ),
              span(child.text <-- siteSignal.combineWith(currentTickSignal).map {
                case (site, tick) =>
                  s"Next contest: ${prettyTimeFromTicks(Math.max(0L, site.nextContestTick - tick))}"
              }),
            ),
            child.maybe <-- guardsUnlocked.map(unlocked =>
              Option.when(unlocked)(
                div(
                  className := "territory-active-guards small text-body-secondary",
                  child.text <-- activeCrew.map { case (workers, guards) =>
                    s"${TerritoryProgression.guardCount(workers, guards)} guarding this round"
                  }
                )
              )
            ),
            div(
              className := "territory-risk text-danger small my-2",
              child.text <-- activeCrew.map { case (workers, guards) =>
                TerritoryProgression.risk(workers, guards)
              }
            ),
            div(
              className := "territory-pending",
              child.text <-- siteSignal
                .map(site => s"Haul: ${prettyNumberStr(site.pendingProtein)} protein")
            ),
            div(
              className := "territory-rate small",
              child.text <-- activeCrew.map { case (workers, guards) =>
                s"+${resourceRateStr(TerritoryProgression.rate(workers, guards))} protein/s"
              }
            ),
            div(
              className := "territory-storage small my-1",
              child.text <-- proteinSignal.map(p =>
                if (p.atCapacity) "Full storage · losses continue"
                else s"Storage room: ${prettyNumberStr(p.freeSpace)} protein"
              ),
              className <-- proteinSignal
                .combineWith(siteSignal, workerSignal, currentTickSignal)
                .map { case (p, site, count, tick) =>
                  val expected =
                    site.pendingProtein + TerritoryProgression.rate(count, site.guards) * Math
                      .max(0L, site.nextContestTick - tick) / TicksPerSecond
                  if (p.freeSpace < expected) "text-danger" else ""
                },
            ),
            div(
              className := "territory-aging text-danger small my-1",
              child.text <-- siteSignal.map { site =>
                val due = site.ages.filter(_.expiresAt <= site.nextContestTick).map(_.count).sum
                if (due == 0L) ""
                else s"$due holders expire by the contest. Aging losses can overlap combat losses."
              }
            ),
            div(
              className := "territory-reinforcements small",
              child.maybe <-- antsSignal.combineWith(siteSignal).map { case (ants, site) =>
                val missing = Math.max(0L, site.targetWorkers - TerritoryProgression.workers(ants))
                Option.when(missing > 0L)(
                  span(
                    "Reinforcements: ",
                    requirementLabel(
                      ActionRequirement(
                        ants.availableWorkersLabel,
                        ants.availableWorkersCount,
                        missing * u
                      )
                    )
                  )
                )
              },
            ),
            div(
              className := "d-flex flex-wrap gap-2 my-2",
              button(
                className := "btn btn-outline-primary",
                typ := "button",
                minHeight.px := 44,
                "Reinforce",
                disabled <-- workerSignal.combineWith(siteSignal, availableWorkersCountSignal).map {
                  case (n, site, available) => n >= site.targetWorkers || available < u
                },
                onClick --> (_ => Actions.reinforceTerritory())
              ),
              button(
                className := "btn btn-outline-danger",
                typ := "button",
                minHeight.px := 44,
                "Withdraw",
                onClick --> (_ => Actions.withdrawTerritory())
              ),
            ),
            child.maybe <-- guardsUnlocked.map(unlocked =>
              Option.when(unlocked) {
                val next = siteSignal.map(_.nextGuards).distinct
                val limit = siteSignal
                  .map(site => TerritoryProgression.maxGuards(site.targetWorkers.toLong))
                  .distinct
                guardPicker("Guards next round", next, limit)(Actions.planTerritoryGuards)
              }
            ),
            label(
              className := "d-flex align-items-center gap-2 my-2",
              minHeight.px := 44,
              input(
                typ := "checkbox",
                className := "form-check-input",
                checked <-- siteSignal.map(_.maintain).distinct,
                onChange.mapToChecked --> (enabled => Actions.maintainTerritory(enabled))
              ),
              "Maintain staffing automatically",
            ),
          )
      },
    )
  }

  private def prestigeDiv: ReactiveHtmlElement[HTMLDivElement] = {
    val step = Var(0)
    val selected = Var(Set.empty[String])
    val acknowledged = Var(false)
    val drySeason = Var(false)
    val planned = snapshotSignal.combineWith(selected.signal, drySeason.signal).map {
      case (data, choices, challenge) => Prestige.plan(data, choices.toSeq, challenge)
    }
    val challengeAvailable = antsSignal.map(a => DrySeasonChallenge.canEnter(a.lineage)).distinct
    def buttonFor(label: String)(action: => Unit) = button(
      typ := "button",
      className := "btn btn-outline-primary",
      minHeight.px := 44,
      label,
      onClick --> (_ => action)
    )
    def advance(next: Int): Unit = { acknowledged.set(false); step.set(next) }
    def row(label: String, value: Signal[String]) = div(
      className := "d-flex flex-wrap justify-content-between gap-2 py-1",
      span(label),
      b(child.text <-- value)
    )
    def rewardRows = div(
      className := "founding-rewards border rounded p-2 my-2",
      row(
        "Generation",
        antsSignal.map(a =>
          s"${a.lineage.foundings.toLong + 1} → ${a.lineage.foundings.toLong + 2}"
        )
      ),
      row("DNA earned", Val(s"+${Prestige.DnaReward}")),
      row(
        "DNA available to spend",
        antsSignal.map(a =>
          s"${a.lineage.dna} + ${Prestige.DnaReward} = ${BigInt(a.lineage.dna) + Prestige.DnaReward}"
        )
      ),
      row(
        "Permanent milestone sugar",
        statisticsData.signal.map(s => s"×${ColonyMilestones.multiplier(s)} (kept)")
      ),
      row(
        "Inherited sugar production",
        antsSignal.map(a =>
          s"+${Prestige.sugarBonus(a.lineage)}% → +${Prestige.sugarBonus(a.lineage) + 10L}%"
        )
      ),
      div(
        className := "small text-body-secondary",
        "Applies to Sugar Collectors and Aphid Tenders. Adds 10 percentage points each founding."
      ),
      row(
        "DNA spent on Understandings",
        selected.signal.map(ids =>
          Prestige.understandings.filter(u => ids(u.id)).map(_.cost).sum.toString
        )
      ),
      row("DNA carried forward", planned.map(_.map(_.dna.toString).getOrElse("Invalid selection"))),
    )
    def resetRows = detailsTag(
      rememberDetails("lineage.reset"),
      className := "founding-reset my-2",
      summaryTag(minHeight.px := 44, "Colony reset: exact values"),
      children <-- snapshotSignal.combineWith(planned).map { case (data, plan) =>
        val fresh = AllData.initial
        val starting = Prestige.startingResources(plan.getOrElse(data.ants.lineage))
        def resource(label: String, before: BasicResource, after: BasicResource) = {
          def stock(r: BasicResource) =
            prettyNumberStr(r.amount) + (if (r.maxAmount == Long.MaxValue) ""
                                         else s"/${prettyNumberStr(r.maxAmount)}")
          label -> s"${stock(before)} → ${stock(after)}"
        }
        val a = data.ants
        val values = Seq(
          resource("Sugar", data.basicResources.sugar, starting.sugar),
          resource("Protein", data.basicResources.protein, fresh.basicResources.protein),
          resource("Water", data.basicResources.water, fresh.basicResources.water),
          resource("Resin", data.basicResources.resin, fresh.basicResources.resin),
          resource("Knowledge", data.basicResources.knowledge, fresh.basicResources.knowledge),
          resource(
            "Tunnelling space",
            data.basicResources.tunnelingSpace,
            fresh.basicResources.tunnelingSpace
          ),
          "Standard workers" -> s"${a.workers / u} → 0",
          "Small workers" -> s"${a.smallWorkers / u} → 0",
          "Scouts" -> s"${a.scouts / u} → 0",
          "Repletes" -> s"${a.repletes / u} → 0",
          "Daughter queens" -> s"${a.daughterQueens / u} → 0",
          "Brood" -> s"${a.eggsAndLarvae.size + a.royalBrood.size} → 0",
          "Aphids" -> s"${data.nestAttributes.aphids} → 0",
          "Explored hexes" -> s"${data.exploration.revealedSites} → 0",
          "Active missions" -> s"${data.exploration.explorationParties.size} → 0",
          "Build queue" -> s"${data.nestAttributes.buildQueue.size} → 0",
          "Egg orders" -> s"${a.queuedEggs} → 0",
          "Resource visibility" -> "Fresh start"
        ) ++
          Seq(
            ChamberType.Nest,
            ChamberType.Queen,
            ChamberType.FoodStorage,
            ChamberType.Nursery,
            ChamberType.Aphid,
            ChamberType.Replete,
            ChamberType.RoyalNursery
          ).map(kind =>
            kind.name -> s"${Construction.level(data.nestAttributes.chambers, kind)} → ${Construction
                .level(fresh.nestAttributes.chambers, kind)}"
          )
        values.map { case (label, number) =>
          div(
            className := "d-flex flex-wrap justify-content-between gap-2 small py-1",
            span(label),
            span(number)
          )
        } :+
          div(
            className := "small mt-2",
            s"Research reset (${data.upgrades.upgrades.count(_._2.unlocked)}): ",
            data.upgrades.upgrades.toSeq
              .filter(_._2.unlocked)
              .sortBy(_._1.title)
              .map(_._1.title)
              .mkString(", ")
          )
      }
    )
    def challengeChoice = div(
      child.maybe <-- challengeAvailable.map(available =>
        Option.when(available)(
          div(
            className := "dry-season-choice border rounded p-2 my-2",
            label(
              className := "d-flex align-items-center gap-2",
              minHeight.px := 44,
              input(
                typ := "checkbox",
                className := "form-check-input",
                checked <-- drySeason.signal,
                onChange.mapToChecked --> (enabled => {
                  drySeason.set(enabled); acknowledged.set(false)
                })
              ),
              b("Found in the Dry Season")
            ),
            div(className := "small", DrySeasonChallenge.description)
          )
        )
      ),
    )
    def challengeStatus = div(
      child.maybe <-- snapshotSignal
        .map { data =>
          Option.when(data.ants.lineage.drySeasonActive)(
            (
              data.nestAttributes.dryClearingOutpost,
              data.ants.repletes / u,
              data.ants.daughterQueens >= u,
              DrySeasonChallenge.earnsReward(data)
            )
          )
        }
        .distinct
        .map(_.map { case (outpost, repletes, queen, reward) =>
          div(
            className := "dry-season-status border rounded p-2 my-2",
            b("Dry Season"),
            div(className := "small", "Dew gathered: −50%"),
            div(
              className := "small",
              if (outpost) "Dry Clearing Outpost established" else "Establish Dry Clearing Outpost"
            ),
            div(
              className := "small",
              s"$repletes/${DrySeasonChallenge.RequiredRepletes} living repletes"
            ),
            div(
              className := "small",
              if (reward && queen)
                s"On founding: +${DrySeasonChallenge.WaterBonus / u} water capacity per replete"
              else if (!queen) "Raise a daughter queen to finish."
              else "Founding now ends the challenge without its reward."
            )
          )
        }),
      child.maybe <-- antsSignal
        .map(_.lineage.drySeasonCompleted)
        .distinct
        .map(done =>
          Option.when(done)(
            div(
              className := "small",
              s"Dry Season completed · +${DrySeasonChallenge.WaterBonus / u} water capacity per replete"
            )
          )
        ),
    )
    def newResearch = detailsTag(
      rememberDetails("lineage.research"),
      className := "understanding-research my-2",
      summaryTag(minHeight.px := 44, "New colony research"),
      div(
        className := "small text-body-secondary my-2",
        "Each needs 3 searches at its outer site. Colony research resets on founding."
      ),
      Seq(UpgradeType.AphidShelters, UpgradeType.TrailLogistics, UpgradeType.CoordinatedExpeditions)
        .map { upgrade =>
          div(
            className := "border-bottom small py-2",
            b(upgrade.title),
            div(upgrade.description),
            div(
              s"${prettyNumberStr(upgrade.cost.sugar)} sugar · ${prettyNumberStr(upgrade.cost.knowledge)} knowledge" +
                (if (upgrade.cost.resin > 0L) s" · ${prettyNumberStr(upgrade.cost.resin)} resin"
                 else "")
            ),
            emptyNode
          )
        }
    )
    def choices = div(
      className := "understanding-choices",
      children <-- antsSignal
        .map(_.lineage)
        .distinct
        .combineWith(selected.signal)
        .map { case (lineage, ids) =>
          val available = lineage.copy(foundings =
            if (lineage.foundings < Int.MaxValue) lineage.foundings + 1 else Int.MaxValue
          )
          val projected = Prestige.projectedPrerequisites(available, ids)
          val options =
            Prestige.understandings.filter(u => !u.owned(lineage) && u.available(projected))
          if (options.isEmpty)
            Seq(
              div(className := "small my-2", "No new Understandings available for this founding.")
            )
          else
            options.map { understanding =>
              div(
                className := "understanding-choice border rounded p-2 my-2",
                dataAttr("understanding") := understanding.id,
                label(
                  className := "d-flex align-items-center gap-2",
                  minHeight.px := 44,
                  input(
                    typ := "checkbox",
                    className := "form-check-input",
                    aria.label := s"Select ${understanding.title}",
                    checked := ids(understanding.id),
                    onChange.mapToChecked --> { checked =>
                      selected.update { old =>
                        if (checked) old + understanding.id
                        else {
                          val remaining = old - understanding.id
                          val projected = Prestige.projectedPrerequisites(available, remaining)
                          remaining.filter(id =>
                            Prestige.understandings
                              .exists(u => u.id == id && u.available(projected))
                          )
                        }
                      }
                    }
                  ),
                  b(understanding.title),
                  span(s"${understanding.cost} DNA")
                ),
                div(className := "small", understanding.effect),
                if (understanding.id == Prestige.BeyondWoodland.id) newResearch else emptyNode
              )
            }
        },
      div(
        className := "small text-body-secondary",
        "Selections apply only on confirmation. You may keep DNA unspent. Existing Understandings are inherited automatically."
      )
    )
    div(
      className := "col-12 lineage-section",
      infoHeading(
        "Queen's lineage",
        div(
          "Understandings are permanent. Colony research resets when the daughter queen founds her colony."
        )
      ),
      row(
        "Current lineage",
        antsSignal.map(a =>
          s"Generation ${a.lineage.foundings.toLong + 1} · ${a.lineage.dna} DNA · +${Prestige
              .sugarBonus(a.lineage)}% sugar"
        )
      ),
      div(
        className := "lineage-queen-status small my-2",
        child.text <-- antsSignal.map(a =>
          if (a.daughterQueens >= u) "Daughter queen: ready"
          else if (a.royalBrood.nonEmpty) "Daughter queen: rearing in Nest"
          else "Daughter queen: raise in Nest"
        )
      ),
      div(
        className := "inherited-understandings small",
        child.text <-- antsSignal.map { a =>
          val known = Prestige.understandings.filter(_.owned(a.lineage)).map(_.title)
          if (known.isEmpty) "Understandings: none yet" else "Inherited: " + known.mkString(", ")
        }.distinct
      ),
      challengeStatus,
      child <-- step.signal.distinct.map {
        case 0 =>
          div(
            className := "founding-step",
            dataAttr("step") := "rewards",
            h5(className := "mt-3", "1. Founding rewards"),
            rewardRows,
            div(
              className := "small",
              "Keep: DNA, Understandings, lifetime statistics, milestones, settings and stored time."
            ),
            div(
              className := "small",
              "Reset: resources, population, brood, chambers, colony research, exploration, discoveries, trails, queues and assignments."
            ),
            resetRows,
            buttonFor("Choose Understandings")(advance(1)),
            children <-- antsSignal.map(_.lineage).distinct.map { lineage =>
              Prestige.understandings.filter(u => !u.owned(lineage) && u.available(lineage)).map {
                understanding =>
                  div(
                    className := "learn-understanding small mt-3",
                    span(s"${understanding.title}: ${understanding.effect} "),
                    button(
                      typ := "button",
                      className := "btn btn-outline-primary",
                      minHeight.px := 44,
                      s"Learn ${understanding.title}",
                      disabled := lineage.dna < understanding.cost,
                      onClick --> (_ => {
                        selected.set(Set.empty); acknowledged.set(false);
                        Actions.purchaseUnderstanding(understanding.id)
                      })
                    ),
                    span(s" ${understanding.cost} DNA")
                  )
              }
            }
          )
        case 1 =>
          div(
            className := "founding-step",
            dataAttr("step") := "understandings",
            h5(className := "mt-3", "2. Choose Understandings"),
            rewardRows,
            choices,
            div(
              className := "text-danger small",
              child.text <-- planned.map(p =>
                if (p.isEmpty) "Not enough DNA for this selection." else ""
              )
            ),
            div(
              className := "d-flex flex-wrap gap-2 mt-2",
              buttonFor("Back to rewards")(advance(0)),
              button(
                typ := "button",
                className := "btn btn-primary",
                minHeight.px := 44,
                "Review founding",
                disabled <-- planned.map(_.isEmpty),
                onClick --> (_ => advance(2))
              )
            )
          )
        case _ =>
          div(
            className := "founding-step",
            dataAttr("step") := "confirmation",
            h5(className := "mt-3", "3. Confirm daughter colony"),
            rewardRows,
            challengeChoice,
            div(
              className := "selected-understandings",
              child.text <-- planned.map(
                _.map(l =>
                  "Inherited Understandings: " + Prestige.understandings
                    .filter(_.owned(l))
                    .map(_.title)
                    .mkString(", ")
                ).getOrElse("Invalid selection")
              )
            ),
            resetRows,
            div(
              className := "alert alert-warning small mt-2",
              "This replaces the current colony. Begin with the founding queen and no workers."
            ),
            label(
              className := "d-flex align-items-center gap-2 my-2",
              minHeight.px := 44,
              input(
                typ := "checkbox",
                className := "form-check-input",
                aria.label := "Confirm colony reset",
                checked <-- acknowledged.signal,
                onChange.mapToChecked --> acknowledged.writer
              ),
              "I understand that this colony will reset."
            ),
            div(
              className := "d-flex flex-wrap gap-2",
              buttonFor("Back to Understandings")(advance(1)),
              button(
                className := "btn btn-primary",
                typ := "button",
                minHeight.px := 44,
                "Found daughter colony",
                disabled <-- snapshotSignal.combineWith(planned, acknowledged.signal).map {
                  case (data, plan, yes) => !yes || plan.isEmpty || !Prestige.canFound(data)
                },
                onClick --> (_ => {
                  if (acknowledged.now()) {
                    Actions.foundDaughterColony(selected.now().toSeq, drySeason.now());
                    selected.set(Set.empty); drySeason.set(false); advance(0)
                  }
                })
              )
            ),
            div(
              className := "text-danger small mt-2",
              child.text <-- antsSignal.map(a =>
                if (a.daughterQueens < u) "Requires 1 ready daughter queen." else ""
              )
            )
          )
      }
    )
  }

  private def royalRearingDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val broodSignal = antsSignal.map(_.royalBrood).distinct
    val nursesSignal = antsSignal
      .combineWith(nestSignal)
      .map { case (ants, nest) =>
        RoyalBroodProgression.nurses(ants, nest)
      }
      .distinct
    val projectedSignal = antsSignal
      .combineWith(
        nestSignal,
        upgradesSignal,
        explorationSignal,
        resourcesSignal,
        statisticsData.signal
      )
      .map { case (ants, nest, upgrades, exploration, resources, stats) =>
        RoyalBroodProgression.forecastResources(ants, nest, upgrades, exploration, resources, stats)
      }
    val statusSignal = antsSignal
      .combineWith(nestSignal, projectedSignal)
      .map { case (ants, nest, resources) =>
        RoyalBroodProgression.status(ants, nest, resources)
      }
      .distinct
    div(
      className := "royal-nursery-ready",
      child <-- antsSignal
        .map(ants => if (ants.royalBrood.nonEmpty) 1 else if (ants.daughterQueens > 0L) 2 else 0)
        .distinct
        .map {
          case 0 =>
            div(
              div(
                className := "royal-staffing small mb-2",
                className("text-danger") <-- nursesSignal.map(_ == 0L),
                child.text <-- nursesSignal
                  .map(n => s"$n/${RoyalBroodProgression.MaxNurses} Royal Nurses")
              ),
              p(
                className := "royal-total-care small mb-1",
                s"Care: ${prettyNumberStr(RoyalBroodProgression.rearingCost.sugar)} sugar · ${prettyNumberStr(
                    RoyalBroodProgression.rearingCost.protein
                  )} protein · ${prettyNumberStr(RoyalBroodProgression.rearingCost.water)} water."
              ),
              div(
                className := "row",
                actionButton(
                  name = "Raise Royal Egg",
                  enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal).map {
                    case (ants, nest, resources) =>
                      RoyalBroodProgression.canStart(ants, nest, resources)
                  },
                  costSignal = Val(RoyalBroodProgression.startCost),
                  bonusSignal = Val(ActionBonus.empty),
                  requirementsSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                    List(
                      ActionRequirement(
                        "colony slots",
                        BroodProgression.availableColonySlots(ants, nest),
                        isCost = true
                      ),
                      ActionRequirement(
                        "royal brood places",
                        RoyalBroodProgression.freeSlots(ants, nest) * u
                      ),
                    )
                  },
                  descriptionSignal = Val(
                    Some(
                      div(
                        "Reserves one colony slot. Nurses use water throughout rearing; larvae also need sugar and protein. Progress is kept when care pauses."
                      )
                    )
                  ),
                  onClickAction = _ => Actions.raiseRoyalEgg(),
                ),
              ),
            )
          case 1 =>
            div(
              className := "royal-brood-card border rounded p-2",
              div(
                className := "d-flex justify-content-between gap-2",
                b(
                  child.text <-- broodSignal
                    .map(_.map(RoyalBroodProgression.stage).getOrElse("Royal brood"))
                ),
                span(
                  child.text <-- broodSignal.map(b =>
                    s"${b.map(_.careUnits * 100L / RoyalBroodProgression.TotalCare).getOrElse(0L)}%"
                  )
                ),
              ),
              div(
                className := "progress my-2",
                role := "progressbar",
                aria.label := "Royal brood development",
                aria.valueMin := 0.0,
                aria.valueMax := 100.0,
                aria.valueNow <-- broodSignal.map(
                  _.map(_.careUnits * 100L / RoyalBroodProgression.TotalCare).getOrElse(0L).toDouble
                ),
                div(
                  className := "progress-bar",
                  role := "progressbar",
                  aria.label := "Royal brood progress",
                  aria.valueMin := 0,
                  aria.valueMax := 100,
                  aria.valueNow <-- broodSignal.map(b =>
                    Math.min(
                      100.0,
                      Math.max(
                        0.0,
                        b.map(_.careUnits * 100.0 / RoyalBroodProgression.TotalCare).getOrElse(0.0)
                      )
                    )
                  ),
                  width <-- broodSignal.map(b =>
                    s"${Math.min(100.0, Math.max(0.0, b.map(_.careUnits * 100.0 / RoyalBroodProgression.TotalCare).getOrElse(0.0)))}%"
                  )
                ),
              ),
              div(
                className := "small",
                child.text <-- nursesSignal
                  .map(n => s"$n/${RoyalBroodProgression.MaxNurses} Royal Nurses")
              ),
              div(
                className := "royal-feeding small my-1",
                child.text <-- broodSignal.combineWith(nursesSignal).map { case (brood, count) =>
                  val cost = brood
                    .map(b =>
                      RoyalBroodProgression
                        .scaledCost(RoyalBroodProgression.unitCost(b), count * TicksPerSecond)
                    )
                    .getOrElse(ActionCost.empty)
                  val rates =
                    List("sugar" -> cost.sugar, "protein" -> cost.protein, "water" -> cost.water)
                      .collect {
                        case (name, amount) if amount > 0L => s"${signedRateStr(-amount)} $name/s"
                      }
                  if (rates.isEmpty) "" else rates.mkString(" · ")
                },
              ),
              activityStatus(statusSignal, "royal-care-status small"),
              div(
                className := "small text-body-secondary my-1",
                child.text <-- broodSignal.combineWith(nursesSignal, statusSignal).map {
                  case (brood, count, status) =>
                    val ticks = brood
                      .filter(_ => status.startsWith("Tending "))
                      .map(RoyalBroodProgression.remainingTicks(_, count))
                      .getOrElse(-1L)
                    if (ticks < 0L) ""
                    else s"~${prettyTimeFromTicks(ticks)} remaining at current staffing"
                },
              ),
              child.maybe <-- statusSignal.map(status =>
                Option.when(status == "Needs Royal Nurses")(
                  button(
                    className := "btn btn-outline-primary me-2 mt-1",
                    minHeight.px := 44,
                    typ := "button",
                    "Assign Royal Nurses",
                    onClick --> (_ => {
                      Option(dom.document.querySelector(".standard-worker-tasks"))
                        .foreach(_.setAttribute("open", ""))
                      Option(dom.document.getElementById("tasks-tab"))
                        .foreach(_.asInstanceOf[dom.html.Element].click())
                    })
                  )
                )
              ),
              button(
                className := "btn btn-outline-primary royal-care-toggle mt-1",
                minHeight.px := 44,
                `type` := "button",
                child.text <-- broodSignal
                  .map(b => if (b.exists(_.paused)) "Resume care" else "Pause care"),
                onClick --> { _ =>
                  useSignalValue[Option[RoyalBrood]](
                    broodSignal,
                    b => Actions.setRoyalCarePaused(!b.exists(_.paused))
                  )
                },
              ),
            )
          case _ =>
            div(
              className := "royal-queen-ready border rounded p-2",
              ResourceIcons.icon(ResourceIcons.Queens),
              b("Daughter queen mature"),
              div(className := "small", "Your daughter queen is ready for the next generation."),
            )
        },
    )
  }

  private def requirementLabel(
      requirement: ActionRequirement,
      compact: Boolean = false
  ): ReactiveHtmlElement[HTMLSpanElement] = {
    val resource = Set("sugar", "protein", "water", "resin", "knowledge", "tunneling space")
      .contains(requirement.label)
    val available =
      if (resource) availableCostStr(requirement.available)
      else prettyNumberStr(requirement.available)
    val required =
      if (resource && requirement.required % u != 0L) availableCostStr(requirement.required)
      else prettyNumberStr(requirement.required)
    span(
      className := "action-cost",
      aria.label := s"${requirement.label}: ${preciseRateStr(requirement.available)} available, ${preciseRateStr(requirement.required)} required",
      title := s"$available / $required ${requirement.label}",
      role := "img",
      ResourceIcons.forLabel(requirement.label).filter(_ => compact).map(ResourceIcons.icon),
      span(
        className := (if (requirement.available < requirement.required) "text-danger"
                      else "text-success"),
        available,
      ),
      "/",
      span(
        className := (if (requirement.maximum.exists(_ < requirement.required)) "text-danger"
                      else ""),
        title := requirement.maximum
          .map(max => s"Storage capacity: ${prettyNumberStr(max)}")
          .getOrElse(""),
        required,
      ),
      span(
        className := (if (compact && ResourceIcons.forLabel(requirement.label).nonEmpty)
                        "visually-hidden"
                      else ""),
        s" ${requirement.label}",
      ),
    )
  }

  private[main] def actionCosts(
      costSignal: Signal[ActionCost],
      requirementsSignal: Signal[List[ActionRequirement]],
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "action-costs small d-flex flex-wrap justify-content-center gap-2 pt-1",
      aria.label := "Action costs",
      children <-- costSignal.combineWith(resourcesSignal, antsSignal, requirementsSignal).map {
        case (cost, resources, ants, requirements) =>
          def costLine(
              required: Long,
              available: Long,
              label: String,
              maximum: Option[Long] = None
          ) =
            Option.when(required > 0)(ActionRequirement(label, available, required, maximum))
          val costs = List(
            costLine(
              cost.protein,
              resources.protein.amount,
              "protein",
              Some(resources.protein.maxAmount)
            ),
            costLine(cost.water, resources.water.amount, "water", Some(resources.water.maxAmount)),
            costLine(cost.resin, resources.resin.amount, "resin", Some(resources.resin.maxAmount)),
            costLine(cost.sugar, resources.sugar.amount, "sugar", Some(resources.sugar.maxAmount)),
            costLine(
              cost.tunnelingSpace,
              resources.tunnelingSpace.amount,
              "tunneling space",
              Some(resources.tunnelingSpace.maxAmount)
            ),
            costLine(cost.knowledge, resources.knowledge.amount, "knowledge"),
            costLine(cost.idleWorkers, ants.availableWorkersCount, ants.availableWorkersLabel),
          ).flatten ++ requirements.filter(_.isCost)
          if (costs.isEmpty) Seq(span(className := "text-body-secondary", "Free"))
          else
            costs.map(cost =>
              requirementLabel(cost, compact = true).amend(className := "text-nowrap")
            )
      },
    )

  private var nextActionId = 0L

  private[main] def actionButton(
      name: String,
      enabledSignal: Signal[Boolean],
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
      onClickAction: Int => Unit,
      topLeftBadgeSignal: Signal[Option[ReactiveHtmlElement[HTMLElement]]] = Val(None),
      topRightBadgeSignal: Signal[Option[ReactiveHtmlElement[HTMLElement]]] = Val(None),
      requirementsSignal: Signal[List[ActionRequirement]] = Val(Nil),
      blockedReasonSignal: Signal[Option[String]] = Val(None),
      displayNameSignal: Option[Signal[String]] = None,
      progressBadge: Option[ReactiveHtmlElement[HTMLSpanElement]] = None,
      showDetails: Boolean = true,
      buttonContent: Option[ReactiveHtmlElement[HTMLSpanElement]] = None,
      accessibleName: Option[String] = None,
  )(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    nextActionId += 1L
    val reasonId = s"action-blocker-$nextActionId"
    val blocker = enabledSignal
      .combineWith(costSignal, resourcesSignal, antsSignal, requirementsSignal, blockedReasonSignal)
      .map { case (enabled, cost, resources, ants, requirements, specific) =>
        ActionBlockers.reason(enabled, cost, resources, ants, requirements, specific)
      }
      .distinct
    div(
      className := "col-6 px-3 py-2 d-grid action-cell",
      button(
        className := "btn btn-primary position-relative",
        className := "fs-4",
        `type` := "button",
        aria.label <-- accessibleName.map(Val(_)).getOrElse(displayNameSignal.getOrElse(Val(name))),
        buttonContent match {
          case Some(content) => content
          case None          => span(child.text <-- displayNameSignal.getOrElse(Val(name)))
        },
        progressBadge.map(badge => badge.amend(idAttr := s"$reasonId-counts")),
        disabled <-- enabledSignal.map(!_),
        aria.describedBy <-- blocker.map(reason =>
          (reason.map(_ => reasonId).toList ++ progressBadge.map(_ => s"$reasonId-counts"))
            .mkString(" ")
        ),
        onClick --> { _ =>
          useSignalValue[Int](modifiersMultiplierSignal, mult => onClickAction(mult))
        },
        child.maybe <--
          topLeftBadgeSignal.map {
            _.map { elem =>
              span(
                className := "position-absolute top-0 start-0 translate-middle badge rounded-pill bg-secondary",
                elem,
              )
            }
          },
        child.maybe <--
          topRightBadgeSignal.map {
            _.map { elem =>
              span(
                className := "position-absolute top-0 start-100 translate-middle badge rounded-pill bg-secondary",
                elem,
              )
            }
          },
      ),
      actionCosts(costSignal, requirementsSignal),
      child.maybe <-- blocker.map(
        _.map(reason =>
          div(
            idAttr := reasonId,
            className := "action-blocked-reason small text-danger text-center",
            className("visually-hidden") := ActionBlockers.isBasicResourceShortage(reason),
            reason
          )
        )
      ),
      Option.when(showDetails)(
        detailsTag(
          className := "action-details",
          rememberDetails("action." + name),
          summaryTag(
            aria.label <-- displayNameSignal.getOrElse(Val(name)).map(n => s"Details for $n"),
            title <-- displayNameSignal.getOrElse(Val(name)).map(n => s"Details for $n"),
            span(
              aria.hidden := true,
              svg.svg(
                svg.viewBox := "0 0 24 24",
                svg.path(svg.d := "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 11v6M12 7h.01")
              ),
            ),
          ),
          actionDetails(
            descriptionSignal,
            costSignal,
            bonusSignal,
            requirementsSignal,
            includeCosts = false
          ),
        )
      ),
    )
  }

  private def createTooltipForActionButton(
      elem: ReactiveHtmlElement[HTMLElement],
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
      requirementsSignal: Signal[List[ActionRequirement]] = Val(Nil),
      includeCosts: Boolean = true,
  ): EventListener[MouseEvent, MouseEvent] =
    setTooltip(
      elem,
      actionDetails(descriptionSignal, costSignal, bonusSignal, requirementsSignal, includeCosts)
    )

  private[main] def actionFact(label: String, text: String): ReactiveHtmlElement[HTMLDivElement] =
    div(className := "action-detail-row", b(s"$label: "), text)

  private def actionDetails(
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
      requirementsSignal: Signal[List[ActionRequirement]] = Val(Nil),
      includeCosts: Boolean = true,
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "p-1",
      child.maybe <-- descriptionSignal.map {
        _.map { descriptionDiv =>
          div(
            className := "text-start action-description",
            maxWidth.px := 350,
            descriptionDiv,
          )
        }
      },
      if (includeCosts) actionCosts(costSignal, requirementsSignal) else emptyNode,
      div(
        className := "d-flex flex-column text-start action-requirements",
        children <-- requirementsSignal.map { requirements =>
          val visible = requirements.filterNot(_.isCost)
          if (visible.isEmpty) Nil
          else
            List(div(className := "small text-muted text-start", "Requires")) ++ visible.map(
              requirement => requirementLabel(requirement)
            )
        },
      ),
      child.maybe <-- costSignal.combineWith(bonusSignal).map { case (cost, bonus) =>
        if (!includeCosts || cost == ActionCost.empty || bonus == ActionBonus.empty)
          None
        else
          Some(hr())
      },
      child <-- bonusSignal.map { bonus =>
        div(
          className := "d-flex flex-column justify-content-center text-start action-result",
          if (bonus != ActionBonus.empty) div(className := "small text-muted", "Result")
          else emptyNode,
          ifGreater0(bonus.sugar)(prettyNumberSimple("+", _, " sugar")),
          ifGreater0(bonus.eggs)(value => s"+${value / u} ${if (value == u) "egg" else "eggs"}"),
          ifGreater0(bonus.maxTunnelingSpace)(
            prettyNumberSimple("+", _, " tunneling space capacity")
          ),
          ifGreater0(bonus.maxSugar)(prettyNumberSimple("+", _, " sugar capacity")),
          ifGreater0(bonus.maxProtein)(prettyNumberSimple("+", _, " protein capacity")),
          ifGreater0(bonus.maxWater)(prettyNumberSimple("+", _, " water capacity")),
          ifGreater0(bonus.maxRefuse)(prettyNumberSimple("+", _, " waste capacity")),
          ifGreater0(bonus.repleteSlots.toLong)(value =>
            s"+$value replete ${if (value == 1) "slot" else "slots"}"
          ),
          ifGreater0(bonus.broodNurseSlots.toLong)(value =>
            s"+$value Brood Nurse ${if (value == 1) "slot" else "slots"}"
          ),
          ifGreater0(bonus.layingIntervalReduction)(ticks =>
            s"−${prettyTimeFromTicks(ticks)} laying time"
          ),
          ifGreater0(bonus.maxEggs)(prettyNumberSimple("+", _, " brood places")),
          ifGreater0(bonus.maxAnts)(prettyNumberSimple("+", _, " colony slots")),
          ifGreater0(bonus.maxCarcasses.toLong)(value =>
            s"+$value carcass ${if (value == 1) "slot" else "slots"}"
          ),
          ifGreater0(bonus.aphidSlots.toLong)(value => s"+$value aphid places"),
          ifGreater0(bonus.seedForagerSlots.toLong)(value =>
            s"+$value Seed Forager ${if (value == 1) "slot" else "slots"}"
          ),
          ifGreater0(bonus.royalBroodSlots.toLong)(value =>
            s"+$value royal brood ${if (value == 1) "slot" else "slots"}"
          ),
        )
      },
    )

  private def tasksDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "jobs-panel d-grid gap-2",
      actionAmountControl("tasks-action-amount", tasksMultiplier),
      child.maybe <-- upgradesSignal.map(_(UpgradeType.DefaultWorkers).unlocked).distinct.map {
        unlocked =>
          Option.when(unlocked) {
            div(
              className := "default-workers",
              label(forId := "default-worker-task", "Default task"),
              select(
                idAttr := "default-worker-task",
                className := "form-select",
                children <-- antsSignal
                  .map(ants => (ants.defaultTaskChoices, ants.effectiveDefaultTask))
                  .distinct
                  .map { case (choices, current) =>
                    option(value := "idle", selected := current.isEmpty, "Keep idle") +:
                      choices.map(task =>
                        option(
                          value := task.toString,
                          selected := current.contains(task),
                          task.name
                        )
                      )
                  },
                onChange.mapToValue --> { selected =>
                  useSignalValue[AntsData](
                    antsSignal,
                    ants =>
                      Actions
                        .selectDefaultTask(ants.defaultTaskChoices.find(_.toString == selected))
                  )
                },
              ),
            )
          }
      },
      smallWorkerTasks,
      child.maybe <-- antsSignal
        .map(_.standardWorkersUnlocked)
        .distinct
        .map(unlocked =>
          Option.when(unlocked)(
            detailsTag(
              className := "standard-worker-tasks border rounded p-2",
              rememberDetails("tasks.standard-workers", defaultOpen = true),
              summaryTag(
                className := "standard-workers-summary",
                span(
                  className := "worker-summary-title",
                  ResourceIcons.icon(ResourceIcons.Workers),
                  "Workers · ",
                  child.text <-- antsSignal.map(a => prettyNumberStr(a.workers)).distinct
                ),
                child.maybe <-- antsSignal
                  .map(_.idleWorkersCount)
                  .distinct
                  .map(idle =>
                    Option.when(idle > 0L)(
                      span(className := "idle-worker-summary", "Idle ", b(prettyNumberStr(idle)))
                    )
                  )
              ),
              div(
                className := "standard-worker-jobs",
                children <-- unlockedAntTasks.map(
                  _.sortBy(task => (if (task.showButtons) 0 else 1, task.uiOrder))
                    .map(task => taskDiv(task))
                )
              ),
              StaffingView.workers,
            )
          )
        ),
      StaffingView.otherCastes,
    )

  private def smallWorkerTasks: ReactiveHtmlElement[HTMLDivElement] = div(
    display <-- antsSignal.map(ants => if (ants.smallWorkers > 0L) "block" else "none").distinct,
    child.maybe <-- antsSignal.map(_.smallWorkers > 0L).distinct.map { alive =>
      Option.when(alive) {
        detailsTag(
          className := "small-worker-tasks border rounded p-2",
          rememberDetails("tasks.small-workers", defaultOpen = true),
          summaryTag(
            className := "small-workers-summary",
            minHeight.px := 44,
            ResourceIcons.icon(ResourceIcons.SmallWorkers),
            "Small workers · ",
            child.text <-- antsSignal.map(ants => prettyNumberStr(ants.smallWorkers)).distinct
          ),
          div(
            className := "small-collector-summary",
            div(
              child.text <-- antsSignal
                .map(ants => s"Sugar Collector (${ants.smallCollectors / u})")
            ),
            jobRate(
              ResourceIcons.Sugar,
              "sugar",
              antsSignal.combineWith(upgradesSignal, statisticsData.signal).map {
                case (ants, upgrades, stats) =>
                  ColonyMilestones.sugar(Foraging.perCollector(ants, upgrades, small = true), stats)
              }
            )
          ),
          List(AntTask.Tunneler, AntTask.Scavenger, AntTask.BroodNurse).map { task =>
            div(
              className := "small-worker-job task-row row",
              display <-- antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                if (
                  task == AntTask.Scavenger && !ants.standardWorkersUnlocked || task == AntTask.BroodNurse && nest.chambers.nurseryChamber.level == 0
                ) "none"
                else "flex"
              },
              div(
                className := "col-6 job-label",
                div(child.text <-- antsSignal.map { ants =>
                  val count =
                    if (task == AntTask.Tunneler) ants.smallTunnelers
                    else if (task == AntTask.Scavenger) ants.smallScavengers
                    else ants.smallNurses
                  s"${task.name}: ${count / u}"
                }),
                task match {
                  case AntTask.Tunneler =>
                    jobRate(
                      ResourceIcons.Tunneling,
                      "space",
                      upgradesSignal
                        .map(ColonyDevelopment.tunnelingPerWorkerTick(_) * TicksPerSecond / 2L)
                    )
                  case AntTask.Scavenger =>
                    jobRate(
                      ResourceIcons.Protein,
                      "protein",
                      upgradesSignal.map(upgrades =>
                        balance.UpgradeBalance
                          .production(50L, "scrapsGatherers", upgrades) * TicksPerSecond
                      )
                    )
                  case AntTask.BroodNurse =>
                    broodCareBadge(antsSignal.map(_.smallNurses), small = true)
                  case _ => emptyNode
                }
              ),
              div(
                className := "task-controls",
                button(
                  className := "btn btn-primary m-1",
                  typ := "button",
                  minHeight.px := 44,
                  minWidth.px := 44,
                  "−",
                  aria.label := s"Remove small ${task.name}",
                  disabled <-- antsSignal.map { ants =>
                    (if (task == AntTask.Tunneler) ants.smallTunnelers
                     else if (task == AntTask.Scavenger) ants.smallScavengers
                     else ants.smallNurses) == 0L
                  },
                  onClick --> (event =>
                    Actions.assignSmallWorker(task, -taskMultiplier(event).toLong * u)
                  )
                ),
                button(
                  className := "btn btn-primary m-1",
                  typ := "button",
                  minHeight.px := 44,
                  minWidth.px := 44,
                  "+",
                  aria.label := s"Assign small ${task.name}",
                  disabled <-- antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                    val current =
                      if (task == AntTask.Tunneler) ants.smallTunnelers
                      else if (task == AntTask.Scavenger) ants.smallScavengers
                      else ants.smallNurses
                    val max =
                      if (task == AntTask.Tunneler) Long.MaxValue
                      else DefaultWorkerAssignment.capacity(task, nest) - ants.taskWorkers(task)
                    ants.smallCollectors < u || current >= max
                  },
                  onClick --> (event =>
                    Actions.assignSmallWorker(task, taskMultiplier(event).toLong * u)
                  )
                )
              ),
            )
          },
        )
      }
    },
  )

  private def jobRate(
      icon: ResourceIcons.ResourceIcon,
      resource: String,
      rate: Signal[Long],
      scope: String = "per worker"
  ): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "job-rate small text-body-secondary",
      role := "img",
      title <-- rate.map(n => s"${preciseRateStr(n)} $resource/s $scope").distinct,
      aria.label <-- rate.map(n => s"${preciseRateStr(n)} $resource/s $scope").distinct,
      ResourceIcons.icon(icon),
      child.text <-- rate.map(n => s"${signedRateStr(n, compact = false)}/s").distinct
    )

  private def broodCareBadge(
      workers: Signal[Long],
      small: Boolean
  ): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "brood-care-badge",
      child.maybe <-- workers
        .combineWith(upgradesSignal)
        .map { case (count, upgrades) =>
          BroodProgression.nurseContribution(count, upgrades, small)
        }
        .distinct
        .map(care =>
          Option.when(care > 0L)(
            span(
              className := "job-rate small text-body-secondary",
              role := "img",
              aria.label := s"${prettyNumberStr(care)} extra brood care units",
              title := "Extra brood care shared across developing brood",
              ResourceIcons.icon(ResourceIcons.Eggs),
              s"+${prettyNumberStr(care)} care"
            )
          )
        )
    )

  private def taskMultiplier(event: MouseEvent): Int =
    if (window.innerWidth <= 767) tasksMultiplier.now() else getMouseEventMultiplier(event)

  private def taskDiv(
      antTask: AntTask,
      idSuffix: String = ""
  ): ReactiveHtmlElement[HTMLDivElement] = {
    val amountSignal: Signal[Long] =
      antsSignal.map(_.tasks.find(_._1 == antTask).map(_._2).getOrElse(0L)).distinct
    val id = s"task-${antTask.toString.toLowerCase}$idSuffix"

    val assignmentBlocker = antsSignal
      .combineWith(nestSignal)
      .map { case (ants, nest) => ActionBlockers.assignment(ants, nest, antTask) }
      .distinct

    val perWorker = nestSignal
      .combineWith(antsSignal, upgradesSignal)
      .map { case (nest, ants, upgrades) =>
        (upgrades, OutpostProgression.specialization(nest, ants.lineage))
      }
      .distinct

    def shortage(icon: ResourceIcons.ResourceIcon, reason: String, missing: Signal[Boolean]) =
      child.maybe <-- missing.distinct.map(on =>
        Option.when(on)(
          span(
            className := "job-shortage text-danger",
            title := reason,
            aria.label := reason,
            role := "img",
            ResourceIcons.icon(icon),
            "!"
          )
        )
      )

    def rates = antTask match {
      case AntTask.SugarCollector =>
        val input = antsSignal.combineWith(upgradesSignal, statisticsData.signal)
        span(
          className := "collector-rates",
          jobRate(
            ResourceIcons.Sugar,
            "sugar",
            input.map { case (ants, upgrades, stats) =>
              ColonyMilestones.sugar(Foraging.nextStandardPerSecond(ants, upgrades), stats)
            },
            scope = "from one more standard collector"
          ),
          span(
            className := "collector-total small text-body-secondary",
            className("invisible") <-- amountSignal.map(_ == 0L),
            "Total: ",
            ResourceIcons.icon(ResourceIcons.Sugar),
            child.text <-- input.map { case (ants, upgrades, stats) =>
              val collectors = ants.taskWorkers(AntTask.SugarCollector) / u
              val total = ColonyMilestones.sugar(
                Foraging.productionPerTick(collectors, upgrades, ants.lineage),
                stats
              )
              s"${signedRateStr(total * TicksPerSecond)}/s"
            }.distinct
          )
        )
      case AntTask.Tunneler =>
        jobRate(
          ResourceIcons.Tunneling,
          "space",
          upgradesSignal.map(ColonyDevelopment.tunnelingPerWorkerTick(_) * TicksPerSecond)
        )
      case AntTask.Scavenger =>
        jobRate(
          ResourceIcons.Protein,
          "protein",
          upgradesSignal.map(upgrades =>
            balance.UpgradeBalance.production(100L, "scrapsGatherers", upgrades) * TicksPerSecond
          )
        )
      case AntTask.BroodNurse => broodCareBadge(amountSignal, small = false)
      case AntTask.WaterCollector =>
        jobRate(
          ResourceIcons.Water,
          "water",
          antsSignal.combineWith(upgradesSignal).map { case (ants, upgrades) =>
            OutpostProgression.dewPerWorkerSecond(ants.lineage, upgrades)
          }
        )
      case AntTask.AphidTender =>
        span(
          jobRate(
            ResourceIcons.Sugar,
            "sugar",
            antsSignal.combineWith(statisticsData.signal, upgradesSignal).map {
              case (ants, stats, upgrades) =>
                ColonyMilestones
                  .sugar(
                    Prestige.sugar(AphidHusbandry.sugarPerWorkerSecond(upgrades), ants.lineage),
                    stats
                  )
            }
          ),
          jobRate(ResourceIcons.Protein, "protein", Val(-100L)),
          small(
            className := "job-rate",
            child.text <-- nestSignal.map(n => s"${n.aphids}/${AphidHusbandry.housing(n)} aphids")
          ),
          shortage(
            ResourceIcons.Protein,
            "Needs protein",
            proteinSignal.combineWith(amountSignal).map { case (protein, amount) =>
              protein.amount == 0L && amount > 0L
            }
          )
        )
      case AntTask.SeedForager =>
        span(
          jobRate(
            ResourceIcons.Protein,
            "protein",
            perWorker.map { case (upgrades, station) =>
              OutpostProgression.proteinPerWorkerSecond(upgrades, station)
            }
          ),
          jobRate(
            ResourceIcons.Water,
            "water",
            perWorker.map { case (upgrades, station) =>
              -OutpostProgression.waterPerWorkerSecond(upgrades, station)
            }
          ),
          shortage(
            ResourceIcons.Water,
            "Needs water",
            resourcesSignal.combineWith(amountSignal).map { case (resources, amount) =>
              amount > 0L && !resources.protein.atCapacity && resources.water.amount == 0L
            }
          )
        )
      case AntTask.RefuseCarrier =>
        small(className := "job-rate", s"−${Midden.ClearPerSecond / u} waste/s")
      case _ => emptyNode
    }

    div(
      idAttr := id,
      className := "row task-row",
      div(
        className := "col-6 job-label",
        div(
          antTask.name,
          nbsp,
          "(",
          child.text <-- amountSignal.combineWith(nestSignal, antsSignal).map { case (amount, nest, ants) =>
            if (DefaultWorkerAssignment.capacity(antTask, nest) != Long.MaxValue)
              s"${amount / u}/${DefaultWorkerAssignment.availableCapacity(ants, antTask, nest) / u}"
            else prettyNumberStr(amount)
          },
          ")"
        ),
        rates
      ),
      div(
        className := "col-2 task-controls",
        when(antTask.showButtons)(
          button(
            className := "btn btn-primary",
            className := "m-1",
            cursor.pointer,
            aria.label := s"Remove ${antTask.name}",
            span(aria.hidden := true, "−"),
            disabled <-- amountSignal.combineWith(antsSignal).map { case (amount, ants) =>
              amount == 0 || ants.effectiveDefaultTask.contains(antTask)
            },
            onClick --> { mouseEvent =>
              Actions.reduceAntTask(antTask, taskMultiplier(mouseEvent) * u)
            }
          ),
        ),
        when(antTask.showButtons)(
          button(
            className := "btn btn-primary",
            className := "m-1",
            cursor.pointer,
            aria.label := s"Assign ${antTask.name}",
            span(aria.hidden := true, "+"),
            disabled <-- assignmentBlocker.map(_.nonEmpty),
            title <-- assignmentBlocker.map(_.getOrElse(s"Assign ${antTask.name}")),
            onClick --> { mouseEvent =>
              Actions.incrementAntTask(antTask, taskMultiplier(mouseEvent) * u)
            }
          ),
        ),
      ),
    )
  }

  private def discoveriesDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "discoveries-panel",
      children <-- explorationSignal
        .map { data =>
          val found = DiscoveryProgression.all.filter(_.found(data))
          val surveys = DiscoveryProgression.all
            .filterNot(_.found(data))
            .filter(discovery => ExplorationMap.revealed(data, discovery.destination))
            .map { discovery =>
              (
                discovery.destination,
                ExplorationMap.site(discovery.destination).name,
                discovery.progress(data),
                discovery.trips
              )
            }
          (found, surveys)
        }
        .distinct
        .map { case (found, surveys) =>
          List(
            div(
              h5("Surveys"),
              if (surveys.isEmpty) p("All known areas surveyed.")
              else
                div(surveys.map { case (id, name, progress, trips) =>
                  div(
                    className := "card p-2 my-2 survey-entry",
                    dataAttr("destination") := id,
                    b(name),
                    span(className := "destination-survey", s"Survey $progress / $trips")
                  )
                })
            ),
            div(
              className := "exploration-discoveries",
              h5("Discoveries"),
              if (found.isEmpty) p("No discoveries yet.")
              else
                div(
                  found.map(discovery =>
                    div(
                      className := "card p-2 my-2 discovery-entry",
                      infoHeading(discovery.title, div(discovery.description), minor = true)
                    )
                  )
                ),
            ),
          )
        },
    )

}
