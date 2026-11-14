package pt.rcmartins.antidle.main

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.modifiers.EventListener
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.querki.jquery
import org.scalajs.dom
import org.scalajs.dom.{svg => _, _}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.TickCalculator.prettyResourceEstDiv
import pt.rcmartins.antidle.game.UINumbersUtils._
import pt.rcmartins.antidle.game.UIUtils._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.game.saves.SaveLoad
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.Chamber.ChamberType
import pt.rcmartins.antidle.model.Unlocks.ActionUnlocks
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model._

import scala.scalajs.concurrent.JSExecutionContext.queue
import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps

object MainForm {

  private val detailsTag = htmlTag("details")
  private val summaryTag = htmlTag("summary")

  private def infoDetails(label: String, content: ReactiveHtmlElement[HTMLDivElement]): ReactiveHtmlElement[HTMLElement] =
    detailsTag(
      className := "info-details",
      summaryTag(className := "info-summary", aria.label := s"About $label", title := s"About $label",
        svg.svg(aria.hidden := true, svg.viewBox := "0 0 24 24",
          svg.path(svg.d := "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 11v6M12 7h.01")),
      ),
      div(className := "info-content", content),
    )

  private def infoHeading(label: String, content: ReactiveHtmlElement[HTMLDivElement], minor: Boolean = false): ReactiveHtmlElement[HTMLDivElement] =
    div(className := "info-heading", if (minor) h6(label) else h5(label), infoDetails(label, content))

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
  private val broodMultiplier = Var(1)
  private val tasksMultiplier = Var(1)

  private val modifiersMultiplierSignal: Signal[Int] =
    ctrlPressedVar.signal
      .combineWith(shiftPressedVar.signal, broodMultiplier.signal, windowWidthVar.signal)
      .map { case (ctrl, shift, mobile, width) =>
        if (width <= 767) mobile else getMouseEventMultiplier(ctrl, shift)
      }

