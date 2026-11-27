package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{svg => _, _}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.ActionBlockers.{Requirement => ActionRequirement}
import pt.rcmartins.antidle.game.UINumbersUtils._
import pt.rcmartins.antidle.game.UIUtils._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import pt.rcmartins.antidle.model._

private[main] object BroodView {
  import MainForm.{
    actionAmountControl,
    actionButton,
    actionFact,
    broodMultiplier,
    detailsTag,
    modifiersMultiplierSignal,
    rememberDetails,
    summaryTag,
    workerCasteIcon
  }

  private def queenRoutineControls(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def update(change: QueenRoutine => QueenRoutine): Unit =
      useSignalValue[AntsData](
        antsSignal,
        ants => {
          val next = change(ants.queenRoutine)
          Actions.configureQueenRoutine(next.enabled, next.targetWorkers, next.sugarReserve)
        }
      )
    div(
      className := "queen-routine mb-3",
      h6("Queen's Routine"),
      child.maybe <-- antsSignal.map(_.agingJobVacancies).distinct.map { vacancies =>
        Option.when(vacancies.nonEmpty) {
          div(
            className := "aging-job-replacements small mb-2",
            span(
              "Awaiting workers: " + vacancies
                .map { case (task, amount) => s"${amount / u} ${task.name}" }
                .mkString(" · ")
            ),
            button(
              className := "btn btn-outline-secondary btn-sm ms-2",
              typ := "button",
              minHeight.px := 44,
              "Cancel replacements",
              onClick --> (_ => Actions.cancelAgingJobReplacements())
            ),
          )
        }
      },
      label(
        className := "queen-routine-toggle",
        input(
          idAttr := "queen-routine-enabled",
          typ := "checkbox",
          className := "form-check-input me-2",
          checked <-- antsSignal.map(_.queenRoutine.enabled).distinct,
          onChange.mapToChecked --> (enabled => update(_.copy(enabled = enabled))),
        ),
        "Automatic worker eggs",
      ),
      div(
        className := "row g-2 mt-1",
        div(
          className := "col-6",
          label(forId := "queen-routine-target", "Target workers"),
          input(
            idAttr := "queen-routine-target",
            typ := "number",
            inputMode := "numeric",
            minAttr := "0",
            maxAttr := "1000000",
            className := "form-control",
            value <-- antsSignal.map(_.queenRoutine.targetWorkers.toString).distinct,
            onChange.mapToValue --> (value =>
              update(_.copy(targetWorkers = value.toIntOption.getOrElse(0)))
            ),
          ),
        ),
        div(
          className := "col-6",
          label(forId := "queen-routine-reserve", "Sugar reserve"),
          input(
            idAttr := "queen-routine-reserve",
            typ := "number",
            inputMode := "numeric",
            minAttr := "0",
            maxAttr := "1000000000",
            className := "form-control",
            value <-- antsSignal.map(a => (a.queenRoutine.sugarReserve / u).toString).distinct,
            onChange.mapToValue --> (value =>
              update(
                _.copy(sugarReserve =
                  Math.max(0L, Math.min(1000000000L, value.toLongOption.getOrElse(0L))) * u
                )
              )
            ),
          ),
        ),
      ),
      div(
        className := "queen-routine-status small",
        child.text <-- antsSignal.combineWith(nestSignal, sugarSignal).map {
          case (ants, nest, sugar) => AutomaticBrood.status(ants, nest, sugar.amount)
        }
      ),
    )
  }

  private val broodRequirementsSignal: Signal[List[ActionRequirement]] =
    antsSignal
      .combineWith(nestSignal)
      .map { case (ants, nest) =>
        List(
          ActionRequirement("brood places", BroodProgression.openPlaces(ants, nest).toLong * u),
          ActionRequirement("colony slots", BroodProgression.availableColonySlots(ants, nest)),
        )
      }
      .distinct

  // Related actions share fields and order; values come from the gameplay model.
  private def eggDescription(kind: String): ReactiveHtmlElement[HTMLDivElement] = {
    val brood = AntBrood.Developing(0L, kind)
    div(
      className := "egg-description text-start",
      actionFact(
        "Queue",
        if (kind == EmergencyRecovery.Kind) "Bypasses brood limit."
        else "Shared laying queue; costs paid when laying starts."
      ),
      div(
        className := "action-detail-row",
        b("Laying time: "),
        child.text <-- antsSignal
          .combineWith(nestSignal, upgradesSignal)
          .map { case (ants, nest, upgrades) =>
            prettyTimeFromTicks(
              if (kind == EmergencyRecovery.Kind) EmergencyRecovery.layingTime(ants, nest, upgrades)
              else BroodProgression.interval(ants, nest, upgrades)
            )
          }
          .distinct
      ),
      actionFact(
        "Development",
        s"${prettyTimeFromTicks(brood.totalCare / u)} base; varies with care."
      ),
      actionFact(
        "Larval care",
        if (brood.small) "No resources." else "20 sugar · 2 protein per egg."
      ),
    )
  }

  def render(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    def eggCounts(scout: Boolean): ReactiveHtmlElement[HTMLSpanElement] = {
      val counts = antsSignal.map { ants =>
        val small = !ants.standardWorkersUnlocked
        val brood = ants.eggsAndLarvae.count {
          case b: AntBrood.Developing =>
            if (scout) b.kind == "scout" else if (small) b.small else !b.small && b.kind != "scout"
          case _: AntBrood.ScoutEgg => scout
          case _                    => !scout && !small
        }
        val adults = if (scout) ants.scouts else if (small) ants.smallWorkers else ants.workers
        (if (scout) ants.scoutEggOrders.size else ants.eggOrders, brood, adults)
      }.distinct
      span(
        className := "brood-action-counts",
        title <-- counts.map { case (ordered, brood, adults) =>
          s"Ordered: $ordered · Brood: $brood · Adults: ${adults / u}"
        },
        span(
          className := "egg-count-ordered",
          span(
            className := "resource-icon",
            aria.hidden := true,
            svg.svg(svg.viewBox := "0 0 24 24", svg.path(svg.d := "M4 5h16M4 12h16M4 19h16"))
          ),
          span(className := "visually-hidden", "Ordered: "),
          child.text <-- counts.map(c => prettyNumberStr(c._1.toLong * u))
        ),
        span(
          className := "egg-count-brood",
          ResourceIcons.icon(ResourceIcons.Eggs),
          span(className := "visually-hidden", "Brood: "),
          child.text <-- counts.map(c => prettyNumberStr(c._2.toLong * u))
        ),
        span(
          className := "egg-count-adults",
          if (scout) ResourceIcons.icon(ResourceIcons.Scouts) else workerCasteIcon,
          span(className := "visually-hidden", "Adults: "),
          child.text <-- counts.map(c => prettyNumberStr(c._3))
        ),
      )
    }

    val scoutOrdersVisible = antsSignal
      .combineWith(nestSignal)
      .map { case (ants, nest) =>
        ants.standardWorkersUnlocked && nest.chambers.nurseryChamber.level > 0
      }
      .distinct
    val broodPriorityVisible = nestSignal.map(BroodProgression.priorityAvailable).distinct

    div(
      className := "col-12 nest-brood border-top border-bottom my-3 py-3",
      display <-- unlocksSignal.map(u => if (u.actions.canLayEggs) "block" else "none"),
      child.maybe <-- sugarSignal.combineWith(TickCalculator.sugarTickGain.sum).map {
        case (sugar, net) =>
          Option.when(net < 0L && sugar.amount < -net) {
            div(
              className := "starvation-warning text-danger small mb-2",
              b("Colony is starving"),
              s" · Sugar deficit: ${resourceRateStr(-net * TicksPerSecond)}/s"
            )
          }
      },
      child.maybe <-- antsSignal.combineWith(currentTickSignal).map { case (ants, tick) =>
        val ages = ants.ages
        val soon = (ages.workers ++ ages.smallWorkers ++ ages.scouts ++ ages.repletes)
          .exists(c => c.count > 0L && c.expiresAt <= tick + 5L * PopulationAging.Minute)
        Option.when(ages.active && soon)(
          div(
            className := "aging-warning small text-danger mb-2",
            PopulationAging.forecast(ants, tick)
          )
        )
      },
      div(
        className := "brood-counts mb-2",
        div(
          className := "brood-order-count",
          span(className := "small text-body-secondary", "Egg orders"),
          div(
            className := "brood-order-types",
            span(
              className := "brood-worker-orders",
              role := "img",
              title <-- antsSignal
                .map(a =>
                  if (a.standardWorkersUnlocked) "Worker egg orders"
                  else "Small worker egg orders"
                )
                .distinct,
              aria.label <-- antsSignal
                .map(a =>
                  s"${a.eggOrders} ${if (a.standardWorkersUnlocked) "worker" else "small worker"} egg orders"
                )
                .distinct,
              workerCasteIcon,
              b(
                child.text <-- antsSignal
                  .map(ants => prettyNumberStr(ants.eggOrders.toLong * u))
                  .distinct
              )
            ),
            child.maybe <-- scoutOrdersVisible.map(show =>
              Option.when(show)(
                span(
                  className := "brood-scout-orders",
                  role := "img",
                  title := "Scout egg orders",
                  aria.label <-- antsSignal
                    .map(a => s"${a.scoutEggOrders.size} scout egg orders")
                    .distinct,
                  ResourceIcons.icon(ResourceIcons.Scouts),
                  b(
                    child.text <-- antsSignal
                      .map(ants => prettyNumberStr(ants.scoutEggOrders.size.toLong * u))
                      .distinct
                  )
                )
              )
            ),
          ),
        ),
        div(
          className := "brood-place-count",
          span(className := "small text-body-secondary", "Brood places"),
          b(
            child.text <-- antsSignal
              .combineWith(nestSignal)
              .map { case (ants, nest) =>
                s"${prettyNumberStr(BroodProgression.occupiedPlaces(ants).toLong * u)}/${prettyNumberStr(BroodProgression.usablePlaces(nest).toLong * u)}"
              }
              .distinct
          )
        ),
        child.maybe <-- antsSignal
          .map(_.queuedEggs > 0)
          .distinct
          .map(hasOrders =>
            Option.when(hasOrders)(
              button(
                className := "btn btn-outline-danger brood-cancel-orders",
                typ := "button",
                aria.label := "Cancel egg orders",
                title := "Cancel egg orders",
                span(className := "brood-cancel-icon", aria.hidden := true, "×"),
                span(className := "brood-cancel-label", "Cancel egg orders"),
                onClick --> (_ => Actions.cancelEggOrders())
              )
            )
          ),
      ),
      child.maybe <-- nestSignal
        .map(Midden.visible)
        .distinct
        .map(show =>
          Option.when(show) {
            val blocked = nestSignal
              .map(nest => Midden.blockedBroodPlaces(nest, BroodProgression.capacity(nest)))
              .distinct
            val flow = nestSignal
              .combineWith(antsSignal, TickCalculator.larvalFeedingCost)
              .map { case (nest, ants, cost) =>
                val current = Midden.stored(nest)
                (
                  current,
                  Midden.capacity(nest),
                  (Midden.nextRefuse(ants, nest, cost.sugar) - current) * TicksPerSecond
                )
              }
              .distinct
            div(
              className := "refuse-meter small mb-2",
              className("text-danger") <-- blocked.map(_ > 0),
              child.text <-- flow.combineWith(blocked).map {
                case (stored, capacity, rate, closed) =>
                  val places = if (closed == 1) "place" else "places"
                  val crowded = if (closed > 0) s" · $closed $places blocked" else ""
                  s"Refuse ${prettyNumberStr(stored)}/${prettyNumberStr(capacity)} · ${signedRateStr(rate)}/s$crowded"
              }
            )
          }
        ),
      div(
        className := "brood-pipeline small mb-2", {
          val status = antsSignal
            .combineWith(
              nestSignal,
              resourcesSignal,
              upgradesSignal,
              explorationSignal,
              statisticsData.signal
            )
            .map { case (ants, nest, resources, upgrades, exploration, stats) =>
              BroodProgression.layingStatus(ants, nest, resources, upgrades, exploration, stats)
            }
            .distinct
          span(
            className := "activity-status",
            dataAttr("state") <-- status.map(ActivityStatus.kind).distinct,
            child <-- status
              .combineWith(antsSignal.map { a =>
                a.layingBrood
                  .map(b => (b.kind == "scout", !b.small))
                  .getOrElse((a.scoutEggOrders.headOption.contains(0), a.standardWorkersUnlocked))
              }.distinct)
              .map {
                case (label, scout, standard) if label.startsWith("Laying egg · ") =>
                  span(
                    className := "brood-next-egg",
                    "Laying ",
                    ResourceIcons.icon(
                      if (scout) ResourceIcons.Scouts
                      else ResourceIcons.forWorker(standard)
                    ),
                    span(
                      className := "visually-hidden",
                      if (scout) "scout" else if (standard) "worker" else "small worker"
                    ),
                    label.stripPrefix("Laying")
                  )
                case (label, scout, standard) if label.startsWith("Next egg in ") =>
                  span(
                    className := "brood-next-egg",
                    "Next ",
                    ResourceIcons.icon(
                      if (scout) ResourceIcons.Scouts
                      else ResourceIcons.forWorker(standard)
                    ),
                    span(
                      className := "visually-hidden",
                      if (scout) "scout" else if (standard) "worker" else "small worker"
                    ),
                    label.stripPrefix("Next")
                  )
                case (label, _, _) => span(label)
              }
          )
        }
      ),
      child.maybe <-- antsSignal
        .combineWith(sugarSignal)
        .map { case (ants, sugar) =>
          ants.standardWorkersUnlocked && EmergencyRecovery.needed(
            ants
          ) && sugar.amount >= EmergencyRecovery.EggCost
        }
        .distinct
        .map { needed =>
          Option.when(needed) {
            div(
              className := "emergency-recovery row mb-2",
              actionButton(
                name = "Raise Emergency Worker",
                enabledSignal = antsSignal.combineWith(sugarSignal).map { case (ants, sugar) =>
                  EmergencyRecovery.canRaise(ants, sugar.amount)
                },
                blockedReasonSignal = antsSignal
                  .map(ants => Option.when(ants.layingCooldown > 0L)("Queen is laying")),
                descriptionSignal = Val(Some(eggDescription(EmergencyRecovery.Kind))),
                costSignal = Val(ActionCost(sugar = EmergencyRecovery.EggCost)),
                bonusSignal = Val(ActionBonus.empty),
                onClickAction = _ => Actions.raiseEmergencyWorker(),
              ),
            )
          }
        },
      div(
        className := "row",
        child.maybe <-- ifUnlockedOpt(_.actions.canLayEggs) {
          val displayedBatch = modifiersMultiplierSignal
            .combineWith(antsSignal, nestSignal)
            .map { case (requested, ants, nest) =>
              Math.max(1, Actions.workerEggAmount(ants, nest, requested))
            }
            .distinct
          actionButton(
            name = LayEggAction,
            progressBadge = Some(eggCounts(scout = false)),
            displayNameSignal = Some(
              antsSignal
                .map(_.standardWorkersUnlocked)
                .distinct
                .map(standard => if (standard) LayEggAction else "Raise Small Worker Egg")
            ),
            topLeftBadgeSignal = displayedBatch.map {
              case 1    => None
              case mult => Some(span(s"x$mult"))
            },
            enabledSignal = Actions.layEggActionEnabled,
            descriptionSignal = antsSignal
              .map(_.standardWorkersUnlocked)
              .distinct
              .map(standard => Some(eggDescription(if (standard) "worker" else "small"))),
            costSignal = displayedBatch.combineWith(antsSignal).map { case (mult, ants) =>
              ActionCost(sugar = mult * BroodProgression.eggCost(ants))
            },
            bonusSignal = Val(ActionBonus.empty),
            onClickAction = mult => Actions.layEggAction(mult),
            requirementsSignal =
              broodRequirementsSignal.map(_.filterNot(_.label == "brood places")),
            blockedReasonSignal = broodRequirementsSignal.map(
              _.find(_.label == "colony slots").flatMap(ActionBlockers.missing)
            ),
          )
        },
        child.maybe <-- ifUnlockedOpt(nestSignal.map(_.chambers.nurseryChamber.level > 0)) {
          val displayedBatch = modifiersMultiplierSignal
            .combineWith(antsSignal, nestSignal)
            .map { case (requested, ants, nest) =>
              Math.max(1, ScoutProgression.orderAmount(ants, nest, requested))
            }
            .distinct
          actionButton(
            name = "Raise Scout Egg",
            progressBadge = Some(eggCounts(scout = true)),
            topLeftBadgeSignal =
              displayedBatch.map(amount => Option.when(amount > 1)(span(s"x$amount"))),
            enabledSignal = antsSignal
              .combineWith(nestSignal)
              .map { case (ants, nest) => ScoutProgression.canRaise(ants, nest) },
            descriptionSignal = Val(Some(eggDescription("scout"))),
            costSignal = displayedBatch.map(amount =>
              ActionCost(
                sugar = amount * ScoutProgression.eggCost.sugar,
                protein = amount * ScoutProgression.eggCost.protein
              )
            ),
            bonusSignal = Val(ActionBonus.empty),
            onClickAction = amount => Actions.raiseScout(amount),
            requirementsSignal =
              broodRequirementsSignal.map(_.filterNot(_.label == "brood places")),
            blockedReasonSignal = antsSignal.map(ants =>
              Option.when(!ants.standardWorkersUnlocked)("Requires Standard Worker Development")
            ),
          )
        },
      ),
      div(
        className := "brood-options position-relative mt-2 border-top pt-1",
        detailsTag(
          className := "brood-options-details",
          rememberDetails("nest.brood.options"),
          summaryTag(className := "brood-summary", minHeight.px := 44, "Brood options"),
          actionAmountControl("brood-action-amount", broodMultiplier),
          child.maybe <-- broodPriorityVisible.map(available =>
            Option.when(available)(
              div(
                className := "d-flex align-items-center justify-content-between gap-2 mb-2",
                label(forId := "brood-priority", "Brood priority"),
                select(
                  idAttr := "brood-priority",
                  className := "form-select w-auto",
                  minHeight.px := 44,
                  BroodPriority.choices.map(priority =>
                    option(value := priority.toString, priority.label)
                  ),
                  value <-- antsSignal.map(_.broodPriority.toString).distinct,
                  onChange.mapToValue --> (selected =>
                    BroodPriority.choices
                      .find(_.toString == selected)
                      .foreach(Actions.setBroodPriority)
                  ),
                ),
              )
            )
          ),
          child.maybe <-- ifUnlockedOpt(
            upgradesSignal.map(_(UpgradeType.QueensRoutine).unlocked)
          ) {
            queenRoutineControls
          },
        ),
        child.maybe <-- antsSignal.map(_.ages.active).distinct.map(active =>
          Option.when(active)(
            div(
              className := "colony-aging small mb-2",
              child.text <-- antsSignal.combineWith(currentTickSignal).map {
                case (ants, tick) => PopulationAging.forecast(ants, tick)
              }
            )
          )
        ),
      ),
    )
  }
}
