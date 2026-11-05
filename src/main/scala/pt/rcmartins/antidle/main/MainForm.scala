package pt.rcmartins.antidle.main

import com.raquo.airstream.ownership.OneTimeOwner
import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.modifiers.EventListener
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.querki.jquery
import org.scalajs.dom
import org.scalajs.dom._
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
  private val mobileMultiplier = Var(1)

  private val modifiersMultiplierSignal: Signal[Int] =
    ctrlPressedVar.signal
      .combineWith(shiftPressedVar.signal, mobileMultiplier.signal, windowWidthVar.signal)
      .map { case (ctrl, shift, mobile, width) =>
        if (width <= 767) mobile else getMouseEventMultiplier(ctrl, shift)
      }

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
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            li(className := "nav-item",
              a(className := "nav-link", idAttr := "discoveries-tab", dataAttr("bs-toggle") := "tab",
                href := "#discoveriesContent", role := "tab", "Discoveries")),
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
          child.maybe <-- ifUnlockedOpt(discoveriesVisibleSignal)(
            div(className := "tab-pane", idAttr := "discoveriesContent", role := "tabpanel", discoveriesDiv),
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
          Option.when(unlocks.resources.showKnowledge)("Knowledge earned" -> prettyNumberStr(stats.knowledgeU.thisRun)),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Expeditions launched" -> stats.explorationsCount.thisRun.toString),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Expeditions completed" -> stats.explorationsCompleted.thisRun.toString),
          Option.when(unlocks.tabs.exploreTabUnlocked)("Workers sent" -> prettyNumberStr(stats.explorationsWorkersSentU.thisRun)),
          Option.when(unlocks.resources.showEggs)("Eggs raised" -> stats.eggsCount.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Workers matured" -> stats.workersMatured.thisRun.toString),
          Option.when(unlocks.resources.showScouts)("Scouts matured" -> stats.scoutsMatured.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Peak workers" -> stats.maxAliveWorkers.thisRun.toString),
          Option.when(exploration.proteinDiscovered)("Carcasses retrieved" -> stats.carcassesRetrieved.thisRun.toString),
          Option.when(unlocks.resources.showWorkers)("Aging losses" -> (stats.workersLostToAging.thisRun.toLong + stats.scoutsLostToAging.thisRun).toString),
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
      className := "card game-panel",
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
          resourceSignal = sugarSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.sugarTickGain, sugarSignal)),
          showSignal = Val(true),
        ),
        child.maybe <-- createResourceDiv(
          name = "Tunneling Space",
          resourceSignal = tunnelingSpaceSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.tunnelingSpaceTickGain, tunnelingSpaceSignal)),
          showSignal = unlocksSignal.map(_.resources.showTunnelingSpace),
        ),
        child.maybe <-- createResourceDiv(
          name = "Protein",
          resourceSignal = proteinSignal,
          resourceEstimationSignal = Val(prettyResourceEstDiv(TickCalculator.proteinTickGain, proteinSignal)),
          showSignal = nurseryUnlockedSignal,
        ),
        child.maybe <-- createResourceDiv(
          name = "Water",
          resourceSignal = waterSignal,
          resourceEstimationSignal = Val(div("0/s")),
          showSignal = unlocksSignal.map(_.resources.showWater),
          tooltipOpt = Some(div("Water supplies gathered from discovered sources in the dry area.")),
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
          name = "Eggs",
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
                          b(antBlood.name + ": "),
                          child <-- currentTickSignal.combineWith(upgradesSignal).map { case (currentTick, upgrades) =>
                            prettyTimeFromTicks(
                              Math.max(0L, antBlood.initialTick + ColonyDevelopment.growthTicks(antBlood, upgrades) - currentTick)
                            )
                          },
                          " to grow to next stage."
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
          name = "Workers",
          resourceSignal = workersResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showWorkers)
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
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-3 text-end text-nowrap resource-amount",
              className <-- resourceSignal.map(resource =>
                if (resource.atCapacity) "text-primary fw-bold" else ""),
              child <-- amountSignal.map(prettyNumberFixedSize),
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

  private def actionAmountControl(controlId: String): ReactiveHtmlElement[HTMLDivElement] =
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
        value <-- mobileMultiplier.signal.map(_.toString),
        onChange.mapToValue.map(_.toInt) --> mobileMultiplier.writer,
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
      h6("Queen's Routine"),
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
      small("10 sugar / worker egg · Growing eggs count toward the target. Scouts remain manual."),
    )
  }

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
          enabledSignal = nestSignal.map(n => n.buildQueue.size < n.maxBuildQueue),
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
      div(
        className := "col-12 nest-brood border-top border-bottom my-3 py-3",
        display <-- unlocksSignal.map(u => if (u.actions.canLayEggs) "block" else "none"),
        h5("Brood"),
        child.maybe <-- antsSignal.map(_.ages.active).distinct.map(active => Option.when(active) {
          detailsTag(
            className := "colony-aging mb-2",
            summaryTag(child.text <-- antsSignal.combineWith(currentTickSignal).map {
              case (ants, tick) => PopulationAging.forecast(ants, tick)
            }),
            p(child.text <-- upgradesSignal.map(upgrades =>
              s"New adults live ${PopulationAging.lifespanText(upgrades)} minutes of simulated time. Aging continues during expeditions and carcass retrievals. Overdue ants die on return. Stored-time multipliers advance aging with the colony.")),
            p("Losses use idle workers first, then assigned workers. Deaths are summarized once per minute."),
          )
        }),
        child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.QueensRoutine).unlocked)) {
          queenRoutineControls
        },
        actionAmountControl("brood-action-amount"),
        div(
          className := "row",
          child.maybe <-- ifUnlockedOpt(_.actions.canLayEggs) {
            val displayedBatch = modifiersMultiplierSignal.combineWith(antsSignal, nestSignal, sugarSignal).map {
              case (requested, ants, nest, sugar) => Math.max(1, Actions.workerEggAmount(ants, nest, sugar.amount, requested))
            }.distinct
            actionButton(
              name = LayEggAction,
              topLeftBadgeSignal = displayedBatch.map {
                case 1    => None
                case mult => Some(span(s"x$mult"))
              },
              enabledSignal = Actions.layEggActionEnabled,
              descriptionSignal = Val(Some(div("Ants require a constant sugar supply to stay alive."))),
              costSignal = displayedBatch.map(mult => ActionCost(sugar = mult * Constants.LayEggSugarCost)),
              bonusSignal = displayedBatch.map(mult => ActionBonus(eggs = mult * u)),
              onClickAction = mult => Actions.layEggAction(mult),
              requirementsSignal = broodRequirementsSignal,
            )
          },
          child.maybe <-- ifUnlockedOpt(nestSignal.map(_.chambers.nurseryChamber.level > 0)) {
            actionButton(
              name = "Raise Scout Egg",
              enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal)
                .map { case (ants, nest, resources) => ScoutProgression.canRaise(ants, nest, resources) },
              descriptionSignal = Val(Some(div("Guides woodland expeditions. Matures in 30s."))),
              costSignal = Val(ScoutProgression.eggCost),
              bonusSignal = Val(ActionBonus.empty),
              onClickAction = _ => Actions.raiseScout(),
              requirementsSignal = broodRequirementsSignal,
            )
          },
        ),
      ),
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
          child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.AphidTending).unlocked)) {
            val cost = Actions.buildChamberCost(ChamberType.Aphid)
            actionButton(
              name = ChamberType.Aphid.name,
              enabledSignal = nestSignal.map(n => n.buildQueue.size < n.maxBuildQueue),
              descriptionSignal = Val(Some(div("Each level supports one Aphid Tender: +0.8 sugar/s, −0.01 protein/s while fed."))),
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
              enabledSignal = nestSignal.map(n => n.buildQueue.size < n.maxBuildQueue),
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
        ActionRequirement("egg slots", Math.max(0L, nest.maxEggs - ants.eggsAndLarvae.count(_.isEgg) * u)),
        ActionRequirement("colony slots", Math.max(0L, nest.maxAnts - ants.populationCount * u)),
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
  )(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "col-6 px-3 py-2 d-grid action-cell",
      button(
        className := "btn btn-primary position-relative",
        className := "fs-4",
        `type` := "button",
        name,
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
      child.maybe <-- windowWidthVar.signal.map(_ <= 767).distinct.map { mobile =>
        Option.when(mobile) {
          detailsTag(
            className := "mobile-only action-details",
            summaryTag(s"Details: $name"),
            actionDetails(descriptionSignal, costSignal, bonusSignal, requirementsSignal, includeCosts = false),
          )
        }
      },
    ).amendThis(elem =>
      createTooltipForActionButton(elem, descriptionSignal, costSignal, bonusSignal, requirementsSignal, includeCosts = false)
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
            className := "text-center",
            maxWidth.px := 350,
            descriptionDiv,
            hr(),
          )
        }
      },
      if (includeCosts) actionCosts(costSignal, requirementsSignal) else emptyNode,
      div(
        className := "d-flex flex-column text-center action-requirements",
        children <-- requirementsSignal.map(_.filterNot(_.isCost).map(requirement => requirementLabel(requirement))),
      ),
      child.maybe <-- costSignal.combineWith(bonusSignal).map { case (cost, bonus) =>
        if (!includeCosts || cost == ActionCost.empty || bonus == ActionBonus.empty)
          None
        else
          Some(hr())
      },
      child <-- bonusSignal.map { bonus =>
        div(
          className := "d-flex flex-column justify-content-center text-center",
          ifGreater0(bonus.sugar)(prettyNumberSimple("+", _, " sugar")),
          ifGreater0(bonus.eggs)(prettyNumberSimple("+", _, " eggs")),
          ifGreater0(bonus.maxTunnelingSpace)(prettyNumberSimple("+", _, " max tunneling space")),
          ifGreater0(bonus.maxSugar)(prettyNumberSimple("+", _, " max sugar")),
          ifGreater0(bonus.maxProtein)(prettyNumberSimple("+", _, " max protein")),
          ifGreater0(bonus.maxEggs)(prettyNumberSimple("+", _, " max eggs")),
          ifGreater0(bonus.maxAnts)(prettyNumberSimple("+", _, " max ants")),
          ifGreater0(bonus.maxCarcasses.toLong)(value => s"+$value carcass slot"),
          ifGreater0(bonus.aphidSlots.toLong)(value => s"+$value aphid tender slot"),
        )
      },
    )

  private def tasksDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "d-grid gap-2",
      actionAmountControl("tasks-action-amount"),
      child.maybe <-- upgradesSignal.map(_(UpgradeType.DefaultWorkers).unlocked).distinct.map {
        unlocked => Option.when(unlocked) {
          div(
            className := "default-workers",
            label(forId := "default-worker-task", "Default task"),
            select(
              idAttr := "default-worker-task",
              className := "form-select",
              children <-- antsSignal.map(_.defaultTaskChoices).distinct.map(choices =>
                option(value := "idle", "Keep idle") +:
                  choices.map(task => option(value := task.toString, task.name))),
              value <-- antsSignal.map(_.effectiveDefaultTask.map(_.toString).getOrElse("idle")).distinct,
              onChange.mapToValue --> { selected =>
                useSignalValue[AntsData](antsSignal, ants =>
                  Actions.selectDefaultTask(ants.defaultTaskChoices.find(_.toString == selected)))
              },
            ),
            small("New and returning workers join this task when it has room. Its workers remain available for other actions."),
          )
        }
      },
      span(
        "Available workers: ",
        b(child <-- availableWorkersCountSignal.map(prettyNumberFixedSize)),
      ),
      children <-- unlockedAntTasks.map(_.map(task => taskDiv(task))),
    )

  private def taskMultiplier(event: MouseEvent): Int =
    if (window.innerWidth <= 767) mobileMultiplier.now() else getMouseEventMultiplier(event)

  private def taskDiv(antTask: AntTask, idSuffix: String = ""): ReactiveHtmlElement[HTMLDivElement] = {
    val amountSignal: Signal[Long] =
      antsSignal.map(_.tasks.find(_._1 == antTask).map(_._2).getOrElse(0L)).distinct
    val id = s"task-${antTask.toString.toLowerCase}$idSuffix"

    def taskDescription: ReactiveHtmlElement[HTMLDivElement] =
      antTask match {
        case AntTask.SugarCollector =>
          div(
            child.text <-- amountSignal.combineWith(upgradesSignal).map { case (amount, upgrades) =>
              val count = amount / u
              s"${preciseRateStr(Foraging.averagePerSecond(count, upgrades))} sugar/s per collector · next: ${preciseRateStr(Foraging.nextPerSecond(count, upgrades))}"
            },
          )
        case AntTask.AphidTender =>
          div(
            "0.8 sugar/s · −0.01 protein/s per tender",
            br(),
            child.maybe <-- proteinSignal.combineWith(amountSignal).map { case (protein, amount) =>
              Option.when(protein.amount == 0L && amount > 0L)(span(className := "text-danger", "Needs protein"))
            },
          )
        case AntTask.Tunneler =>
          div(
            child.text <-- upgradesSignal.map { upgrades =>
              s"+${preciseRateStr(ColonyDevelopment.tunnelingPerWorkerTick(upgrades) * TicksPerSecond)} tunneling space per second / ant"
            }
          )
        case AntTask.CarcassCarrier =>
          div("Returning with a carcass.")
        case AntTask.Explorer =>
          div("Ants exploring the surroundings.")
      }

    div(
      idAttr := id,
      className := "row task-row",
      span(
        className := "col-6",
        antTask.name,
        child.text <-- antsSignal.map(ants => if (ants.effectiveDefaultTask.contains(antTask)) " · Default" else ""),
        nbsp,
        "(",
        child.text <-- amountSignal.combineWith(nestSignal).map { case (amount, nest) =>
          if (antTask == AntTask.AphidTender) s"${amount / u} / ${AphidHusbandry.capacity(nest) / u}"
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
                (antTask == AntTask.AphidTender && ants.taskWorkers(antTask) >= AphidHusbandry.capacity(nest))
            },
            onClick --> { mouseEvent =>
              Actions.incrementAntTask(antTask, taskMultiplier(mouseEvent) * u)
            }
          ),
        ),
      ),
      div(className := "col-4"),
      div(className := "mobile-only task-description", taskDescription),
    ).amendThis(elem => setTooltip(elem, taskDescription))
  }

  private def upgradesDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def createUpgrade(
        upgradeType: UpgradeType,
        unlockFunc: UpgradesData => UpgradesData = identity,
    ): Signal[Option[ReactiveHtmlElement[HTMLDivElement]]] = {
      val costSignal: Signal[ActionCost] = Val(upgradeType.cost)
      val upgradeSignal: Signal[UpgradeData] = upgradesSignal.map(_(upgradeType))
      ifUpgradeReadyToBuyOpt(upgradeSignal) {
        actionButton(
          name = upgradeType.title,
          enabledSignal = hasResourcesSignal(costSignal),
          descriptionSignal = Val(Some(div(upgradeType.description))),
          costSignal = costSignal,
          bonusSignal = Val(ActionBonus.empty),
          onClickAction = _ =>
            useSignalValue[ActionCost](
              costSignal,
              actionCost =>
                Actions.unlockUpgrade(actionCost, _.unlock(upgradeType).pipe(unlockFunc)),
            ),
        )
      }
    }

    div(
      className := "row",
      UpgradeType.all.map { upgradeType =>
        div(
          child.maybe <--
            createUpgrade(upgradeType)
        )
      },
    )
  }

  private def discoveriesDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(className := "discoveries-panel",
      children <-- explorationSignal.combineWith(antsSignal, nestSignal).map { case (data, ants, nest) =>
        val found = DiscoveryProgression.all.filter(_.found(data))
        val surveys = DiscoveryProgression.all.filterNot(_.found(data)).flatMap { discovery =>
          ExplorationDestination.all.find(d => d.id == discovery.destination && d.isUnlocked(data, ants, nest)).map { destination =>
            div(className := "card p-2 my-2 survey-entry", dataAttr("destination") := destination.id,
              b(destination.name), span(className := "destination-survey", s"Survey ${discovery.progress(data)} / ${discovery.trips}"))
          }
        }
        List(
          div(h5("Surveys"), if (surveys.isEmpty) p("All known areas surveyed.") else div(surveys)),
          div(className := "exploration-discoveries", h5("Discoveries"),
            if (found.isEmpty) p("Complete surveys to uncover discoveries.") else div(found.map(discovery =>
              div(className := "card p-2 my-2 discovery-entry", b(discovery.title), span(discovery.description)))),
          ),
        )
      },
    )

  private def exploreDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val amountOfExplorersVar = Var(1L * u)
    val amountOfExplorersSignal = amountOfExplorersVar.signal.combineWith(availableWorkersCountSignal)
      .map { case (requested, available) => ExplorationProgression.selectedWorkers(requested, available) }.distinct
    val extraScoutsVar = Var(0)
    val extraScoutsSignal = extraScoutsVar.signal.combineWith(availableScoutsSignal).map {
      case (requested, available) => Math.max(0, Math.min(ExplorationProgression.MaxExtraScouts, Math.min(requested, (available / u).toInt)))
    }.distinct

    def editWorkers(field: HTMLInputElement, commitEmpty: Boolean): Unit = {
      val parsed = field.value.toLongOption
      if (parsed.nonEmpty || commitEmpty) {
        val requested = parsed.map(n => Math.max(1L, Math.min(ExplorationProgression.MaxWorkers / u, n)) * u)
          .getOrElse(amountOfExplorersVar.now())
        useSignalValue[Long](availableWorkersCountSignal, available => {
          val selected = ExplorationProgression.selectedWorkers(requested, available)
          amountOfExplorersVar.set(selected)
          field.value = (selected / u).toString
        })
      }
    }

    def area(destination: ExplorationDestination): ReactiveHtmlElement[HTMLDivElement] = {
      val cost = amountOfExplorersSignal.combineWith(extraScoutsSignal).map { case (workers, extra) => ExplorationHelper.cost(workers, destination, extra) }
      div(
        className := "col-12 col-md-6 px-3 py-2 exploration-destination",
        div(
          className := "card p-2 h-100",
          p(
            className := "mb-1 destination-preview",
            child.text <-- amountOfExplorersSignal.combineWith(explorationSignal, extraScoutsSignal).map {
              case (workers, data, extra) => ExplorationHelper.summary(data, workers, destination, extra)
            },
          ),
          div(
            className := "row",
            actionButton(
              name = s"Explore ${destination.name}",
              requirementsSignal = explorationSignal.combineWith(availableScoutsSignal, amountOfExplorersSignal, upgradesSignal, extraScoutsSignal).map {
                case (data, scouts, workers, upgrades, extra) =>
                  List(ActionRequirement("expedition slots",
                    Math.max(0L, ExplorationProgression.maxParties(data.experience, upgrades).toLong - data.explorationParties.size) * u)) ++
                    Option.when(destination.scouts + extra.toLong * u > 0)(ActionRequirement("scouts", scouts, destination.scouts + extra.toLong * u, isCost = true)) ++
                    Option.when(destination.requiredLevel > 1)(ActionRequirement("exploration level",
                      ExplorationProgression.level(data.experience).toLong * u, destination.requiredLevel.toLong * u)) ++
                    Option.when(destination.minimumWorkers > u)(ActionRequirement("party workers", workers, destination.minimumWorkers))
              }.distinct,
              enabledSignal = amountOfExplorersSignal.combineWith(explorationSignal, antsSignal, resourcesSignal, upgradesSignal, extraScoutsSignal)
                .map { case (workers, data, ants, resources, upgrades, extra) =>
                  ExplorationHelper.canDepart(data, ants, upgrades, workers, destination, extra) &&
                    resources.hasResources(ExplorationHelper.cost(workers, destination, extra))
                },
              descriptionSignal = amountOfExplorersSignal.combineWith(explorationSignal, extraScoutsSignal).map {
                case (workers, data, extra) => Some(div(ExplorationHelper.preview(data, workers, destination, extra)))
              },
              costSignal = cost,
              bonusSignal = Val(ActionBonus.empty),
              onClickAction = _ => useSignalValue[(Long, Int)](amountOfExplorersSignal.combineWith(extraScoutsSignal),
                selection => Actions.explore(selection._1, destination, selection._2)),
            ).amend(className := "col-12"),
          ),
        ),
      )
    }

    div(
      className := "row",
      div(
        className := "col-12 px-3 py-2",
        label(forId := "explorer-count", "Workers"),
        div(className := "expedition-worker-stepper",
          button(className := "btn btn-outline-secondary", typ := "button",
            aria.label := "Decrease expedition workers", "−",
            disabled <-- amountOfExplorersSignal.map(_ <= u),
            onClick --> (_ => useSignalValue[Long](amountOfExplorersSignal, n => amountOfExplorersVar.set(n - u))),
          ),
          input(
            idAttr := "explorer-count", className := "form-control text-center", typ := "number",
            inputMode := "numeric", minAttr := "1", stepAttr := "1",
            maxAttr <-- availableWorkersCountSignal.map(amount => Math.max(1L, Math.min(ExplorationProgression.MaxWorkers / u, amount / u)).toString),
            disabled <-- availableWorkersCountSignal.map(_ < u),
            value <-- amountOfExplorersSignal.map(amount => (amount / u).toString),
            onInput --> (event => editWorkers(event.target.asInstanceOf[HTMLInputElement], commitEmpty = false)),
            onBlur --> (event => editWorkers(event.target.asInstanceOf[HTMLInputElement], commitEmpty = true)),
          ),
          button(className := "btn btn-outline-secondary", typ := "button",
            aria.label := "Increase expedition workers", "+",
            disabled <-- amountOfExplorersSignal.combineWith(availableWorkersCountSignal).map {
              case (amount, available) => amount >= Math.min(ExplorationProgression.MaxWorkers, available / u * u)
            },
            onClick --> (_ => useSignalValue[Long](amountOfExplorersSignal, n => amountOfExplorersVar.set(n + u))),
          ),
        ),
        div(className := "d-flex gap-2 flex-wrap worker-presets",
          List(1, 5, 17, 37).zipWithIndex.map { case (workers, index) =>
            button(className := "btn btn-outline-secondary", aria.label := s"Send $workers workers",
              s"$workers · ${index + 1}×",
              disabled <-- availableWorkersCountSignal.map(_ < workers.toLong * u),
              onClick --> (_ => amountOfExplorersVar.set(workers.toLong * u)),
            )
          },
        ),
        child.maybe <-- ifUnlockedOpt(unlocksSignal.map(_.resources.showScouts)) {
          div(className := "mt-2 expedition-scouts",
            label(forId := "extra-scouts", "Extra scouts (+10 sugar each)"),
            select(idAttr := "extra-scouts", className := "form-select",
              (0 to ExplorationProgression.MaxExtraScouts).map(extra =>
                option(value := extra.toString, if (extra == 0) "0" else s"$extra · +${extra * 25}% speed",
                  disabled <-- availableScoutsSignal.map(_ < extra.toLong * u))),
              value <-- extraScoutsSignal.map(_.toString),
              onChange.mapToValue.map(_.toInt) --> extraScoutsVar.writer,
            ),
          )
        },
      ),
      div(
        className := "col-12 px-3 py-2 exploration-progress",
        h5("Exploration experience"),
        p(className := "mb-1", child.text <-- explorationSignal.map(data => ExplorationProgression.progressText(data.experience))),
        detailsTag(
          summaryTag("XP details"),
          p(className := "mb-1 exploration-milestone",
            child.text <-- explorationSignal.map(data => ExplorationProgression.nextMilestone(data.experience))),
          p(child.text <-- explorationSignal.map { data =>
            val bonus = ExplorationProgression.bonusPercent(data.experience)
            val next = if (ExplorationProgression.level(data.experience) < ExplorationProgression.MaxLevel)
              s" Next level: +${bonus + 25}%." else ""
            s"Reward bonus: +$bonus% sugar, carcass sugar and protein.$next"
          }),
          p("Every trip earns XP. Level costs increase by 100 XP each time. Larger parties improve XP and rewards, up to 4× at the 37-worker party limit. Extra scouts add 25% travel speed each, up to three."),
        ),
      ),
      ExplorationDestination.all.map { destination =>
        child.maybe <-- explorationSignal.combineWith(antsSignal, nestSignal).map {
          case (data, ants, nest) => destination.isUnlocked(data, ants, nest)
        }.distinct.map(unlocked => Option.when(unlocked)(area(destination)))
      },
      child.maybe <-- ifUnlockedOpt(explorationSignal.map(data => ExplorationProgression.level(data.experience) >= 2)) {
        div(
          className := "col-12 px-3 py-2 expedition-supplies",
          h5("Expedition supplies"),
          p(className := "mb-1", child.text <-- explorationSignal.map(data =>
            s"Rank ${ExplorationProgression.suppliesRank(data)} · +${ExplorationProgression.suppliesRank(data) * 20}% XP and rewards")),
          child.maybe <-- explorationSignal.map(data =>
            ExplorationProgression.suppliesRank(data) < ExplorationProgression.MaxSuppliesLevel &&
              ExplorationProgression.level(data.experience) >= ExplorationProgression.suppliesRequiredLevel(data)
          ).distinct.map {
            available => Option.when(available) {
              div(
                className := "row",
                actionButton(
                  name = "Improve Expedition Supplies",
                  enabledSignal = explorationSignal.combineWith(resourcesSignal).map { case (data, resources) =>
                    ExplorationProgression.canUpgradeSupplies(data, resources)
                  },
                  requirementsSignal = explorationSignal.map(data => List(ActionRequirement("exploration level",
                    ExplorationProgression.level(data.experience).toLong * u,
                    ExplorationProgression.suppliesRequiredLevel(data).toLong * u))),
                  descriptionSignal = Val(Some(div("Permanent +20% XP and rewards for future departures."))),
                  costSignal = explorationSignal.map(ExplorationProgression.suppliesCost),
                  bonusSignal = Val(ActionBonus.empty),
                  onClickAction = _ => Actions.upgradeExpeditionSupplies(),
                ),
              )
            }
          },
        )
      },
      div(
        className := "col-12 px-3 py-2 expedition-status",
        "Expeditions: ", child.text <-- explorationSignal.map(_.explorationParties.size),
        " / ", child.text <-- explorationSignal.combineWith(upgradesSignal).map {
          case (data, upgrades) => ExplorationProgression.maxParties(data.experience, upgrades)
        },
        children <-- explorationSignal.map(_.explorationParties.map { party =>
          div(
            b(s"${ExplorationDestination.forParty(party).name}: "),
            s"${prettyNumberStr(party.amountWorkers)} workers",
            if (party.scouts > 0L) s" · ${prettyNumberStr(party.scouts)} scouts" else "", ", ",
            child.text <-- currentTickSignal.map(tick => prettyTimeFromTicks(Math.max(0L, party.targetTick - tick))),
          )
        }),
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
          h5("Carcass storage", small(className := "text-body-secondary ms-2 carcass-capacity",
            child.text <-- explorationSignal.combineWith(nestSignal)
              .map { case (exploration, nest) => s"${CarcassProgression.occupiedSlots(exploration)} / ${nest.maxCarcasses}" })),
          div(
            className := "text-body-secondary carcass-storage-summary",
            child.text <-- explorationSignal.map(data =>
              s"${data.carcasses.size} stored · ${data.retrievals.size} returning"),
          ),
          small(className := "text-body-secondary", "Returning parties reserve a slot. Carcass sugar is used before stored sugar."),
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