  private val lineageVisibleSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
    nest.royalBroodSlots > 0 || ants.daughterQueens > 0L || ants.lineage.foundings > 0
  }.distinct

  private val ResourcesWidth = 500
  private val NestWidth = 800
  private val MessagesWidth = 575

  private val windowWidthLimit: Long =
    ResourcesWidth + NestWidth + MessagesWidth

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

    div(
      className := "antidle-app",
      topBarDiv,
      div(
        className := "m-2 game-layout",
        className := "d-flex justify-content-start",
        className := "fs-4",
        className("flex-column") <-- windowWidthVar.signal.map(_ < windowWidthLimit),
        div(
          className := "resources-column",
          resourcesDiv,
        ),
        mainTabsDiv,
        div(
          className := "activity-column",
          child.maybe <-- ifUnlockedOpt(buildQueueUnlockedSignal)(buildQueueDiv),
          messagesDiv,
        ),
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

  private def topBarDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "flex-column game-topbar",
      div(
        className := "m-2",
        div(
          className := "w-100",
          height.px := 26,
          className := "d-flex justify-content-between align-items-center",
          div(
            "Ant Idle"
          ),
          div(
            className := "d-flex justify-content-between align-items-center",
            timeDiv,
            div(
              className := "topbar-spacer",
              width.px := 75,
              nbsp,
            ),
            catchUpDiv,
          ),
          div(
            nbsp
          ),
        ),
      ),
      hr(
        className := "w-100",
        className := "m-0",
      ),
    )

  private def timeDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(
      child <--
        currentTickSignal.map { currentTick =>
          val years = currentTick / TicksPerYear
          val season: Int = ((currentTick - TicksPerYear * years) / TicksPerSeason).toInt
          val day = (currentTick % TicksPerSeason) / TicksPerDay

          span("Year ", years, " - ", Constants.seasonStr(season), ", day ", day + 1)
        }
    )

  private def catchUpDiv: ReactiveHtmlElement[HTMLDivElement] = {
    val isBehind: Signal[Boolean] =
      currentTickSignal
        .combineWith(targetTickSignal)
        .map { case (currentTick, targetTick) =>
          targetTick - currentTick > 0
        }
        .distinct

    div(
      child <--
        isBehind.map {
          case false =>
            span()
          case true =>
            span(
              "Catch up: [",
              child.text <--
                currentTickSignal.combineWith(targetTickSignal).map {
                  case (currentTick, targetTick) => prettyTimeFromTicks(targetTick - currentTick)
                },
              "] ",
              button(
                className := "btn btn-sm py-0",
                className("btn-success active") <-- Utils.useCatchupTicksVar,
                className("btn-secondary") <-- Utils.useCatchupTicksVar.signal.map(!_),
                `type` := "button",
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
                "Tasks",
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
            li(className := "nav-item",
              a(className := "nav-link", idAttr := "territory-tab", dataAttr("bs-toggle") := "tab",
                href := "#territoryContent", role := "tab", "Territory")),
          ),
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            li(className := "nav-item",
              a(className := "nav-link", idAttr := "discoveries-tab", dataAttr("bs-toggle") := "tab",
                href := "#discoveriesContent", role := "tab", "Discoveries")),
          ),
          child.maybe <-- ifUnlockedOpt(lineageVisibleSignal)(
            li(className := "nav-item",
              a(className := "nav-link", idAttr := "lineage-tab", dataAttr("bs-toggle") := "tab",
                href := "#lineageContent", role := "tab", "Lineage")),
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
              upgradesDiv,
            )
          ),
          child.maybe <-- ifUnlockedOpt(exploreTabUnlockedSignal)(
            div(
              className := "tab-pane",
              idAttr := "exploreContent",
              role := "tabpanel",
              exploreDiv,
            )
          ),
          child.maybe <-- ifUnlockedOpt(territoryVisibleSignal)(
            div(className := "tab-pane", idAttr := "territoryContent", role := "tabpanel", territoryDiv),
          ),
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            div(className := "tab-pane", idAttr := "discoveriesContent", role := "tabpanel", discoveriesDiv),
          ),
          child.maybe <-- ifUnlockedOpt(lineageVisibleSignal)(
            div(className := "tab-pane", idAttr := "lineageContent", role := "tabpanel", prestigeDiv),
          ),
          div(
            className := "tab-pane",
            idAttr := "settingsContent",
            role := "tabpanel",
            settingsDiv,
          ),
        )
      )
    )

  private def statisticsDiv: ReactiveHtmlElement[HTMLElement] =
    detailsTag(
      className := "col-12 colony-statistics mb-3",
      summaryTag("Colony statistics"),
      small("Collected resources exclude overflow discarded at full storage."),
      children <-- statisticsData.signal.combineWith(unlocksSignal, explorationSignal).map { case (stats, unlocks, exploration) =>
        val rows = List(
          Some("Sugar collected" -> prettyNumberStr(stats.sugarU.thisRun)),
          Option.when(unlocks.resources.showTunnelingSpace)("Tunneling space dug" -> prettyNumberStr(stats.tunnelingSpaceU.thisRun)),
          Option.when(exploration.proteinDiscovered)("Protein collected" -> prettyNumberStr(stats.proteinU.thisRun)),
          Option.when(unlocks.resources.showWater)("Water collected" -> prettyNumberStr(stats.waterU.thisRun)),
          Option.when(unlocks.resources.showResin)("Resin collected" -> prettyNumberStr(stats.resinU.thisRun)),
          Option.when(unlocks.resources.showKnowledge)("Knowledge earned" -> prettyNumberStr(stats.knowledgeU.thisRun)),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Expeditions launched" -> stats.explorationsCount.thisRun.toString),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Expeditions completed" -> stats.explorationsCompleted.thisRun.toString),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Workers sent" -> prettyNumberStr(stats.explorationsWorkersSentU.thisRun)),
          Option.when(unlocks.resources.showEggs)("Eggs raised" -> stats.eggsCount.thisRun.toString),
          Option.when(unlocks.resources.showSmallWorkers)("Small workers matured" -> stats.smallWorkersMatured.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Workers matured" -> stats.workersMatured.thisRun.toString),
          Option.when(unlocks.resources.showScouts)("Scouts matured" -> stats.scoutsMatured.thisRun.toString),
          Option.when(unlocks.resources.showDaughterQueens)("Royal eggs raised" -> stats.royalEggsRaised.thisRun.toString),
          Option.when(unlocks.resources.showDaughterQueens)("Daughter queens matured" -> stats.daughterQueensMatured.thisRun.toString),
          Option.when(unlocks.resources.showRepletes)("Repletes promoted" -> stats.repletesPromoted.thisRun.toString),
          Option.when(unlocks.resources.showRepletes)("Repletes lost to aging" -> stats.repletesLostToAging.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Peak workers" -> stats.maxAliveWorkers.thisRun.toString),
          Option.when(exploration.proteinDiscovered)("Carcasses retrieved" -> stats.carcassesRetrieved.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Aging losses" -> (stats.workersLostToAging.thisRun.toLong + stats.scoutsLostToAging.thisRun + stats.repletesLostToAging.thisRun).toString),
          Option.when(stats.territoryDeaths.total > 0)("Territory combat losses" -> stats.territoryDeaths.thisRun.toString),
          Option.when(stats.territoryContests.total > 0)("Territory contests" -> stats.territoryContests.thisRun.toString),
          Option.when(stats.raidDeaths.total > 0)("Raid losses" -> stats.raidDeaths.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Starvation losses" -> stats.starvationDeaths.thisRun.toString),
        ).flatten
        rows.map { case (label, value) =>
          div(className := "statistic-row d-flex flex-wrap justify-content-between gap-3 py-1", span(label), b(value))
        }
      },
    )

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
      statisticsDiv,
      settingsButton(
        "Manual Save",
        () =>
          actionUpdater.writer.onNext(
            _.tap(
              SaveLoad
                .saveToLocalStorage(_)
                .foreach(_ => currentGlobalAlert.set(Some("Game Saved!")))
            )
          )
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
            .foreach(SaveLoad.reloadDataFromLoadedSave(_))
      ),
      settingsButton(
        "Export",
        () =>
          actionUpdater.writer.onNext {
            _.tap { allData =>
              val dataStr = SaveLoad.saveString(allData)
              Option(dom.window.navigator.clipboard)
                .foreach(
                  _.writeText(dataStr).toFuture.map(_ =>
                    currentGlobalAlert.set(Some("Exported to clipboard!"))
                  )(queue)
                )
            }
          },
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
    def buildTaskName(buildTask: BuildTask, index: Int): ReactiveHtmlElement[HTMLDivElement] =
      div(
        className := "queued-building border-bottom pb-2 mb-2",
        span(
          className := "build-task",
          role := "button", tabIndex := 0,
          title := "Select to cancel this planned upgrade",
          aria.label := s"Cancel ${buildTask.chamberType.name}",
          buildTask.chamberType.name,
          " → ", child.text <-- nestSignal.map(nest =>
            Construction.level(nest.chambers, buildTask.chamberType) + nest.buildQueue.take(index + 1).count(_.chamberType == buildTask.chamberType)),
          " [", if (index == 0) TickCalculator.calculateTicksToResources(buildTask.actionCost) else span("Queued"), "] ×",
          onClick --> { _ => Actions.removeBuildTask(index) },
          onKeyDown --> { event =>
            if (event.key == "Enter" || event.key == " ") {
              event.preventDefault()
              Actions.removeBuildTask(index)
            }
          },
        ).amendThis(elem => createTooltipForActionButton(
          elem = elem, descriptionSignal = Val(None), costSignal = Val(buildTask.actionCost),
          bonusSignal = Actions.getActionBonus(buildTask.chamberType),
        )),
        actionCosts(Val(buildTask.actionCost), Val(Nil)),
      )

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
              span(nbsp)
            case seq =>
              div(seq.zipWithIndex.map { case (buildTask, index) =>
                buildTaskName(buildTask, index)
              })
          }
        )
      )
    )
  }

  private def messagesDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "m-1",
      className := "card game-panel",
      className := "fs-4",
      width.px := MessagesWidth,
      maxWidth.px := MessagesWidth,
      div(
        className := "card-header",
        "Messages",
      ),
      div(
        className := "card-body",
        className := "fs-5",
        div(
          className := "d-grid gap-2",
          child.maybe <-- messagesSignal.signal.map(_.headOption.map(text => span(text))),
          children <--
            messagesSignal.map {
              _.drop(1).map(text => div(opacity := 0.5, text))
            }
        )
      )
    )

  private def resourcesDiv: Modifier[ReactiveHtmlElement[HTMLDivElement]] =
    div(
      className := "m-1",
      className := "card game-panel",
      className := "fs-5",
      width.px := ResourcesWidth,
      maxWidth.px := ResourcesWidth,
      div(
        className := "card-header",
        "Resources",
      ),
      div(
        className := "card-body",
        child.maybe <-- createResourceDiv(
          name = "Sugar",
          netRateSignal = Some(TickCalculator.sugarTickGain.sum),
          resourceSignal = sugarSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.sugarTickGain, sugarSignal)),
          showSignal = Val(true),
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
          netRateSignal = Some(TickCalculator.proteinTickGain.sum),
          resourceSignal = proteinSignal,
          resourceEstimationSignal = Val(prettyResourceEstDiv(TickCalculator.proteinTickGain, proteinSignal)),
          showSignal = proteinVisibleSignal,
        ),
        child.maybe <-- createResourceDiv(
          name = "Water",
          netRateSignal = Some(TickCalculator.waterTickGain.sum),
          resourceSignal = waterSignal,
          resourceEstimationSignal = Val(div(prettyResourceEstDiv(TickCalculator.waterTickGain, waterSignal),
            child.maybe <-- waterSignal.map(w => Option.when(w.amount > w.maxAmount)(
              div(className := "small", "Collection paused", title := "Stored water is retained above capacity. Collect again once there is free space."))))),
          showSignal = unlocksSignal.map(_.resources.showWater),
          tooltipOpt = Some(div("Water supplies gathered from discovered sources in the dry area.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Resin",
          netRateSignal = Some(TickCalculator.resinTickGain.sum),
          resourceSignal = resinSignal,
          resourceEstimationSignal = Val(prettyResourceEstDiv(TickCalculator.resinTickGain, resinSignal)),
          showSignal = unlocksSignal.map(_.resources.showResin),
          tooltipOpt = Some(div("Collected from woodland resin sources to line protected brood chambers.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Knowledge",
          resourceSignal = knowledgeSignal,
          resourceEstimationSignal =
            Val(div()),
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
                          ResourceIcons.icon(ResourceIcons.forBrood(antBlood)),
                          b(antBlood.name + ": "),
                          child <-- currentTickSignal.combineWith(upgradesSignal).map { case (currentTick, upgrades) =>
                            antBlood match {
                              case b: AntBrood.Developing => s"${b.care * 100L / b.totalCare}% developed"
                              case _ => prettyTimeFromTicks(Math.max(0L, antBlood.initialTick + ColonyDevelopment.growthTicks(antBlood, upgrades) - currentTick))
                            }
                          },
                          ""
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
          tooltipOpt = Some(div("Small founding workers: 50% task output and upkeep; live 30–40 simulated minutes.")),
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
            BasicResource(ants.repletes, nest.chambers.repleteChamber.level.toLong * u)
          },
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showRepletes),
          tooltipOpt = Some(div("Promoted workers store 100 extra water each and consume normal ant sugar upkeep.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Daughter queens",
          resourceSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
            BasicResource(ants.daughterQueens, nest.royalBroodSlots.toLong * u)
          },
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showDaughterQueens),
          tooltipOpt = Some(div("Mature daughters consume normal ant sugar upkeep and occupy a royal brood slot.")),
        ),
        child.maybe <-- createResourceDiv(
          name = "Scouts",
          resourceSignal = scoutResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showScouts),
        ),
      )
    )

  private def createResourceDiv(
      name: String,
      resourceSignal: Signal[BasicResource],
      resourceEstimationSignal: Signal[ReactiveHtmlElement[HTMLDivElement]],
      showSignal: Signal[Boolean],
      tooltipOpt: Option[ReactiveHtmlElement[HTMLDivElement]] = None,
      netRateSignal: Option[Signal[Long]] = None,
  ): Signal[Option[ReactiveHtmlElement[HTMLDivElement]]] = {
    val amountSignal: Signal[Long] = resourceSignal.map(_.amount).distinct
    val maxAmountSignal: Signal[Long] = resourceSignal.map(_.maxAmount).distinct
    def resourceName: ReactiveHtmlElement[HTMLSpanElement] =
      span(className := "resource-label", ResourceIcons.forLabel(name).map(ResourceIcons.icon), span(name))

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
                  case Some(content) => detailsTag(summaryTag(resourceName), content)
                  case None => resourceName
                }
              },
              netRateSignal.map { rate =>
                div(className := "resource-timing", title := "Estimated simulated time at the current net rate.",
                  child.maybe <-- resourceSignal.combineWith(rate).map { case (resource, net) =>
                    ResourceTiming.estimate(resource, net).map { estimate =>
                      span(className := (if (estimate.toEmpty) "text-danger" else "text-body-secondary"), estimate.text)
                    }
                  },
                )
              },
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-3 text-end text-nowrap resource-amount",
              className <-- resourceSignal.map(resource =>
                if (resource.atCapacity) "text-primary fw-bold" else ""),
              child.text <-- amountSignal.map(prettyNumberStr),
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
            )
          )
        )
    }
  }

  private def actionAmountControl(controlId: String, amountVar: Var[Int]): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "mobile-only batch-controls",
      label(
        forId := controlId,
        "Action amount",
      ),
      select(
        idAttr := controlId,
        className := "form-select",
        Seq(1, 10, 25, 250).map { amount =>
          option(value := amount.toString, s"×$amount")
        },
        value <-- amountVar.signal.map(_.toString),
        onChange.mapToValue.map(_.toInt) --> amountVar.writer,
      ),
    )

  private def queenRoutineControls(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def update(change: QueenRoutine => QueenRoutine): Unit =
      useSignalValue[AntsData](antsSignal, ants => {
        val next = change(ants.queenRoutine)
        Actions.configureQueenRoutine(next.enabled, next.targetWorkers, next.sugarReserve)
      })
    div(
      className := "queen-routine mb-3",
      infoHeading("Queen's Routine", div(
        "Keeps the worker target while respecting the sugar reserve. Adults, developing workers and manual worker orders count toward the target. Scouts stay manual.",
        div(child.text <-- antsSignal.map(a => s"${prettyNumberStr(BroodProgression.eggCost(a))} sugar per worker egg.")),
        div("Aging vacancies are refilled from the idle/default pool. With no workers, the queen can gather supplies for one emergency worker."),
      ), minor = true),
      child.maybe <-- antsSignal.map(_.agingJobVacancies).distinct.map { vacancies => Option.when(vacancies.nonEmpty) {
        div(className := "aging-job-replacements small mb-2",
          span("Awaiting workers: " + vacancies.map { case (task, amount) => s"${amount / u} ${task.name}" }.mkString(" · ")),
          button(className := "btn btn-outline-secondary btn-sm ms-2", typ := "button", minHeight.px := 44,
            "Cancel replacements", onClick --> (_ => Actions.cancelAgingJobReplacements())),
        )
      } },
      label(
        input(
          idAttr := "queen-routine-enabled", typ := "checkbox", className := "form-check-input me-2",
          checked <-- antsSignal.map(_.queenRoutine.enabled).distinct,
          onChange.mapToChecked --> (enabled => update(_.copy(enabled = enabled))),
        ),
        "Automatic worker eggs",
      ),
      div(className := "row g-2 mt-1",
        div(className := "col-6",
          label(forId := "queen-routine-target", "Target workers"),
          input(idAttr := "queen-routine-target", typ := "number", minAttr := "0", maxAttr := "1000000", className := "form-control",
            value <-- antsSignal.map(_.queenRoutine.targetWorkers.toString).distinct,
            onChange.mapToValue --> (value => update(_.copy(targetWorkers = value.toIntOption.getOrElse(0)))),
          ),
        ),
        div(className := "col-6",
          label(forId := "queen-routine-reserve", "Sugar reserve"),
          input(idAttr := "queen-routine-reserve", typ := "number", minAttr := "0", maxAttr := "1000000000", className := "form-control",
            value <-- antsSignal.map(a => (a.queenRoutine.sugarReserve / u).toString).distinct,
            onChange.mapToValue --> (value => update(_.copy(sugarReserve = Math.max(0L, Math.min(1000000000L, value.toLongOption.getOrElse(0L))) * u))),
          ),
        ),
      ),
      div(className := "queen-routine-status small", child.text <-- antsSignal.combineWith(nestSignal, sugarSignal).map {
        case (ants, nest, sugar) => AutomaticBrood.status(ants, nest, sugar.amount)
      }),
    )
  }

  private def constructionBlockedSignal(kind: ChamberType): Signal[Option[String]] =
    nestSignal.combineWith(upgradesSignal).map { case (nest, upgrades) => Construction.blockedReason(nest, upgrades, kind) }.distinct

  private def nestDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def standardBuyChamber(
        name: String,
        chamberType: ChamberType,
        unlockF: ActionUnlocks => Boolean,
    ): Signal[Option[ReactiveHtmlElement[HTMLDivElement]]] =
      ifUnlockedOpt(unlocks => unlockF(unlocks.actions)) {
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
      actionButton(
        name = GatherSugarAction,
        enabledSignal = Val(true),
        descriptionSignal = Val(None),
        costSignal = Val(ActionCost.empty),
        bonusSignal = Val(ActionBonus(sugar = 1 * u)),
        onClickAction = _ => giveResources(sugar = PlayerGatherSugarClickAmount),
      ),
      detailsTag(
        className := "col-12 nest-brood border-top border-bottom my-3 py-3",
        onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[HTMLElement].setAttribute("open", "")),
        summaryTag(className := "brood-summary mb-2", minHeight.px := 44, "Brood"),
        display <-- unlocksSignal.map(u => if (u.actions.canLayEggs) "block" else "none"),
        infoHeading("Brood", div(
          div(className := "brood-feeding", child.text <-- antsSignal.map { ants =>
            if (!ants.standardWorkersUnlocked) "Small worker eggs: 5 sugar · about 15s development. Founding care needs no protein."
            else "Worker eggs: 10 sugar · about 2m at full care. Larval care: 20 sugar + 2 protein per worker."
          }),
          p("Egg orders share the queen's laying queue. All development stages occupy brood places. Food shortages pause care without losing progress."),
        child.maybe <-- antsSignal.map(_.ages.active).distinct.map(active => Option.when(active) {
          detailsTag(
            className := "colony-aging mb-2",
            summaryTag(child.text <-- antsSignal.combineWith(currentTickSignal).map {
              case (ants, tick) => PopulationAging.forecast(ants, tick)
            }),
            p(child.text <-- upgradesSignal.map(upgrades =>
              s"New adults live ${PopulationAging.lifespanText(upgrades)} minutes of simulated time. Aging continues during expeditions and carcass retrievals. Overdue ants die on return. Stored-time multipliers advance aging with the colony.")),
            p("After Queen's Routine is unlocked, aging draws from idle/default workers first and refills missing jobs as workers become available. Deaths are summarized once per minute."),
          )
        }),
        )),
        child.maybe <-- sugarSignal.combineWith(TickCalculator.sugarTickGain.sum).map { case (sugar, net) =>
          Option.when(net < 0L && sugar.amount < -net) {
            div(className := "starvation-warning text-danger small mb-2",
              b("Colony is starving"), s" · Sugar deficit: ${resourceRateStr(-net * TicksPerSecond)}/s")
          }
        },
        child.maybe <-- antsSignal.combineWith(currentTickSignal).map { case (ants, tick) =>
          val ages = ants.ages
          val soon = (ages.workers ++ ages.smallWorkers ++ ages.scouts ++ ages.repletes).exists(c => c.count > 0L && c.expiresAt <= tick + 5L * PopulationAging.Minute)
          Option.when(ages.active && soon)(div(className := "aging-warning small text-danger mb-2", PopulationAging.forecast(ants, tick)))
        },
        child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.QueensRoutine).unlocked)) {
          queenRoutineControls
        },
        div(className := "brood-pipeline small mb-2",
          className <-- antsSignal.combineWith(nestSignal, resourcesSignal, upgradesSignal, explorationSignal).map {
            case (ants, nest, resources, upgrades, exploration) =>
              if (BroodProgression.status(ants, nest, resources, upgrades, exploration).contains("Needs")) "text-danger" else ""
          },
          child.text <-- antsSignal.combineWith(nestSignal, resourcesSignal, upgradesSignal, explorationSignal).map { case (ants, nest, resources, upgrades, exploration) =>
            BroodProgression.status(ants, nest, resources, upgrades, exploration)
          },
        ),
        child.maybe <-- antsSignal.map(_.standardWorkersUnlocked).distinct.map(standard => Option.when(standard)(
          div(className := "brood-care-cost small mb-2", "Larvae: 20 sugar · 2 protein / worker"))),
        child.maybe <-- antsSignal.map(_.queuedEggs > 0).distinct.map { queued => Option.when(queued) {
          button(className := "btn btn-outline-secondary mb-2", typ := "button", minHeight.px := 44, "Cancel egg orders",
            onClick --> (_ => Actions.cancelEggOrders()))
        } },
        child.maybe <-- antsSignal.map(EmergencyRecovery.needed).distinct.map { needed => Option.when(needed) {
          div(className := "emergency-recovery row mb-2",
            actionButton(
              name = "Raise Emergency Worker",
              enabledSignal = antsSignal.combineWith(sugarSignal).map { case (ants, sugar) => EmergencyRecovery.canRaise(ants, sugar.amount) },
              descriptionSignal = Val(Some(eggDescription(EmergencyRecovery.Kind))),
              costSignal = Val(ActionCost(sugar = EmergencyRecovery.EggCost)),
              bonusSignal = Val(ActionBonus.empty),
              onClickAction = _ => Actions.raiseEmergencyWorker(),
            ),
          )
        } },
        actionAmountControl("brood-action-amount", broodMultiplier),
        div(
          className := "row",
          child.maybe <-- ifUnlockedOpt(_.actions.canLayEggs) {
            val displayedBatch = modifiersMultiplierSignal.combineWith(antsSignal, nestSignal).map {
              case (requested, ants, nest) => Math.max(1, Actions.workerEggAmount(ants, nest, requested))
            }.distinct
            actionButton(
              name = LayEggAction,
              displayNameSignal = Some(antsSignal.map(_.standardWorkersUnlocked).distinct.map(
                standard => if (standard) LayEggAction else "Raise Small Worker Egg")),
              topLeftBadgeSignal = displayedBatch.map {
                case 1    => None
                case mult => Some(span(s"x$mult"))
              },
              enabledSignal = Actions.layEggActionEnabled,
              descriptionSignal = antsSignal.map(_.standardWorkersUnlocked).distinct.map(standard => Some(eggDescription(if (standard) "worker" else "small"))),
              costSignal = displayedBatch.combineWith(antsSignal).map { case (mult, ants) => ActionCost(sugar = mult * BroodProgression.eggCost(ants)) },
              bonusSignal = Val(ActionBonus.empty),
              onClickAction = mult => Actions.layEggAction(mult),
              requirementsSignal = broodRequirementsSignal.map(_.filterNot(_.label == "brood places")),
            )
          },
          child.maybe <-- ifUnlockedOpt(nestSignal.map(_.chambers.nurseryChamber.level > 0)) {
            val displayedBatch = modifiersMultiplierSignal.combineWith(antsSignal, nestSignal).map {
              case (requested, ants, nest) => Math.max(1, ScoutProgression.orderAmount(ants, nest, requested))
            }.distinct
            actionButton(
              name = "Raise Scout Egg",
              topLeftBadgeSignal = displayedBatch.map(amount => Option.when(amount > 1)(span(s"x$amount"))),
              enabledSignal = antsSignal.combineWith(nestSignal)
                .map { case (ants, nest) => ScoutProgression.canRaise(ants, nest) },
              descriptionSignal = Val(Some(eggDescription("scout"))),
              costSignal = displayedBatch.map(amount => ActionCost(sugar = amount * ScoutProgression.eggCost.sugar, protein = amount * ScoutProgression.eggCost.protein)),
              bonusSignal = Val(ActionBonus.empty),
              onClickAction = amount => Actions.raiseScout(amount),
              requirementsSignal = broodRequirementsSignal.map(_.filterNot(_.label == "brood places")),
            )
          },
        ),
      ),
      child.maybe <-- nestSignal.combineWith(upgradesSignal).map { case (nest, upgrades) =>
        Option.when(upgrades(UpgradeType.RoyalNurseryPlanning).unlocked || nest.royalBroodSlots > 0)(nest.royalBroodSlots > 0)
      }.distinct.map(_.map { built =>
          div(className := "col-12 nest-royal-nursery border-top my-2 py-2",
            infoHeading("Royal brood", div("Up to five Royal Nurses tend one daughter queen. Full staffing takes about 10 minutes. Care pauses when supplies run out; progress is kept.")),
            if (built) royalRearingDiv else div(className := "row",
              actionButton(
                name = "Royal Nursery",
                enabledSignal = constructionBlockedSignal(ChamberType.RoyalNursery).map(_.isEmpty),
                blockedReasonSignal = constructionBlockedSignal(ChamberType.RoyalNursery),
                costSignal = Val(RoyalNurseryProgression.buildCost),
                bonusSignal = Val(ChamberBonuses.forChamber(ChamberType.RoyalNursery)),
                descriptionSignal = Val(None),
                onClickAction = _ => Actions.addBuildTask(BuildTask(ChamberType.RoyalNursery, RoyalNurseryProgression.buildCost)),
              ),
            ),
          )
      }),
      child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.RepleteStorage).unlocked)) {
        div(className := "col-12 nest-repletes border-top my-2 py-2",
          h5("Living water storage"),
          div(className := "row",
            actionButton(
              name = "Promote Replete",
              enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal).map { case (ants, nest, resources) =>
                ants.availableWorkersCount >= u && ants.repletes / u < nest.chambers.repleteChamber.level && resources.hasResources(RepleteProgression.cost)
              },
              costSignal = Val(RepleteProgression.cost),
              bonusSignal = Val(ActionBonus(maxWater = RepleteProgression.WaterPerReplete)),
              requirementsSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                List(ActionRequirement("replete slots", Math.max(0L, nest.chambers.repleteChamber.level.toLong * u - ants.repletes)))
              },
              descriptionSignal = Val(Some(div(
                actionFact("Worker", "Oldest available home worker"),
                actionFact("Lifespan", "Fresh 90–120 simulated minutes"),
                actionFact("Upkeep", s"${resourceRateStr(antsSugarUpkeepSecond)} sugar/s"),
                actionFact("Role", "Water storage; no worker tasks"),
                actionFact("Storage", "Water kept if capacity falls"),
              ))),
              onClickAction = _ => Actions.promoteReplete(),
            ),
          ),
        )
      },
      child.maybe <-- nestSignal.combineWith(explorationSignal).map { case (nest, exploration) =>
        Option.when(nest.dryClearingOutpost || OutpostProgression.visible(exploration))(
          (nest.dryClearingOutpost, OutpostProgression.expansionVisible(nest, exploration)))
      }.distinct.map(_.map { case (established, expansionVisible) =>
          div(className := "col-12 nest-outposts border-top my-2 py-2",
            h5("Outposts"),
            if (established) div(className := "dry-clearing-outpost",
              b(child.text <-- nestSignal.map(nest => s"Dry Clearing Outpost · Level ${OutpostProgression.level(nest)}").distinct),
              div(child.text <-- nestSignal.map(nest => s"+${OutpostProgression.housing(nest) / u} colony capacity · ${OutpostProgression.capacity(nest) / u} Seed Forager slots").distinct),
              div(child.text <-- antsSignal.combineWith(resourcesSignal, upgradesSignal, nestSignal).map { case (ants, resources, upgrades, currentNest) =>
                val harvest = OutpostProgression.harvestPerTick(ants, currentNest, upgrades, resources)
                val state = if (ants.taskWorkers(AntTask.SeedForager) == 0L) "0 Seed Foragers"
                  else if (resources.protein.atCapacity && harvest.protein == 0L) "Protein full · water use paused"
                  else if (resources.water.amount == 0L) "Needs water · foraging paused"
                  else s"+${preciseRateStr(harvest.protein * TicksPerSecond)} protein/s · −${preciseRateStr(harvest.water * TicksPerSecond)} water/s"
                state
              }),
              if (expansionVisible) div(className := "row mt-2",
                actionButton(
                  name = "Expand Dry Clearing Outpost",
                  enabledSignal = nestSignal.combineWith(explorationSignal, resourcesSignal).map { case (n, e, r) => OutpostProgression.canExpand(n, e, r) },
                  costSignal = nestSignal.map(OutpostProgression.expansionCost),
                  bonusSignal = Val(ActionBonus(maxAnts = OutpostProgression.ExpansionHousing, seedForagerSlots = 5)),
                  descriptionSignal = Val(None),
                  onClickAction = _ => Actions.expandDryClearingOutpost(),
                ),
              ) else emptyNode,
            ) else div(className := "row",
              actionButton(
                name = "Establish Dry Clearing Outpost",
                enabledSignal = nestSignal.combineWith(explorationSignal, resourcesSignal).map { case (n, e, r) => OutpostProgression.canEstablish(n, e, r) },
                costSignal = Val(OutpostProgression.cost),
                bonusSignal = Val(ActionBonus(maxAnts = OutpostProgression.Housing, seedForagerSlots = (OutpostProgression.ForagerSlots / u).toInt)),
                descriptionSignal = Val(None),
                onClickAction = _ => Actions.establishDryClearingOutpost(),
              ),
            ),
          )
      }),
      div(
        className := "col-12 nest-chambers",
        display <-- unlocksSignal.combineWith(nurseryUnlockedSignal).map { case (unlocks, nursery) =>
          val actions = unlocks.actions
          if (actions.canBuildNestUpgrade || actions.canBuildQueenChamber || actions.canBuildFoodStorageChamber || nursery) "block" else "none"
        },
        h5(className := "mt-3", "Chambers"),
        div(
          className := "row",
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Nest.name,
            chamberType = ChamberType.Nest,
            unlockF = _.canBuildNestUpgrade,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.Queen.name,
            chamberType = ChamberType.Queen,
            unlockF = _.canBuildQueenChamber,
          ),
          child.maybe <-- standardBuyChamber(
            name = ChamberType.FoodStorage.name,
            chamberType = ChamberType.FoodStorage,
            unlockF = _.canBuildFoodStorageChamber,
          ),
          child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.RepleteStorage).unlocked)) {
            val cost = Actions.buildChamberCost(ChamberType.Replete)
            actionButton(
              name = ChamberType.Replete.name,
              enabledSignal = constructionBlockedSignal(ChamberType.Replete).map(_.isEmpty),
              blockedReasonSignal = constructionBlockedSignal(ChamberType.Replete),
              descriptionSignal = Val(None),
              costSignal = cost,
              bonusSignal = Actions.getActionBonus(ChamberType.Replete),
              topRightBadgeSignal = chambersSignal.map(c => Option.when(c.repleteChamber.level > 0)(span(c.repleteChamber.level))),
              onClickAction = _ => useSignalValue[ActionCost](cost, c => Actions.addBuildTask(BuildTask(ChamberType.Replete, c))),
            )
          },
          child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.AphidTending).unlocked)) {
            val cost = Actions.buildChamberCost(ChamberType.Aphid)
            actionButton(
              name = ChamberType.Aphid.name,
              enabledSignal = constructionBlockedSignal(ChamberType.Aphid).map(_.isEmpty),
              blockedReasonSignal = constructionBlockedSignal(ChamberType.Aphid),
              descriptionSignal = Val(None),
              costSignal = cost,
              bonusSignal = Actions.getActionBonus(ChamberType.Aphid),
              topRightBadgeSignal = chambersSignal.map(chambers =>
                Option.when(chambers.aphidChamber.level > 0)(span(chambers.aphidChamber.level))),
              onClickAction = _ => useSignalValue[ActionCost](cost,
                cost => Actions.addBuildTask(BuildTask(ChamberType.Aphid, cost))),
            )
          },
          child.maybe <-- ifUnlockedOpt(nurseryUnlockedSignal) {
            val cost = Actions.buildChamberCost(ChamberType.Nursery)
            actionButton(
              name = "Nursery Chamber",
              enabledSignal = constructionBlockedSignal(ChamberType.Nursery).map(_.isEmpty),
              blockedReasonSignal = constructionBlockedSignal(ChamberType.Nursery),
              descriptionSignal = Val(None),
              costSignal = cost,
              bonusSignal = Actions.getActionBonus(ChamberType.Nursery),
              topRightBadgeSignal = chambersSignal.map(chambers =>
                Option.when(chambers.nurseryChamber.level > 0)(span(chambers.nurseryChamber.level))),
              onClickAction = _ => useSignalValue[ActionCost](cost,
                cost => Actions.addBuildTask(BuildTask(ChamberType.Nursery, cost))),
            )
          },
        ),
      ),
    )
  }

  private def territoryDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val chosen = Var(10)
    val siteSignal = explorationSignal.map(_.territory).distinct
    val workerSignal = antsSignal.map(TerritoryProgression.workers).distinct
    div(className := "territory-card",
      infoHeading("Contested Foraging Ground", div("Hauls arrive after each 60-second contest. Reinforcements use the oldest idle/default workers. Withdrawal returns survivors but abandons the unfinished haul. Full storage does not stop contests or losses.")),
      child <-- siteSignal.map(_.held).distinct.map {
        case false => div(
          label(forId := "territory-workers", "Workers to hold"),
          div(className := "d-flex flex-wrap align-items-center gap-2 my-2",
            input(idAttr := "territory-workers", className := "form-control", typ := "number", minAttr := "10", maxAttr := "37", stepAttr := "1",
              width.px := 110, minHeight.px := 44, value <-- chosen.signal.map(_.toString),
              onChange.mapToValue --> (value => chosen.set(TerritoryProgression.target(value.toIntOption.getOrElse(10))))),
            List(10, 20, 37).map(n => button(className := "btn btn-outline-secondary", typ := "button", minHeight.px := 44,
              n.toString, aria.label := s"Hold with $n workers", onClick --> (_ => chosen.set(n)))),
          ),
          div(className := "territory-risk text-danger small mb-2", child.text <-- chosen.signal.map(n => TerritoryProgression.risk(n.toLong))),
          div(className := "small mb-1", child.text <-- chosen.signal.map(n =>
            s"+${resourceRateStr(n.toLong * TerritoryProgression.ProteinPerWorkerSecond)} protein/s")),
          div(className := "territory-entry-aging text-danger small mb-1", child.text <-- antsSignal.combineWith(currentTickSignal, chosen.signal).map {
            case (ants, tick, amount) =>
              val due = RaidProgression.overdue(ants.ages.workers, amount.toLong, tick + TerritoryProgression.ContestTicks)
              if (due == 0L) "" else s"$due selected workers expire before the first contest; aging can add losses."
          }),
          div(className := "row", actionButton(
            name = "Hold Foraging Ground",
            enabledSignal = chosen.signal.combineWith(antsSignal, resourcesSignal, explorationSignal, unlocksSignal).map {
              case (n, ants, resources, exploration, unlocks) =>
                !exploration.territory.held && unlocks.tabs.exploreTabUnlocked && DiscoveryProgression.ContestedGround.found(exploration) &&
                  ants.availableWorkersCount >= n.toLong * u && resources.hasResources(TerritoryProgression.cost(n))
            },
            costSignal = chosen.signal.map(TerritoryProgression.cost), bonusSignal = Val(ActionBonus.empty),
            descriptionSignal = Val(Some(div("Oldest home workers are assigned first. Combat casualties are random within the holders. Workers consume ordinary sugar upkeep. Full protein storage does not stop contests or casualties. Hauls are delivered only if workers survive the contest and aging."))),
            onClickAction = _ => useSignalValue[Int](chosen.signal, n => Actions.claimTerritory(n)),
          )),
        )
        case true => div(className := "territory-held border rounded p-2",
          div(className := "d-flex flex-wrap justify-content-between gap-2",
            b(child.text <-- workerSignal.combineWith(siteSignal).map { case (count, site) => s"$count/${site.targetWorkers} workers holding" },
              className <-- workerSignal.combineWith(siteSignal).map { case (count, site) => if (count < site.targetWorkers) "text-danger" else "" }),
            span(child.text <-- siteSignal.combineWith(currentTickSignal).map { case (site, tick) =>
              s"Next contest: ${prettyTimeFromTicks(Math.max(0L, site.nextContestTick - tick))}"
            }),
          ),
          div(className := "territory-risk text-danger small my-2", child.text <-- workerSignal.map(TerritoryProgression.risk)),
          div(className := "territory-pending", child.text <-- siteSignal.map(site => s"Haul: ${prettyNumberStr(site.pendingProtein)} protein")),
          div(className := "small", child.text <-- workerSignal.map(n =>
            s"+${resourceRateStr(n * TerritoryProgression.ProteinPerWorkerSecond)} protein/s")),
          div(className := "territory-storage small my-1",
            child.text <-- proteinSignal.map(p => if (p.atCapacity) "Full storage · losses continue" else s"Storage room: ${prettyNumberStr(p.freeSpace)} protein"),
            className <-- proteinSignal.combineWith(siteSignal, workerSignal, currentTickSignal).map { case (p, site, count, tick) =>
              val expected = site.pendingProtein + count * TerritoryProgression.ProteinPerWorkerSecond * Math.max(0L, site.nextContestTick - tick) / TicksPerSecond
              if (p.freeSpace < expected) "text-danger" else ""
            },
          ),
          div(className := "territory-aging text-danger small my-1", child.text <-- siteSignal.map { site =>
            val due = site.ages.filter(_.expiresAt <= site.nextContestTick).map(_.count).sum
            if (due == 0L) "" else s"$due holders expire by the contest. Aging losses can overlap combat losses."
          }),
          div(className := "territory-reinforcements small",
            child.maybe <-- antsSignal.combineWith(siteSignal).map { case (ants, site) =>
              val missing = Math.max(0L, site.targetWorkers - TerritoryProgression.workers(ants))
              Option.when(missing > 0L)(span("Reinforcements: ", requirementLabel(ActionRequirement(
                ants.availableWorkersLabel, ants.availableWorkersCount, missing * u))))
            },
          ),
          div(className := "d-flex flex-wrap gap-2 my-2",
            button(className := "btn btn-outline-primary", typ := "button", minHeight.px := 44, "Reinforce",
              disabled <-- workerSignal.combineWith(siteSignal, availableWorkersCountSignal).map {
                case (n, site, available) => n >= site.targetWorkers || available < u
              }, onClick --> (_ => Actions.reinforceTerritory())),
            button(className := "btn btn-outline-danger", typ := "button", minHeight.px := 44, "Withdraw",
              onClick --> (_ => Actions.withdrawTerritory())),
          ),
          label(className := "d-flex align-items-center gap-2 my-2", minHeight.px := 44,
            input(typ := "checkbox", className := "form-check-input", checked <-- siteSignal.map(_.maintain).distinct,
              onChange.mapToChecked --> (enabled => Actions.maintainTerritory(enabled))),
            "Maintain staffing automatically",
          ),
        )
      },
    )
  }

  private def prestigeDiv: ReactiveHtmlElement[HTMLDivElement] = div(
    className := "col-12 lineage-section",
    child.maybe <-- lineageVisibleSignal.map {
      visible => Option.when(visible) {
        div(className := "lineage-overview",
          infoHeading("Queen's lineage", div("Daughter queens found new colonies. DNA buys permanent Understandings.")),
          child.maybe <-- antsSignal.map(_.daughterQueens < u).distinct.map { notReady => Option.when(notReady) {
            div(className := "lineage-queen-status small text-body-secondary mb-2",
              child.text <-- antsSignal.map(ants => if (ants.royalBrood.nonEmpty) "Daughter queen: rearing" else "Daughter queen: not ready").distinct)
          } },
          div(child.text <-- antsSignal.map(a => s"Generation ${a.lineage.foundings.toLong + 1L} · DNA: ${a.lineage.dna} · +${a.lineage.foundings.toLong * 10L}% sugar production")),
          child.maybe <-- antsSignal.map(_.lineage).distinct.map { lineage =>
            Option.when(lineage.foundings > 0 && !lineage.sharedPaths) {
              div(className := "understanding-shared-paths mt-2",
                infoHeading("Understanding: Shared Paths", div("Automates collection from studied sites using workers and guiding scouts."), minor = true),
                div(className := "small", "Cost: 1 DNA"),
                button(className := "btn btn-primary mt-1", minHeight.px := 44, typ := "button",
                  "Learn Shared Paths", disabled := (lineage.dna < 1L),
                  onClick --> (_ => Actions.purchaseSharedPaths())),
              )
            }
          },
          child.maybe <-- antsSignal.map(_.lineage.sharedPaths).distinct.map(known =>
            Option.when(known)(div(className := "small mt-1", "Shared Paths inherited"))),
          child.maybe <-- antsSignal.map(_.daughterQueens >= u).distinct.map { ready => Option.when(ready) {
            detailsTag(className := "founding-preview mt-2",
              summaryTag(minHeight.px := 44, "Review founding"),
              p(className := "small my-2", "Found with your daughter queen: +1 DNA and +10% sugar production, added to your inherited bonus."),
              p(className := "small", "Keep: DNA, Understandings, lifetime statistics, discovered resource visibility, settings and stored time."),
              p(className := "small", "Reset: resources, ants and brood, chambers, research, explored map, discoveries, trails, queues and task assignments."),
              button(className := "btn btn-primary", minHeight.px := 44, typ := "button", "Found daughter colony",
                onClick --> { _ =>
                  if (window.confirm("Found a new colony? Colony resources, ants, chambers, research and exploration will reset. DNA, Understandings, lifetime statistics, settings and stored time are kept. Gain 1 DNA and +10% sugar production."))
                    Actions.foundDaughterColony()
                }),
            )
          } },
        )
      }
    },
  )

  private def royalRearingDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val broodSignal = antsSignal.map(_.royalBrood).distinct
    val nursesSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
      RoyalBroodProgression.nurses(ants, nest)
    }.distinct
    val projectedSignal = antsSignal.combineWith(nestSignal, upgradesSignal, explorationSignal, resourcesSignal).map {
      case (ants, nest, upgrades, exploration, resources) =>
        RoyalBroodProgression.forecastResources(ants, nest, upgrades, exploration, resources)
    }
    val statusSignal = antsSignal.combineWith(nestSignal, projectedSignal).map { case (ants, nest, resources) =>
      RoyalBroodProgression.status(ants, nest, resources)
    }.distinct
    div(className := "royal-nursery-ready",
      child <-- antsSignal.map(ants => if (ants.royalBrood.nonEmpty) 1 else if (ants.daughterQueens > 0L) 2 else 0).distinct.map {
        case 0 => div(
          div(className := "royal-staffing small mb-2",
            className("text-danger") <-- nursesSignal.map(_ == 0L),
            child.text <-- nursesSignal.map(n => s"$n/${RoyalBroodProgression.MaxNurses} Royal Nurses")),
          p(className := "royal-total-care small mb-1",
            s"Care: ${prettyNumberStr(RoyalBroodProgression.rearingCost.sugar)} sugar · ${prettyNumberStr(RoyalBroodProgression.rearingCost.protein)} protein · ${prettyNumberStr(RoyalBroodProgression.rearingCost.water)} water."),
          div(className := "row",
            actionButton(
              name = "Raise Royal Egg",
              enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal).map { case (ants, nest, resources) =>
                RoyalBroodProgression.canStart(ants, nest, resources)
              },
              costSignal = Val(RoyalBroodProgression.startCost),
              bonusSignal = Val(ActionBonus.empty),
              requirementsSignal = antsSignal.combineWith(nestSignal).map { case (ants, nest) => List(
                ActionRequirement("colony slots", BroodProgression.availableColonySlots(ants, nest), isCost = true),
              ) },
              descriptionSignal = Val(Some(div("Reserves one colony slot. Nurses use water throughout rearing; larvae also need sugar and protein. Progress is kept when care pauses."))),
              onClickAction = _ => Actions.raiseRoyalEgg(),
            ),
          ),
        )
        case 1 => div(className := "royal-brood-card border rounded p-2",
          div(className := "d-flex justify-content-between gap-2",
            b(child.text <-- broodSignal.map(_.map(RoyalBroodProgression.stage).getOrElse("Royal brood"))),
            span(child.text <-- broodSignal.map(b => s"${b.map(_.careUnits * 100L / RoyalBroodProgression.TotalCare).getOrElse(0L)}%")),
          ),
          div(className := "progress my-2", role := "progressbar", aria.label := "Royal brood development",
            aria.valueMin := 0.0, aria.valueMax := 100.0,
            aria.valueNow <-- broodSignal.map(_.map(_.careUnits * 100L / RoyalBroodProgression.TotalCare).getOrElse(0L).toDouble),
            div(className := "progress-bar", width <-- broodSignal.map(b => s"${b.map(_.careUnits * 100.0 / RoyalBroodProgression.TotalCare).getOrElse(0.0)}%")),
          ),
          div(className := "small", child.text <-- nursesSignal.map(n => s"$n/${RoyalBroodProgression.MaxNurses} Royal Nurses")),
          div(className := "royal-feeding small my-1",
            child.text <-- broodSignal.combineWith(nursesSignal).map { case (brood, count) =>
              val cost = brood.map(b => RoyalBroodProgression.scaledCost(RoyalBroodProgression.unitCost(b), count * TicksPerSecond)).getOrElse(ActionCost.empty)
              val rates = List("sugar" -> cost.sugar, "protein" -> cost.protein, "water" -> cost.water)
                .collect { case (name, amount) if amount > 0L => s"−${resourceRateStr(amount)} $name/s" }
              if (rates.isEmpty) "" else rates.mkString(" · ")
            },
          ),
          div(className := "royal-care-status small", child.text <-- statusSignal,
            className <-- statusSignal.map(s => if (s.startsWith("Needs") || s.startsWith("Limited")) "text-danger" else "")),
          div(className := "small text-body-secondary my-1",
            child.text <-- broodSignal.combineWith(nursesSignal).map { case (brood, count) =>
              brood.map { b =>
                val ticks = RoyalBroodProgression.remainingTicks(b, count)
                if (ticks < 0L) ""
                else s"~${prettyTimeFromTicks(ticks)} remaining"
              }.getOrElse("")
            },
          ),
          button(className := "btn btn-outline-primary royal-care-toggle mt-1", minHeight.px := 44,
            `type` := "button",
            child.text <-- broodSignal.map(b => if (b.exists(_.paused)) "Resume care" else "Pause care"),
            onClick --> { _ => useSignalValue[Option[RoyalBrood]](broodSignal, b => Actions.setRoyalCarePaused(!b.exists(_.paused))) },
          ),
        )
        case _ => div(className := "royal-queen-ready border rounded p-2",
          ResourceIcons.icon(ResourceIcons.Queens), b("Daughter queen mature"),
          div(className := "small", "Your daughter queen is ready for the next generation."),
        )
      },
    )
  }

  private case class ActionRequirement(label: String, available: Long, required: Long = u, maximum: Option[Long] = None, isCost: Boolean = false)

  private def requirementLabel(requirement: ActionRequirement, compact: Boolean = false): ReactiveHtmlElement[HTMLSpanElement] =
    span(
      className := "action-cost",
      title := s"${requirement.label}: ${prettyNumberStr(requirement.available)} available, ${prettyNumberStr(requirement.required)} required",
      aria.label := s"${requirement.label}: ${prettyNumberStr(requirement.available)} available, ${prettyNumberStr(requirement.required)} required",
      role := "img",
      ResourceIcons.forLabel(requirement.label).filter(_ => compact).map(ResourceIcons.icon),
      span(
        className := (if (requirement.available < requirement.required) "text-danger" else "text-success"),
        prettyNumberStr(requirement.available),
      ),
      "/",
      span(
        className := (if (requirement.maximum.exists(_ < requirement.required)) "text-danger" else ""),
        title := requirement.maximum.map(max => s"Storage capacity: ${prettyNumberStr(max)}").getOrElse(""),
        prettyNumberStr(requirement.required),
      ),
      span(
        className := (if (compact && ResourceIcons.forLabel(requirement.label).nonEmpty) "visually-hidden" else ""),
        s" ${requirement.label}",
      ),
    )

  private val broodRequirementsSignal: Signal[List[ActionRequirement]] =
    antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
      List(
        ActionRequirement("brood places", Math.max(0L, BroodProgression.capacity(nest).toLong * u - ants.eggsAndLarvae.size * u)),
        ActionRequirement("colony slots", BroodProgression.availableColonySlots(ants, nest)),
      )
    }.distinct

  private def actionCosts(
      costSignal: Signal[ActionCost],
      requirementsSignal: Signal[List[ActionRequirement]],
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "action-costs small d-flex flex-wrap justify-content-center gap-2 pt-1",
      aria.label := "Action costs",
      children <-- costSignal.combineWith(resourcesSignal, antsSignal, requirementsSignal).map {
        case (cost, resources, ants, requirements) =>
          def costLine(required: Long, available: Long, label: String, maximum: Option[Long] = None) =
            Option.when(required > 0)(ActionRequirement(label, available, required, maximum))
          val costs = List(
            costLine(cost.protein, resources.protein.amount, "protein", Some(resources.protein.maxAmount)),
            costLine(cost.water, resources.water.amount, "water", Some(resources.water.maxAmount)),
            costLine(cost.resin, resources.resin.amount, "resin", Some(resources.resin.maxAmount)),
            costLine(cost.sugar, resources.sugar.amount, "sugar", Some(resources.sugar.maxAmount)),
            costLine(cost.tunnelingSpace, resources.tunnelingSpace.amount, "tunneling space", Some(resources.tunnelingSpace.maxAmount)),
            costLine(cost.knowledge, resources.knowledge.amount, "knowledge"),
            costLine(cost.idleWorkers, ants.availableWorkersCount, ants.availableWorkersLabel),
          ).flatten ++ requirements.filter(_.isCost)
          if (costs.isEmpty) Seq(span(className := "text-body-secondary", "Free"))
          else costs.map(cost => requirementLabel(cost, compact = true).amend(className := "text-nowrap"))
      },
    )

  private def actionButton(
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
  )(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "col-6 px-3 py-2 d-grid action-cell",
      button(
        className := "btn btn-primary position-relative",
        className := "fs-4",
        `type` := "button",
        child.text <-- displayNameSignal.getOrElse(Val(name)),
        disabled <-- enabledSignal.map(!_),
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
      child.maybe <-- blockedReasonSignal.map(_.map(reason =>
        div(className := "action-blocked-reason small text-danger text-center", reason))),
          detailsTag(
            className := "action-details",
            summaryTag(
              aria.label <-- displayNameSignal.getOrElse(Val(name)).map(n => s"Details for $n"),
              title <-- displayNameSignal.getOrElse(Val(name)).map(n => s"Details for $n"),
              span(aria.hidden := true,
                svg.svg(svg.viewBox := "0 0 24 24",
                  svg.path(svg.d := "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 11v6M12 7h.01")),
              ),
            ),
            actionDetails(descriptionSignal, costSignal, bonusSignal, requirementsSignal, includeCosts = false),
          ),
    )

  private def createTooltipForActionButton(
      elem: ReactiveHtmlElement[HTMLElement],
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
      requirementsSignal: Signal[List[ActionRequirement]] = Val(Nil),
      includeCosts: Boolean = true,
  ): EventListener[MouseEvent, MouseEvent] =
    setTooltip(elem, actionDetails(descriptionSignal, costSignal, bonusSignal, requirementsSignal, includeCosts))

  private def actionFact(label: String, text: String): ReactiveHtmlElement[HTMLDivElement] =
    div(className := "action-detail-row", b(s"$label: "), text)

  // Related actions share fields and order; values come from the gameplay model.
  private def eggDescription(kind: String): ReactiveHtmlElement[HTMLDivElement] = {
    val brood = AntBrood.Developing(0L, kind)
    div(className := "egg-description text-start",
      actionFact("Queue",
        if (kind == EmergencyRecovery.Kind) "Immediate; bypasses brood limit."
        else "Shared laying queue; costs paid when laid."),
      actionFact("Development",
        s"${prettyTimeFromTicks(brood.totalCare / u)} base; varies with care."),
      actionFact("Larval care",
        if (brood.small) "No resources." else "20 sugar · 2 protein per egg."),
    )
  }

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
            hr(),
          )
        }
      },
      if (includeCosts) actionCosts(costSignal, requirementsSignal) else emptyNode,
      div(
        className := "d-flex flex-column text-start action-requirements",
        children <-- requirementsSignal.map { requirements =>
          val visible = requirements.filterNot(_.isCost)
          if (visible.isEmpty) Nil else List(div(className := "small text-muted text-start", "Requires")) ++ visible.map(requirement => requirementLabel(requirement))
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
          if (bonus != ActionBonus.empty) div(className := "small text-muted", "Result") else emptyNode,
          ifGreater0(bonus.sugar)(prettyNumberSimple("+", _, " sugar")),
          ifGreater0(bonus.eggs)(value => s"+${value / u} ${if (value == u) "egg" else "eggs"}"),
          ifGreater0(bonus.maxTunnelingSpace)(prettyNumberSimple("+", _, " tunneling space capacity")),
          ifGreater0(bonus.maxSugar)(prettyNumberSimple("+", _, " sugar capacity")),
          ifGreater0(bonus.maxProtein)(prettyNumberSimple("+", _, " protein capacity")),
          ifGreater0(bonus.maxWater)(prettyNumberSimple("+", _, " water capacity")),
          ifGreater0(bonus.repleteSlots.toLong)(value => s"+$value replete ${if (value == 1) "slot" else "slots"}"),
          ifGreater0(bonus.broodNurseSlots.toLong)(value => s"+$value Brood Nurse ${if (value == 1) "slot" else "slots"}"),
          ifGreater0(bonus.layingIntervalReduction)(ticks => s"−${prettyTimeFromTicks(ticks)} laying interval"),
          ifGreater0(bonus.maxEggs)(prettyNumberSimple("+", _, " brood places")),
          ifGreater0(bonus.maxAnts)(prettyNumberSimple("+", _, " colony slots")),
          ifGreater0(bonus.maxCarcasses.toLong)(value => s"+$value carcass ${if (value == 1) "slot" else "slots"}"),
          ifGreater0(bonus.aphidSlots.toLong)(value => s"+$value Aphid Tender ${if (value == 1) "slot" else "slots"}"),
          ifGreater0(bonus.seedForagerSlots.toLong)(value => s"+$value Seed Forager ${if (value == 1) "slot" else "slots"}"),
          ifGreater0(bonus.royalBroodSlots.toLong)(value => s"+$value royal brood ${if (value == 1) "slot" else "slots"}"),
        )
      },
    )

  private def tasksDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "d-grid gap-2",
      actionAmountControl("tasks-action-amount", tasksMultiplier),
      child.maybe <-- upgradesSignal.map(_(UpgradeType.DefaultWorkers).unlocked).distinct.map {
        unlocked => Option.when(unlocked) {
          div(
            className := "default-workers",
            div(className := "info-heading", label(forId := "default-worker-task", "Default task"),
              infoDetails("Default task", div("New and returning workers join this task when it has room. They remain available for expeditions and other assignments."))),
            select(
              idAttr := "default-worker-task",
              className := "form-select",
              children <-- antsSignal.map(ants => (ants.defaultTaskChoices, ants.effectiveDefaultTask)).distinct.map {
                case (choices, current) =>
                  option(value := "idle", selected := current.isEmpty, "Keep idle") +:
                    choices.map(task => option(value := task.toString, selected := current.contains(task), task.name))
              },
              onChange.mapToValue --> { selected =>
                useSignalValue[AntsData](antsSignal, ants =>
                  Actions.selectDefaultTask(ants.defaultTaskChoices.find(_.toString == selected)))
              },
            ),
          )
        }
      },
      span(
        "Available standard workers: ",
        b(child.text <-- availableWorkersCountSignal.map(prettyNumberStr)),
      ),
      smallWorkerTasks,
      children <-- unlockedAntTasks.map(_.map(task => taskDiv(task))),
    )

  private def smallWorkerTasks: ReactiveHtmlElement[HTMLDivElement] = div(
    child.maybe <-- antsSignal.map(_.smallWorkers > 0L).distinct.map { alive => Option.when(alive) {
      div(className := "small-worker-tasks border rounded p-2 my-2",
        infoHeading("Small workers · 50%", div("Unassigned small workers collect sugar. They share scraps and nurse places with standard workers, but cannot join expeditions or promotions.")),
        div(className := "small-collector-summary", child.text <-- antsSignal.combineWith(upgradesSignal).map { case (ants, upgrades) =>
          s"${ants.smallCollectors / u} × ${resourceRateStr(Foraging.perCollector(ants, upgrades, small = true))} sugar/s"
        }),
        List(AntTask.Tunneler, AntTask.Scavenger, AntTask.BroodNurse).map { task =>
          div(className := "small-worker-job my-2",
            display <-- antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
              if (task == AntTask.Scavenger && !ants.standardWorkersUnlocked || task == AntTask.BroodNurse && nest.chambers.nurseryChamber.level == 0) "none" else "block"
            },
            span(child.text <-- antsSignal.map { ants =>
              val count = if (task == AntTask.Tunneler) ants.smallTunnelers else if (task == AntTask.Scavenger) ants.smallScavengers else ants.smallNurses
              s"${task.name}: ${count / u} "
            }),
            button(className := "btn btn-outline-primary mx-1", typ := "button", minHeight.px := 44, minWidth.px := 44, "−", aria.label := s"Remove small ${task.name}",
              disabled <-- antsSignal.map { ants =>
                (if (task == AntTask.Tunneler) ants.smallTunnelers else if (task == AntTask.Scavenger) ants.smallScavengers else ants.smallNurses) == 0L
              },
              onClick --> (event => Actions.assignSmallWorker(task, -taskMultiplier(event).toLong * u))),
            button(className := "btn btn-outline-primary mx-1", typ := "button", minHeight.px := 44, minWidth.px := 44, "+", aria.label := s"Assign small ${task.name}",
              disabled <-- antsSignal.combineWith(nestSignal).map { case (ants, nest) =>
                val current = if (task == AntTask.Tunneler) ants.smallTunnelers else if (task == AntTask.Scavenger) ants.smallScavengers else ants.smallNurses
                val max = if (task == AntTask.Tunneler) Long.MaxValue else DefaultWorkerAssignment.capacity(task, nest) - ants.taskWorkers(task)
                ants.smallCollectors < u || current >= max
              },
              onClick --> (event => Actions.assignSmallWorker(task, taskMultiplier(event).toLong * u))),
            div(className := "small text-body-secondary", task match {
              case AntTask.Tunneler => ""
              case AntTask.Scavenger => "+0.025 protein/s each"
              case _ => ""
            }),
          )
        },
      )
    } },
  )

  private def taskMultiplier(event: MouseEvent): Int =
    if (window.innerWidth <= 767) tasksMultiplier.now() else getMouseEventMultiplier(event)

  private def taskDiv(antTask: AntTask, idSuffix: String = ""): ReactiveHtmlElement[HTMLDivElement] = {
    val amountSignal: Signal[Long] =
      antsSignal.map(_.tasks.find(_._1 == antTask).map(_._2).getOrElse(0L)).distinct
    val id = s"task-${antTask.toString.toLowerCase}$idSuffix"

    def taskDescription: ReactiveHtmlElement[HTMLDivElement] =
      antTask match {
        case AntTask.SugarCollector =>
          div(
            child.text <-- amountSignal.combineWith(upgradesSignal, antsSignal).map { case (amount, upgrades, ants) =>
              val count = amount / u
              s"${preciseRateStr(Foraging.perCollector(ants, upgrades, small = false))} sugar/s per collector · next: ${preciseRateStr(Foraging.nextPerSecond(count, upgrades, ants.lineage))}"
            },
          )
        case AntTask.AphidTender =>
          div(child.text <-- antsSignal.map(a => s"${resourceRateStr(Prestige.sugar(8000L, a.lineage))} sugar/s · −0.01 protein/s per tender"))
        case AntTask.Tunneler =>
          div(
            child.text <-- upgradesSignal.map { upgrades =>
              s"+${preciseRateStr(ColonyDevelopment.tunnelingPerWorkerTick(upgrades) * TicksPerSecond)} tunneling space per second / ant"
            }
          )
        case AntTask.WaterCollector => div("+0.15 water/s per worker · 5 places")
        case AntTask.SeedForager =>
          div(child.text <-- upgradesSignal.map(upgrades =>
            s"${preciseRateStr(OutpostProgression.proteinPerWorkerSecond(upgrades))} protein/s · −${preciseRateStr(OutpostProgression.waterPerWorkerSecond(upgrades))} water/s per forager"))
        case AntTask.Scavenger =>
          div("+0.05 protein/s per standard gatherer. Two shared places, including small gatherers.")
        case AntTask.BroodNurse =>
          div("Supports two additional brood at base speed. Food shortages pause development.")
        case AntTask.TerritoryForager =>
          div("Holding contested ground. Manage staffing and withdrawal in Territory.")
        case AntTask.TrailForager =>
          div("Following a nectar trail. Manage workers in Explore.")
        case AntTask.RoyalNurse =>
          div("Tends royal brood. Feeding costs and progress are shown in Nest.")
        case AntTask.CarcassCarrier =>
          div("Returning with a carcass.")
        case AntTask.Explorer =>
          div("Workers collecting resources at a mapped site.")
      }

    div(
      idAttr := id,
      className := "row task-row",
      span(
        className := "col-6",
        antTask.name,
        nbsp,
        "(",
        child.text <-- amountSignal.combineWith(nestSignal, antsSignal).map { case (amount, nest, ants) =>
          if (DefaultWorkerAssignment.capacity(antTask, nest) != Long.MaxValue) s"${amount / u} / ${DefaultWorkerAssignment.availableCapacity(ants, antTask, nest) / u}"
          else prettyNumberStr(amount)
        },
        ")",
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
            disabled <-- availableWorkersCountSignal.combineWith(antsSignal, nestSignal).map { case (available, ants, nest) =>
              available < u || ants.effectiveDefaultTask.contains(antTask) ||
                ants.taskWorkers(antTask) >= DefaultWorkerAssignment.availableCapacity(ants, antTask, nest)
            },
            onClick --> { mouseEvent =>
              Actions.incrementAntTask(antTask, taskMultiplier(mouseEvent) * u)
            }
          ),
        ),
      ),
      div(className := "task-rate small text-body-secondary", antTask match {
        case AntTask.SugarCollector => div(child.text <-- antsSignal.combineWith(upgradesSignal).map { case (ants, upgrades) => s"${preciseRateStr(Foraging.perCollector(ants, upgrades, small = false))} sugar/s each" })
        case AntTask.Tunneler => div(child.text <-- upgradesSignal.map(upgrades => s"+${preciseRateStr(ColonyDevelopment.tunnelingPerWorkerTick(upgrades) * TicksPerSecond)} space/s each"))
        case AntTask.Scavenger => div("+0.05 protein/s each")
        case AntTask.AphidTender => div(child.text <-- antsSignal.map(a => s"+${resourceRateStr(Prestige.sugar(8000L, a.lineage))} sugar/s · −0.01 protein/s each"),
          child.maybe <-- proteinSignal.combineWith(amountSignal).map { case (protein, amount) => Option.when(protein.amount == 0L && amount > 0L)(div(className := "text-danger", "Needs protein")) })
        case AntTask.WaterCollector => div("+0.15 water/s each")
        case AntTask.SeedForager => div(child.text <-- upgradesSignal.map(upgrades => s"+${preciseRateStr(OutpostProgression.proteinPerWorkerSecond(upgrades))} protein/s · −${preciseRateStr(OutpostProgression.waterPerWorkerSecond(upgrades))} water/s each"),
          child.maybe <-- antsSignal.combineWith(resourcesSignal).map { case (ants, resources) =>
            if (ants.taskWorkers(antTask) == 0L) None
            else if (resources.protein.atCapacity) Some(div("Protein full"))
            else Option.when(resources.water.amount == 0L)(div(className := "text-danger", "Needs water")) })
        case _ => emptyNode
      }),
      div(className := "col-12 task-info", infoDetails(antTask.name, div(className := "task-description", taskDescription))),
    )
  }

  private def upgradesDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def createUpgrade(upgradeType: UpgradeType): ReactiveHtmlElement[HTMLDivElement] = {
      val costSignal: Signal[ActionCost] = Val(upgradeType.cost)
      actionButton(
        name = upgradeType.title,
        enabledSignal = canPurchaseUpgradeSignal(upgradeType),
        descriptionSignal = Val(Some(div(upgradeType.description))),
        costSignal = costSignal,
        bonusSignal = Val(ActionBonus.empty),
        onClickAction = _ => Actions.unlockUpgrade(upgradeType),
      )
    }

    div(
      className := "row upgrades-grid",
      children <-- upgradesSignal.map { upgrades =>
        val available = UpgradeType.all.filter(t => upgrades(t).show && !upgrades(t).unlocked)
        if (available.isEmpty) Seq(div(className := "col-12 py-3 upgrades-empty text-body-secondary",
          if (UpgradeType.all.forall(t => upgrades(t).unlocked))
            p(className := "mb-0", "All current upgrades researched.")
          else div(p(className := "mb-1", "No upgrades available."))))
        else available.map(t => createUpgrade(t))
      },
      detailsTag(className := "col-12 purchased-upgrades border-top mt-3 pt-2",
        summaryTag(minHeight.px := 44,
          child.text <-- upgradesSignal.map(upgrades =>
            s"Purchased upgrades (${UpgradeType.all.count(upgrades(_).unlocked)})").distinct),
        children <-- upgradesSignal.map(upgrades => UpgradeType.all.filter(upgrades(_).unlocked)).distinct.map {
          purchased =>
            if (purchased.isEmpty) Seq(p(className := "small text-body-secondary", "No upgrades purchased yet."))
            else purchased.map(upgrade => div(className := "purchased-upgrade d-flex align-items-center justify-content-between gap-2 border-bottom py-1",
              span(upgrade.title), infoDetails(upgrade.title, div(upgrade.description))))
        },
      ),
    )
  }

  private def discoveriesDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(className := "discoveries-panel",
      children <-- explorationSignal.map { data =>
        val found = DiscoveryProgression.all.filter(_.found(data))
        val surveys = DiscoveryProgression.all.filterNot(_.found(data))
          .filter(discovery => ExplorationMap.revealed(data, discovery.destination)).map { discovery =>
            (discovery.destination, ExplorationMap.site(discovery.destination).name, discovery.progress(data), discovery.trips)
          }
        (found, surveys)
      }.distinct.map { case (found, surveys) =>
        List(
          div(h5("Surveys"), if (surveys.isEmpty) p("All known areas surveyed.") else div(surveys.map {
            case (id, name, progress, trips) => div(className := "card p-2 my-2 survey-entry", dataAttr("destination") := id,
              b(name), span(className := "destination-survey", s"Survey $progress / $trips"))
          })),
          div(className := "exploration-discoveries", h5("Discoveries"),
            if (found.isEmpty) p("No discoveries yet.") else div(found.map(discovery =>
              div(className := "card p-2 my-2 discovery-entry", infoHeading(discovery.title, div(discovery.description), minor = true)))),
          ),
        )
      },
    )

  private def exploreDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val selected = Var("surroundings")
    val scoutCrew = Var(1)
    val requestedWorkers = Var(5L * u)
    val workers = requestedWorkers.signal.combineWith(availableWorkersCountSignal).map {
      case (n, available) => ExplorationProgression.selectedWorkers(n, available)
    }.distinct
    def scoutAction(id: String): ReactiveHtmlElement[HTMLDivElement] = div(
      p(className := "mission-preview", child.text <-- scoutCrew.signal.combineWith(explorationSignal).map { case (count, data) =>
        val time = prettyTimeFromTicks(ExplorationMap.scoutDuration(count, id == "surroundings"))
        if (id == "surroundings") s"$time · reveals one hexagon" else s"$time · ${prettyNumberStr(ExplorationMap.studyReward(data, id))} knowledge"
      }),
      actionButton(
        descriptionSignal = Val(None),
        name = if (id == "surroundings") "Explore Surroundings" else "Study Site",
        enabledSignal = snapshotSignal.combineWith(scoutCrew.signal).map { case (data, count) => ExplorationMap.canScout(data, id, count) },
        costSignal = scoutCrew.signal.map(ExplorationMap.scoutCost),
        bonusSignal = Val(ActionBonus.empty),
        requirementsSignal = availableScoutsSignal.combineWith(scoutCrew.signal, explorationSignal, upgradesSignal).map {
          case (available, count, data, upgrades) => List(ActionRequirement("scouts", available, count.toLong * u, isCost = true),
            ActionRequirement("mission slots", Math.max(0, ExplorationProgression.maxParties(data, upgrades) - data.explorationParties.size).toLong * u))
        },
        onClickAction = _ => Actions.scoutMap(id, scoutCrew.now()),
      ).amend(className := "col-12"),
    )
    def trailControls(route: ExplorationDestination): ReactiveHtmlElement[HTMLDivElement] = div(
      child.maybe <-- antsSignal.map(_.lineage.sharedPaths).distinct.map(known =>
        Option.when(known && !RaidProgression.isRaid(route))(div(className := "collection-trail border rounded p-2 mt-2", dataAttr("route") := route.id,
          infoHeading("Collection trail", div("60% of manual haul rate. One scout per ten workers, rounded up. Supplies and normal upkeep apply."), minor = true),
          div(className := "trail-preview small", child.text <-- snapshotSignal.map { data =>
            val count = Math.max(PheromoneTrails.Step, PheromoneTrails.target(data.exploration, route.id))
            val (rate, cost) = PheromoneTrails.nominal(route, count, data.upgrades)
            s"+${resourceRateStr(rate * TicksPerSecond)} ${ExplorationHelper.resource(route)}/s · −${resourceRateStr(cost * TicksPerSecond)} sugar/s supplies"
          }),
          div(className := "d-flex flex-wrap align-items-center gap-2 my-2",
            button(typ := "button", className := "btn btn-outline-primary", minHeight.px := 44, "−5",
              aria.label := s"Remove 5 trail workers from ${route.name}",
              disabled <-- snapshotSignal.map(data => !PheromoneTrails.canAdjust(data, route.id, -PheromoneTrails.Step)),
              onClick --> (_ => Actions.assignTrailWorkers(route.id, -PheromoneTrails.Step))),
            span(className := "trail-staffing", child.text <-- antsSignal.combineWith(explorationSignal).map { case (ants, data) =>
              s"${PheromoneTrails.staffed(ants, data, route.id) / u}/50 workers"
            }),
            button(typ := "button", className := "btn btn-outline-primary", minHeight.px := 44, "+5",
              aria.label := s"Assign 5 trail workers to ${route.name}",
              disabled <-- snapshotSignal.map(data => !PheromoneTrails.canAdjust(data, route.id, PheromoneTrails.Step)),
              onClick --> (_ => Actions.assignTrailWorkers(route.id, PheromoneTrails.Step))),
            button(typ := "button", className := "btn btn-outline-secondary", minHeight.px := 44, "Recall trail",
              disabled <-- explorationSignal.map(data => PheromoneTrails.target(data, route.id) == 0L),
              onClick --> (_ => Actions.recallTrail(route.id))),
          ),
          actionCosts(Val(ActionCost.empty), antsSignal.combineWith(explorationSignal).map { case (ants, data) =>
            val current = PheromoneTrails.target(data, route.id)
            val available = Math.min(ScoutProgression.atHome(ants, data), ScoutProgression.available(ants, data) + PheromoneTrails.requiredScouts(current))
            List(ActionRequirement("scouts", available, PheromoneTrails.requiredScouts(Math.max(PheromoneTrails.Step, current)), isCost = true))
          }),
          div(className := "trail-status small", child.text <-- snapshotSignal.map(data =>
            PheromoneTrails.forecast(data.ants, data.exploration, data.upgrades, data.basicResources)
              .find(_.destination == route.id).map(_.status).getOrElse("No workers assigned"))),
        ))),
    )
    def haul(route: ExplorationDestination): ReactiveHtmlElement[HTMLDivElement] = div(className := "site-haul my-3",
      b(route.actionName),
      p(className := "mb-1", child.text <-- explorationSignal.map(data =>
        s"${prettyNumberStr(ExplorationMap.remaining(data, route.id))} ${ExplorationHelper.resource(route)} remaining")),
      p(className := "mission-preview", child.text <-- snapshotSignal.combineWith(workers).map { case (data, count) =>
        ExplorationHelper.summary(data, count, route)
      }),
      if (RaidProgression.isRaid(route)) div(className := "text-danger raid-risk", child.text <-- workers.map(RaidProgression.risk)) else emptyNode,
      if (RaidProgression.isRaid(route)) div(className := "text-danger raid-aging small",
        child.text <-- antsSignal.combineWith(currentTickSignal, workers).map { case (ants, tick, count) => RaidProgression.ageWarning(ants, tick, count, 0) }) else emptyNode,
      actionButton(
        descriptionSignal = Val(None),
        name = route.actionName,
        enabledSignal = snapshotSignal.combineWith(workers).map { case (data, count) => ExplorationHelper.canStart(data, count, route) },
        costSignal = workers.map(ExplorationHelper.cost(_, route)), bonusSignal = Val(ActionBonus.empty),
        blockedReasonSignal = snapshotSignal.combineWith(workers).map { case (data, count) =>
          val cargo = ExplorationHelper.load(data, count, route)
          Option.when(cargo.sugar + cargo.protein + cargo.water + cargo.resin == 0L)("No storage room for this haul")
        },
        requirementsSignal = workers.combineWith(explorationSignal, upgradesSignal, availableScoutsSignal).map { case (count, data, upgrades, scouts) =>
          List(ActionRequirement("mission slots", Math.max(0, ExplorationProgression.maxParties(data, upgrades) - data.explorationParties.size).toLong * u)) ++
            Option.when(count < route.minimumWorkers)(ActionRequirement("party workers", count, route.minimumWorkers)) ++
            Option.when(route.scouts > 0L)(ActionRequirement("scout guide", scouts, route.scouts, isCost = true))
        },
        onClickAction = _ => useSignalValue[Long](workers, Actions.explore(_, route)),
      ).amend(className := "col-12"),
      trailControls(route),
    )
    def sitePanel(id: String): ReactiveHtmlElement[HTMLDivElement] = {
      if (id == "surroundings") div(className := "map-site-card card p-3",
        h5("Nest surroundings"),
        p(child.text <-- explorationSignal.map(data => s"${data.revealedSites} / 6 hexagons revealed")),
        child.maybe <-- explorationSignal.map(_.revealedSites < 6).distinct.map(more => Option.when(more)(scoutAction(id))),
        child.maybe <-- explorationSignal.map(_.revealedSites >= 6).distinct.map(done => Option.when(done)(span("First ring mapped"))),
      ) else {
        val site = ExplorationMap.site(id)
        div(className := "map-site-card card p-3", h5(site.name),
          p(className := "site-knowledge", child.text <-- explorationSignal.map(data => s"${prettyNumberStr(ExplorationMap.knowledgeRemaining(data, id))} knowledge remaining")),
          child.maybe <-- explorationSignal.map(data => ExplorationMap.studyReward(data, id) > 0L).distinct.map(more => Option.when(more)(scoutAction(id))),
          child.maybe <-- explorationSignal.combineWith(antsSignal, nestSignal).map { case (data, ants, nest) =>
            site.routes.flatMap(id => ExplorationDestination.all.find(_.id == id)).exists(route =>
              route.isUnlocked(data, ants, nest) && ExplorationMap.remaining(data, route.id) > 0L)
          }.distinct.map(active => Option.when(active)(
          div(className := "site-worker-controls my-2",
            label(forId := "map-worker-count", "Collection crew"),
            div(className := "d-flex gap-2 align-items-center",
              button(className := "btn btn-outline-secondary", typ := "button", aria.label := "Decrease collection workers", "−5",
                disabled <-- workers.map(_ <= ExplorationProgression.MinWorkers),
                onClick --> (_ => useSignalValue[Long](workers, n => requestedWorkers.set(Math.max(ExplorationProgression.MinWorkers, n - ExplorationProgression.WorkerStep))))),
              input(idAttr := "map-worker-count", className := "form-control", typ := "number", inputMode := "numeric", minAttr := "5", stepAttr := "5",
                maxAttr <-- availableWorkersCountSignal.map(n => (ExplorationProgression.selectedWorkers(ExplorationProgression.MaxWorkers, n) / u).toString),
                disabled <-- availableWorkersCountSignal.map(_ < ExplorationProgression.MinWorkers),
                value <-- workers.map(n => (n / u).toString),
                onChange --> (event => {
                  val field = event.target.asInstanceOf[HTMLInputElement]
                  useSignalValue[Long](availableWorkersCountSignal, available => {
                    val count = ExplorationProgression.selectedWorkers(field.value.toLongOption.getOrElse(5L) * u, available)
                    field.value = (count / u).toString
                    requestedWorkers.set(count)
                  })
                })),
              button(className := "btn btn-outline-secondary", typ := "button", aria.label := "Increase collection workers", "+5",
                disabled <-- workers.combineWith(availableWorkersCountSignal).map { case (count, available) => count >= ExplorationProgression.selectedWorkers(ExplorationProgression.MaxWorkers, available) },
                onClick --> (_ => useSignalValue[Long](workers, n => requestedWorkers.set(Math.min(ExplorationProgression.MaxWorkers, n + ExplorationProgression.WorkerStep))))),
            ),
          )
          )),
          site.routes.flatMap(route => ExplorationDestination.all.find(_.id == route)).map { route =>
            child.maybe <-- explorationSignal.combineWith(antsSignal, nestSignal).map { case (data, ants, nest) =>
              route.isUnlocked(data, ants, nest) && ExplorationMap.remaining(data, route.id) > 0L
            }.distinct.map(available => Option.when(available)(haul(route)))
          },
          child.maybe <-- explorationSignal.map(data => ExplorationMap.exhausted(data, id)).distinct.map(done => Option.when(done)(p("Site exhausted"))),
        )
      }
    }
    div(className := "row exploration-layout hex-exploration",
      div(className := "col-12 p-3",
        infoHeading("Local map", div("Scouts reveal hexagons and study sites. Knowledge and deposits are finite. Workers collect resources. Scout crews of 2–3 finish sooner and cost more supplies. Local scouting still consumes sugar upkeep.")),
        div(className := "hex-map", role := "group", aria.label := "First exploration ring",
          button(className := "map-hex nest-hex", typ := "button", left.percent := 50, top.percent := 50,
            aria.label := "Nest surroundings",
            aria.pressed <-- selected.signal.map(id => (id == "surroundings").toString),
            MapIcons("surroundings"), span("Nest"), onClick --> (_ => selected.set("surroundings"))),
          ExplorationMap.sites.map { site =>
            button(className := "map-hex", typ := "button", dataAttr("site") := site.id,
              left.percent := site.x, top.percent := site.y,
              disabled <-- explorationSignal.map(data => !ExplorationMap.revealed(data, site.id)),
              aria.label <-- explorationSignal.map(data =>
                if (!ExplorationMap.revealed(data, site.id)) "Unexplored hexagon"
                else s"${site.name}: ${if (ExplorationMap.exhausted(data, site.id)) "exhausted" else if (ExplorationMap.knowledgeRemaining(data, site.id) > 0L) "knowledge remaining" else "resource deposits"}"),
              aria.pressed <-- selected.signal.map(id => (id == site.id).toString),
              className <-- explorationSignal.map(data => if (!ExplorationMap.revealed(data, site.id)) "unexplored" else if (ExplorationMap.exhausted(data, site.id)) "exhausted" else "revealed"),
              child <-- explorationSignal.map(data => ExplorationMap.revealed(data, site.id)).distinct.map(known =>
                MapIcons(if (known) site.id else "fog")),
              span(className := "map-terrain-name", child.text <-- explorationSignal.map(data =>
                if (!ExplorationMap.revealed(data, site.id)) "" else site.id match {
                  case "woodland-edge" => "Woodland"
                  case "nectar-meadow" => "Meadow"
                  case "rotten-log" => "Deadwood"
                  case "dry-clearing" => "Dry ground"
                  case "fallen-pine" => "Pine"
                  case _ => "Rivals"
                })),
              small(className := "map-state", aria.hidden := true, child.text <-- explorationSignal.map(data =>
                if (!ExplorationMap.revealed(data, site.id)) "" else if (ExplorationMap.exhausted(data, site.id)) "✓"
                else if (ExplorationMap.knowledgeRemaining(data, site.id) > 0L) "Study" else "Collect")),
              onClick --> (_ => selected.set(site.id)),
            )
          },
        ),
        div(className := "scout-crew-control d-flex flex-wrap gap-2 align-items-center mb-2",
          span("Scout crew"),
          div(idAttr := "scout-crew", role := "group", aria.label := "Scout crew",
            className := "btn-group",
            (1 to ExplorationMap.MaxSurveyScouts).map(n => button(
              typ := "button", className := "btn", minWidth.px := 44, minHeight.px := 44,
              className("btn-primary") <-- scoutCrew.signal.map(_ == n),
              className("btn-outline-primary") <-- scoutCrew.signal.map(_ != n),
              aria.label := s"$n ${if (n == 1) "scout" else "scouts"}",
              aria.pressed <-- scoutCrew.signal.map(current => (current == n).toString), n.toString,
              onClick --> (_ => scoutCrew.set(n)),
            )),
          ),
          span(className := "small", child.text <-- availableScoutsSignal.combineWith(scoutsSignal).map {
            case (available, total) => s"${available / u} / ${total / u} available"
          }),
        ),
        child <-- selected.signal.map(sitePanel),
        div(className := "mission-status my-3",
          b("Missions "), span(child.text <-- explorationSignal.combineWith(upgradesSignal).map { case (data, upgrades) => s"${data.explorationParties.size} / ${ExplorationProgression.maxParties(data, upgrades)}" }),
          children <-- explorationSignal.map(_.explorationParties.map(party =>
            div(className := "map-mission p-2",
              b(if (party.mission == "reveal") "Exploring surroundings" else if (party.mission == "study") s"Studying ${ExplorationMap.site(party.destination).name}" else ExplorationDestination.forParty(party).name),
              s" · ${party.scouts / u} scouts · ${party.amountWorkers / u} workers · ",
              span(child.text <-- currentTickSignal.map(tick => prettyTimeFromTicks(Math.max(0L, party.targetTick - tick)))),
              if (party.mission == "haul" && RaidProgression.isRaid(ExplorationDestination.forParty(party)))
                div(className := "text-danger small", RaidProgression.risk(party.amountWorkers)) else emptyNode,
            )
          )),
        ),
      ),
      child.maybe <-- ifUnlockedOpt(explorationSignal.map(_.carcassDiscovered)) {
        div(
          className := "col-12 px-3 py-2 carcass-panel",
          h5("Discovered"),
          children <-- explorationSignal.map(_.discoveredCarcasses.zipWithIndex.map { case (carcass, index) =>
            div(
              className := "card p-2 my-2 discovered-carcass",
              b("Dead insect"),
              div(s"Sugar: ${prettyNumberStr(carcass.totalFood)} · Protein: ${prettyNumberStr(carcass.proteinReward)}"),
              div(
                className := "row",
                actionButton(
                  name = "Retrieve Carcass",
                  requirementsSignal = explorationSignal.combineWith(nestSignal).map { case (exploration, nest) =>
                    List(ActionRequirement("carcass slots",
                      Math.max(0L, nest.maxCarcasses.toLong - CarcassProgression.occupiedSlots(exploration)) * u))
                  }.distinct,
                  enabledSignal = antsSignal.combineWith(nestSignal, explorationSignal)
                    .map { case (ants, nest, exploration) => CarcassProgression.canRetrieve(ants, nest, exploration, carcass) },
                  descriptionSignal = Val(Some(div(s"${prettyTimeFromTicks(carcass.retrievalTicks)} · ", prettyNumberInt(carcass.requiredWorkers), " carriers"))),
                  costSignal = Val(ActionCost(idleWorkers = carcass.requiredWorkers)),
                  bonusSignal = Val(ActionBonus.empty),
                  onClickAction = _ => Actions.retrieveCarcass(index),
                ),
              ),
            )
          }),
          children <-- explorationSignal.map(_.retrievals.map { retrieval =>
            div(
              className := "card p-2 my-2 carcass-retrieval",
              b("Returning: ", prettyNumberInt(retrieval.carcass.requiredWorkers), " carriers"),
              child.text <-- currentTickSignal.map(tick => prettyTimeFromTicks(Math.max(0L, retrieval.targetTick - tick))),
            )
          }),
          infoHeading("Carcass storage", div("Returning parties reserve a slot. Carcass sugar is consumed before stored sugar.")),
          small(className := "text-body-secondary carcass-capacity",
            child.text <-- explorationSignal.combineWith(nestSignal)
              .map { case (exploration, nest) => s"${CarcassProgression.occupiedSlots(exploration)} / ${nest.maxCarcasses}" }),
          div(
            className := "text-body-secondary carcass-storage-summary",
            child.text <-- explorationSignal.map(data =>
              s"${data.carcasses.size} stored · ${data.retrievals.size} returning"),
          ),
          children <-- explorationSignal.map { exploration =>
            if (exploration.carcasses.isEmpty) Seq(p("No carcasses at home."))
            else exploration.carcasses.map { carcass =>
              div(
                className := "card p-2 my-2 stored-carcass",
                b("Dead insect"),
                div(s"Sugar remaining: ${prettyNumberStr(carcass.foodRemaining)} / ${prettyNumberStr(carcass.totalFood)}"),
                div(className := "progress my-2",
                  div(className := "progress-bar", role := "progressbar",
                    aria.label := "Carcass sugar remaining",
                    width.percent := (100.0 * carcass.foodRemaining / carcass.totalFood))),
              )
            }
          },
        )
      },
    )
  }

}
