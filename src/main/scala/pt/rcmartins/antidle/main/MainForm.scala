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
                child <-- idleWorkersCountSignal.map {
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
          className := "mobile-only batch-controls",
          label(
            forId := "mobile-action-amount",
            "Action amount",
            small(className := "d-block text-body-secondary", "Eggs and worker assignments"),
          ),
          select(
            idAttr := "mobile-action-amount",
            className := "form-select",
            Seq(1, 10, 25, 250).map { amount =>
              option(value := amount.toString, s"×$amount")
            },
            onChange.mapToValue.map(_.toInt) --> mobileMultiplier.writer,
          ),
        ),
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
          div(
            className := "tab-pane",
            idAttr := "settingsContent",
            role := "tabpanel",
            settingsDiv,
          ),
        )
      )
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
    def buildTaskName(buildTask: BuildTask, index: Int): ReactiveHtmlElement[HTMLSpanElement] = {
      span(
        className := "build-task",
        role := "button",
        tabIndex := 0,
        buildTask.chamberType.name,
        " [",
        TickCalculator.calculateTicksToResources(buildTask.actionCost),
        "]",
        onClick --> { _ => Actions.removeBuildTask(index) },
        onKeyDown --> { event =>
          if (event.key == "Enter" || event.key == " ") {
            event.preventDefault()
            Actions.removeBuildTask(index)
          }
        },
      ).amendThis(elem =>
        createTooltipForActionButton(
          elem = elem,
          descriptionSignal = Val(None),
          costSignal = Val(buildTask.actionCost),
          bonusSignal = Actions.getActionBonus(buildTask.chamberType),
        )
      )
    }

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
              span(seq.zipWithIndex.map { case (buildTask, index) =>
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
          resourceEstimationSignal = Val(div()),
          showSignal = nurseryUnlockedSignal,
        ),
        child.maybe <-- createResourceDiv(
          name = "Scouts",
          resourceSignal = scoutResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = nestSignal.map(_.chambers.nurseryChamber.level > 0),
        ),
        child.maybe <-- createResourceDiv(
          name = "Colony Points",
          resourceSignal = colonyPointsSignal,
          resourceEstimationSignal =
            Val(prettyResourceEstDiv(TickCalculator.colonyPointsTickGain, colonyPointsSignal)),
          showSignal = unlocksSignal.map(_.resources.showColonyPoints),
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
                          child <-- currentTickSignal.map { currentTick =>
                            prettyTimeFromTicks(
                              (antBlood.initialTick + (antBlood match {
                              case _: AntBrood.ScoutEgg => Constants.ScoutGrowthTicks
                              case _ => AntBrood.defaultTicksToGrow
                            })) - currentTick
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
          name = "Workers",
          resourceSignal = workersResourceSignal,
          resourceEstimationSignal = Val(div()),
          showSignal = unlocksSignal.map(_.resources.showWorkers)
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

    showSignal.distinct.map {
      case false =>
        None
      case true =>
        Some(
          div(
            className := "row resource-row",
            div(
              className := "col-4 text-nowrap",
              child <-- windowWidthVar.signal.map(_ <= 767).distinct.map { mobile =>
                tooltipOpt.filter(_ => mobile) match {
                  case Some(content) => detailsTag(summaryTag(name), content)
                  case None => span(name)
                }
              },
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-3 text-end text-nowrap",
              child <-- amountSignal.map(prettyNumberFixedSize),
            ).amendThis(elem => setTooltipOpt(elem, tooltipOpt)),
            div(
              className := "col-2 text-start text-body-tertiary text-nowrap ps-0",
              child <--
                maxAmountSignal.map { maxValue =>
                  if (maxValue == 0)
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
          enabledSignal = Val(true),
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
      child.maybe <-- ifUnlockedOpt(_.actions.canLayEggs) {
        actionButton(
          name = LayEggAction,
          topLeftBadgeSignal = modifiersMultiplierSignal.map {
            case 1    => None
            case mult => Some(span(s"x$mult"))
          },
          enabledSignal = Actions.layEggActionEnabled,
          descriptionSignal = Val(Some(div("Ants require a constant sugar supply to stay alive."))),
          costSignal = modifiersMultiplierSignal.map(mult =>
            ActionCost(sugar = mult * Constants.LayEggSugarCost)
          ),
          bonusSignal = modifiersMultiplierSignal.map(mult => ActionBonus(eggs = mult * u)),
          onClickAction = mult => Actions.layEggAction(mult),
        )
      },
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
      child.maybe <-- ifUnlockedOpt(nurseryUnlockedSignal) {
        val cost = Actions.buildChamberCost(ChamberType.Nursery)
        actionButton(
          name = "Nursery Chamber",
          enabledSignal = Val(true),
          descriptionSignal = Val(Some(div("A nursery supports three scouts per level. Scout eggs need protein and mature in 30 seconds."))),
          costSignal = cost,
          bonusSignal = Actions.getActionBonus(ChamberType.Nursery),
          topRightBadgeSignal = chambersSignal.map(chambers =>
            Option.when(chambers.nurseryChamber.level > 0)(span(chambers.nurseryChamber.level))),
          onClickAction = _ => useSignalValue[ActionCost](cost,
            cost => Actions.addBuildTask(BuildTask(ChamberType.Nursery, cost))),
        )
      },
      child.maybe <-- ifUnlockedOpt(nestSignal.map(_.chambers.nurseryChamber.level > 0)) {
        actionButton(
          name = "Raise Scout Egg",
          enabledSignal = antsSignal.combineWith(nestSignal, resourcesSignal)
            .map { case (ants, nest, resources) => ScoutProgression.canRaise(ants, nest, resources) },
          descriptionSignal = Val(Some(div("Raise one scout in 30 seconds. Scouts use colony and egg capacity, eat sugar, and guide woodland expeditions."))),
          costSignal = Val(ScoutProgression.eggCost),
          bonusSignal = Val(ActionBonus.empty),
          onClickAction = _ => Actions.raiseScout(),
        )
      },
    )
  }

  private def actionButton(
      name: String,
      enabledSignal: Signal[Boolean],
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
      onClickAction: Int => Unit,
      topLeftBadgeSignal: Signal[Option[ReactiveHtmlElement[HTMLElement]]] = Val(None),
      topRightBadgeSignal: Signal[Option[ReactiveHtmlElement[HTMLElement]]] = Val(None),
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
      child.maybe <-- windowWidthVar.signal.map(_ <= 767).distinct.map { mobile =>
        Option.when(mobile) {
          detailsTag(
            className := "mobile-only action-details",
            summaryTag(s"Details: $name"),
            actionDetails(descriptionSignal, costSignal, bonusSignal),
          )
        }
      },
    ).amendThis(elem =>
      createTooltipForActionButton(elem, descriptionSignal, costSignal, bonusSignal)
    )

  private def createTooltipForActionButton(
      elem: ReactiveHtmlElement[HTMLElement],
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
  ): EventListener[MouseEvent, MouseEvent] =
    setTooltip(elem, actionDetails(descriptionSignal, costSignal, bonusSignal))

  private def actionDetails(
      descriptionSignal: Signal[Option[ReactiveHtmlElement[HTMLDivElement]]],
      costSignal: Signal[ActionCost],
      bonusSignal: Signal[ActionBonus],
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
      child <-- costSignal.map { cost =>
        div(
          className := "d-flex flex-column justify-content-center text-center",
          ifGreater0(cost.protein)(prettyNumberSimple("", _, " protein")),
          ifGreater0(cost.sugar)(prettyNumberSimple("", _, " sugar")),
          ifGreater0(cost.tunnelingSpace)(prettyNumberSimple("", _, " tunneling space")),
          ifGreater0(cost.colonyPoints)(prettyNumberSimple("", _, " colony points")),
          ifGreater0(cost.idleWorkers)(prettyNumberSimple("", _, " idle workers")),
        )
      },
      child.maybe <-- costSignal.combineWith(bonusSignal).map { case (cost, bonus) =>
        if (cost == ActionCost.empty || bonus == ActionBonus.empty)
          None
        else
          Some(hr())
      },
      child <-- bonusSignal.map { bonus =>
        div(
          className := "d-flex flex-column justify-content-center text-center",
          ifGreater0(bonus.sugar)(prettyNumberSimple("+", _, " sugar")),
          ifGreater0(bonus.eggs)(prettyNumberSimple("+", _, " eggs")),
          ifGreater0(bonus.colonyPointsEachWorker)(
            prettyNumberSimple("+", _, " colony points/worker/s")
          ),
          ifGreater0(bonus.maxColonyPoints)(prettyNumberSimple("+", _, " max colony points")),
          ifGreater0(bonus.maxSugar)(prettyNumberSimple("+", _, " max sugar")),
          ifGreater0(bonus.maxEggs)(prettyNumberSimple("+", _, " max eggs")),
          ifGreater0(bonus.maxWorkers)(prettyNumberSimple("+", _, " max workers")),
          ifGreater0(bonus.maxScouts)(prettyNumberSimple("+", _, " max scouts")),
        )
      },
    )

  private def tasksDiv: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "d-grid gap-2",
      span(
        "Idle Ants: ",
        b(child <-- idleWorkersCountSignal.map(prettyNumberFixedSize)),
      ),
      children <-- unlockedAntTasks.map(_.map(task => taskDiv(task))),
    )

  private def taskMultiplier(event: MouseEvent): Int =
    if (window.innerWidth <= 767) mobileMultiplier.now() else getMouseEventMultiplier(event)

  private def taskDiv(antTask: AntTask, idSuffix: String = ""): ReactiveHtmlElement[HTMLDivElement] = {
    val amountSignal: Signal[Long] =
      antsSignal.map(_.tasks.find(_._1 == antTask).map(_._2).getOrElse(0L)).distinct
    val id = s"task-${antTask.toString.toLowerCase}$idSuffix"

    // TODO: Improve this, add bonuses dynamically, instead of constants
    def taskDescription: ReactiveHtmlElement[HTMLDivElement] =
      antTask match {
        case AntTask.SugarCollector =>
          div(prettyNumberSimple("+", defaultTaskCollectSugarSecond, " sugar per second / ant"))
        case AntTask.Tunneler =>
          div(
            prettyNumberSimple(
              "+",
              defaultTaskTunnelingSpaceSecond,
              " tunneling space per second / ant"
            )
          )
        case AntTask.CarcassHarvester =>
          div("Harvests 0.8 food per second. Food offsets sugar upkeep; surplus is consumed. Protein is recovered when the carcass is exhausted.")
        case AntTask.Explorer =>
          div("Ants exploring the surroundings.")
      }

    div(
      idAttr := id,
      className := "row task-row",
      span(
        className := "col-6",
        antTask.name,
        nbsp,
        "(",
        child <-- amountSignal.map(prettyNumberInt),
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
            disabled <-- amountSignal.map(_ == 0),
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
            disabled <-- idleWorkersCountSignal.combineWith(explorationSignal).map {
              case (idle, exploration) => idle == 0 ||
                (antTask == AntTask.CarcassHarvester && exploration.carcasses.isEmpty)
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
          topRightBadgeSignal =
            costSignal.map(actionCost => Some(prettyNumberSimple(actionCost.colonyPoints))),
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

  private def exploreDiv(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val amountOfExplorersVar = Var(1L * u)

    def area(name: String, distant: Boolean): ReactiveHtmlElement[HTMLDivElement] = {
      val cost = amountOfExplorersVar.signal.map(ExplorationHelper.cost(_, distant))
      actionButton(
        name = name,
        enabledSignal = hasResourcesSignal(cost).combineWith(explorationSignal, availableScoutsSignal)
          .map { case (resources, exploration, scouts) =>
            resources && exploration.explorationParties.size < Constants.MaxExplorationParties &&
              (!distant || scouts >= u)
          },
        descriptionSignal = Val(Some(div(if (distant)
          "A 45-second expedition guided by one scout. Brings back an insect with twice the food and protein, if carcass storage has room."
        else "A 15-second expedition. The first trip finds a dead insect; later trips may find insects, sugar, or nothing."))),
        costSignal = cost,
        bonusSignal = Val(ActionBonus.empty),
        onClickAction = _ => useSignalValue(amountOfExplorersVar.signal,
          workers => Actions.explore(workers, distant)),
      )
    }

    div(
      className := "row",
      div(
        className := "col-12 px-3 py-2",
        p("Explore, harvest a dead insect, and recover protein to build a Nursery and raise scouts."),
        label(
          forId := "explorer-count",
          "Workers to send: ",
          b(child <-- amountOfExplorersVar.signal.map(prettyNumberInt)),
        ),
        input(
          idAttr := "explorer-count",
          className := "form-range",
          `type` := "range",
          MinAttr := "1",
          MaxAttr <-- idleWorkersCountSignal.map(amount => Math.max(1L, amount / u).toString),
          disabled <-- idleWorkersCountSignal.map(_ < u),
          value <-- amountOfExplorersVar.signal.map(amount => (amount / u).toString),
          onInput.mapToValue.map(_.toLong * u) --> amountOfExplorersVar.writer,
        ),
      ),
      area("Explore Surroundings", distant = false),
      child.maybe <-- ifUnlockedOpt(nestSignal.map(_.chambers.nurseryChamber.level > 0)) {
        div(
          className := "col-12 px-0",
          p(className := "px-3", "Available scouts: ",
            b(child <-- availableScoutsSignal.map(prettyNumberInt)),
            ". Raise a scout egg in the Nest to guide a woodland expedition."),
          div(className := "row mx-0", area("Explore Woodland Edge", distant = true)),
        )
      },
      div(
        className := "col-12 px-3 py-2",
        "Expeditions: ", child.text <-- explorationSignal.map(_.explorationParties.size),
        s" / ${Constants.MaxExplorationParties}",
        children <-- explorationSignal.map(_.explorationParties.map { party =>
          div(
            b(if (party.scouts > 0) "Woodland edge: " else "Surroundings: "),
            s"${prettyNumberStr(party.amountWorkers)} workers", ", ",
            child.text <-- currentTickSignal.map(tick => prettyTimeFromTicks(Math.max(0L, party.targetTick - tick))),
          )
        }),
      ),
      child.maybe <-- ifUnlockedOpt(explorationSignal.map(_.carcassDiscovered)) {
        div(
          className := "col-12 px-3 py-2",
          h5("Carcass harvesting"),
          p("Harvesters work on one insect at a time. Food offsets sugar upkeep; surplus food is consumed. Exhausting an insect yields protein. Up to three insects can be stored."),
          taskDiv(AntTask.CarcassHarvester, "-explore"),
          div(
            children <-- explorationSignal.map { exploration =>
              if (exploration.carcasses.isEmpty) Seq(p("No carcasses left. Explore to find another insect."))
              else exploration.carcasses.zipWithIndex.map { case (carcass, index) =>
                div(
                  className := "card p-2 my-2",
                  b(if (index == 0) "Dead insect — ready to harvest" else "Dead insect — stored"),
                  div(s"Food remaining: ${prettyNumberStr(carcass.foodRemaining)} / ${prettyNumberStr(carcass.totalFood)}"),
                  div(className := "progress my-2",
                    div(className := "progress-bar", role := "progressbar",
                      aria.label := "Carcass food remaining",
                      width.percent := (100.0 * carcass.foodRemaining / carcass.totalFood))),
                  div(s"Protein on completion: ${prettyNumberStr(carcass.proteinReward)}"),
                )
              }
            },
          ),
        )
      },
    )
  }

}
