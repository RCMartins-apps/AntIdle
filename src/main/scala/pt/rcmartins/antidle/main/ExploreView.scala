package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{svg => _, _}
import pt.rcmartins.antidle.game.ActionBlockers.{Requirement => ActionRequirement}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.UINumbersUtils._
import pt.rcmartins.antidle.game.UIUtils._
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.game._
import pt.rcmartins.antidle.model._

private[main] object ExploreView {
  import MainForm.{actionButton, actionCosts, activityStatus, infoHeading}

  private def mapHexWidth(expanded: Boolean): Double = if (expanded) 27.0 else 36.0
  private def mapHexHeight(expanded: Boolean): Double = mapHexWidth(expanded) * Math.sqrt(3.0) / 2.0

  def render(implicit owner: Owner): ReactiveHtmlElement[HTMLDivElement] = {
    val selected = Var("surroundings")
    def validSelection(id: String, data: ExplorationData, ants: AntsData): Boolean =
      id == "surroundings" || (ExplorationMap
        .availableSites(ants)
        .exists(_.id == id) && ExplorationMap.revealed(data, id))

    val feedbackSignals = (Vector("surroundings") ++ ExplorationMap.allSites.map(_.id)).map { id =>
      id -> explorationSignal
        .combineWith(currentTickSignal)
        .map { case (data, tick) =>
          MapFeedback.state(data, id, tick)
        }
        .distinct
    }.toMap
    def feedback(id: String): Signal[MapFeedback.State] = feedbackSignals(id)
    def mapProgress(id: String): ReactiveHtmlElement[HTMLDivElement] = div(
      className := "map-progress",
      role := "progressbar",
      aria.label := "Mission progress",
      aria.valueMin := 0,
      aria.valueMax := 100,
      aria.valueNow <-- feedback(id).map(_.percent.getOrElse(0).toDouble),
      display <-- feedback(id).map(f => if (f.percent.isDefined) "" else "none"),
      div(
        className := "map-progress-fill",
        width.percent <-- feedback(id).map(_.percent.getOrElse(0).toDouble)
      )
    )
    def hexLabel(site: ExplorationMap.Site): String =
      s"Ring ${site.distance} · (${site.q}, ${site.r})"

    def routeAvailable(route: ExplorationDestination, data: AllData): Boolean =
      route.isUnlocked(data.exploration, data.ants, data.nestAttributes) &&
        ExplorationMap.remaining(data.exploration, route.id) > 0L

    def hexActions(site: ExplorationMap.Site): Signal[List[(String, String)]] = {
      val routes = ExplorationDestination.all.filter(route => site.routes.contains(route.id))
      snapshotSignal.map { data =>
        if (!ExplorationMap.revealed(data.exploration, site.id)) List("fog" -> "Unexplored")
        else {
          val study = Option.when(ExplorationMap.studyReward(data.exploration, site.id) > 0L)(
            "study" -> "Search"
          )
          val collect = Option.when(routes.exists(route => routeAvailable(route, data)))(
            "collect" -> "Collect"
          )
          val relocate =
            Option.when(site.id == "woodland-edge" && AphidRelocation.visible(data))(
              "collect" -> "Relocate aphids"
            )
          val actions = study.toList ++ collect.orElse(relocate).toList
          if (actions.nonEmpty) actions
          else if (ExplorationMap.exhausted(data.exploration, site.id))
            List("exhausted" -> "Exhausted")
          else List("idle" -> "No available actions")
        }
      }.distinct
    }

    val scoutCrew = Var(1)
    def exploreAmount(
        icon: Option[ResourceIcons.ResourceIcon],
        amount: Signal[String]
    ): ReactiveHtmlElement[HTMLSpanElement] =
      span(
        className := "d-inline-flex align-items-center gap-1 text-nowrap",
        icon.map(ResourceIcons.icon),
        span(child.text <-- amount)
      )
    val requestedWorkers = Var(5L * u)
    val workers = requestedWorkers.signal
      .combineWith(availableWorkersCountSignal)
      .map { case (n, available) =>
        ExplorationProgression.selectedWorkers(n, available)
      }
      .distinct
    def scoutPicker = div(
      idAttr := "scout-crew",
      role := "group",
      aria.label := "Scout crew",
      className := "btn-group",
      (1 to ExplorationMap.MaxSurveyScouts).map(n =>
        button(
          typ := "button",
          className := "btn",
          minWidth.px := 44,
          minHeight.px := 44,
          className("btn-primary") <-- scoutCrew.signal.map(_ == n),
          className("btn-outline-primary") <-- scoutCrew.signal.map(_ != n),
          aria.label := s"$n ${if (n == 1) "scout" else "scouts"}",
          aria.pressed <-- scoutCrew.signal.map(current => (current == n).toString),
          n.toString,
          onClick --> (_ => scoutCrew.set(n)),
        )
      ),
    )
    def scoutAction(id: String): ReactiveHtmlElement[HTMLDivElement] = {
      val supported = snapshotSignal
        .combineWith(scoutCrew.signal)
        .map { case (data, count) =>
          ExplorationMap.supportedStudy(data, id, count)
        }
        .distinct
      div(
        div(
          className := "explore-control-row",
          ResourceIcons.icon(ResourceIcons.Scouts),
          scoutPicker,
          span(
            className := "explore-duration",
            child.text <-- scoutCrew.signal.combineWith(upgradesSignal, supported).map {
              case (count, upgrades, stationSupport) =>
                prettyTimeFromTicks(
                  ExplorationMap
                    .scoutDuration(count, id == "surroundings", upgrades, stationSupport)
                )
            }
          )
        ),
        actionButton(
          descriptionSignal = Val(None),
          name = if (id == "surroundings") "Explore Surroundings" else "Search Site",
          buttonContent = Option.when(id != "surroundings")(
            span(
              className := "explore-launch-label",
              "Send ",
              exploreAmount(
                Some(ResourceIcons.Scouts),
                scoutCrew.signal.map(n => s"$n ${if (n == 1) "scout" else "scouts"}")
              )
            )
          ),
          accessibleName = Option.when(id != "surroundings")("Search Site"),
          showDetails = false,
          enabledSignal = snapshotSignal.combineWith(scoutCrew.signal).map { case (data, count) =>
            ExplorationMap.canScout(data, id, count)
          },
          blockedReasonSignal = explorationSignal.map(data =>
            Option.when(
              ExplorationMap.busy(data, id, if (id == "surroundings") "reveal" else "study")
            )(if (id == "surroundings") "Scouting in progress" else "Search in progress")
          ),
          costSignal = scoutCrew.signal.combineWith(supported).map { case (count, stationSupport) =>
            ExplorationMap.scoutCost(count, stationSupport)
          },
          bonusSignal = Val(ActionBonus.empty),
          requirementsSignal = availableScoutsSignal
            .combineWith(scoutCrew.signal, explorationSignal, upgradesSignal)
            .map { case (available, count, data, upgrades) =>
              List(
                ActionRequirement("scouts", available, count.toLong * u, isCost = true),
                ActionRequirement(
                  "mission slots",
                  Math
                    .max(
                      0,
                      ExplorationProgression
                        .maxParties(data, upgrades) - data.explorationParties.size
                    )
                    .toLong * u
                )
              )
            },
          onClickAction = _ => Actions.scoutMap(id, scoutCrew.now()),
        ).amend(className := "col-12"),
      )
    }
    def surveyOrderControls(id: String): ReactiveHtmlElement[HTMLDivElement] = div(
      child.maybe <-- snapshotSignal
        .combineWith(scoutCrew.signal)
        .map { case (data, count) =>
          val e = data.exploration
          val completed = ExplorationMap.studies(e, id)
          e.surveyOrder match {
            case Some(order) if order.site == id =>
              val status =
                if (ExplorationMap.busy(e, id, "study")) "Searching"
                else "Waiting for scouts, supplies or a mission slot"
              Some((s"$status · $completed/${order.target} searches", true, false))
            case None =>
              SurveyOrders.nextDiscovery(e, id).filter(_.trips - completed >= 2).map { discovery =>
                val trips = discovery.trips - completed
                val cost =
                  ExplorationMap.scoutCost(count, ExplorationMap.supportedStudy(data, id, count))
                val water =
                  if (cost.water > 0L) s" · ${prettyNumberStr(cost.water * trips)} water" else ""
                (
                  s"${discovery.title} · $completed/${discovery.trips} searches · ${prettyNumberStr(cost.sugar * trips)} sugar$water",
                  false,
                  ExplorationMap.canScout(data, id, count)
                )
              }
            case _ => None
          }
        }
        .distinct
        .map(_.map { case (summary, active, canStart) =>
          div(
            className := "small mb-2",
            div(summary),
            if (active)
              button(
                typ := "button",
                className := "btn btn-outline-secondary",
                minHeight.px := 44,
                "Stop searching",
                onClick --> (_ => Actions.cancelSurveys())
              )
            else
              button(
                typ := "button",
                className := "btn btn-outline-primary",
                minHeight.px := 44,
                "Search until discovery",
                disabled := !canStart,
                onClick --> (_ => Actions.orderSurveys(id, scoutCrew.now()))
              )
          )
        }),
    )
    def trailControls(route: ExplorationDestination): ReactiveHtmlElement[HTMLDivElement] = div(
      child.maybe <-- antsSignal
        .map(_.lineage.sharedPaths)
        .distinct
        .map(known =>
          Option.when(known && !RaidProgression.isRaid(route))(
            div(
              className := "collection-trail border rounded p-2 mt-2",
              dataAttr("route") := route.id,
              infoHeading(
                "Collection trail",
                div(
                  child.text <-- upgradesSignal.map(upgrades =>
                    s"${PheromoneTrails.efficiency(upgrades)}% of manual haul rate. One scout per ten workers, rounded up. Supplies and normal upkeep apply."
                  )
                ),
                minor = true
              ),
              div(
                className := "trail-preview small",
                child.text <-- snapshotSignal.map { data =>
                  val count = Math
                    .max(PheromoneTrails.Step, PheromoneTrails.target(data.exploration, route.id))
                  val (baseRate, cost) = PheromoneTrails.nominal(route, count, data.upgrades)
                  val rate =
                    if (route.sugarReward > 0L) ColonyMilestones.sugar(baseRate, data.statistics)
                    else baseRate
                  s"${signedRateStr(rate * TicksPerSecond)} ${ExplorationHelper
                      .resource(route)}/s · ${signedRateStr(-cost * TicksPerSecond)} sugar/s supplies"
                }
              ),
              div(
                className := "explore-control-row my-2",
                ResourceIcons.icon(ResourceIcons.Workers),
                button(
                  typ := "button",
                  className := "btn btn-outline-secondary",
                  minHeight.px := 44,
                  "0",
                  aria.label := "Recall trail",
                  disabled <-- explorationSignal
                    .map(data => PheromoneTrails.target(data, route.id) == 0L),
                  onClick --> (_ => Actions.recallTrail(route.id))
                ),
                button(
                  typ := "button",
                  className := "btn btn-outline-primary",
                  minHeight.px := 44,
                  "−5",
                  aria.label := s"Remove 5 trail workers from ${route.name}",
                  disabled <-- snapshotSignal
                    .map(data => !PheromoneTrails.canAdjust(data, route.id, -PheromoneTrails.Step)),
                  onClick --> (_ => Actions.assignTrailWorkers(route.id, -PheromoneTrails.Step))
                ),
                span(
                  className := "trail-staffing",
                  child.text <-- antsSignal.combineWith(explorationSignal).map {
                    case (ants, data) =>
                      s"${PheromoneTrails.staffed(ants, data, route.id) / u}/50 workers"
                  }
                ),
                button(
                  typ := "button",
                  className := "btn btn-outline-primary",
                  minHeight.px := 44,
                  "+5",
                  aria.label := s"Assign 5 trail workers to ${route.name}",
                  disabled <-- snapshotSignal
                    .map(data => !PheromoneTrails.canAdjust(data, route.id, PheromoneTrails.Step)),
                  onClick --> (_ => Actions.assignTrailWorkers(route.id, PheromoneTrails.Step))
                ),
                button(
                  typ := "button",
                  className := "btn btn-outline-secondary",
                  minHeight.px := 44,
                  "Max",
                  aria.label := s"Maximize trail workers at ${route.name}",
                  disabled <-- snapshotSignal
                    .map(data => !PheromoneTrails.canAdjust(data, route.id, PheromoneTrails.Step)),
                  onClick --> (_ => Actions.maximizeTrail(route.id))
                ),
              ),
              actionCosts(
                Val(ActionCost.empty),
                antsSignal.combineWith(explorationSignal).map { case (ants, data) =>
                  val current = PheromoneTrails.target(data, route.id)
                  val available = PheromoneTrails.scoutsAvailableFor(ants, data, route.id)
                  val next = PheromoneTrails.nextTarget(data, route.id)
                  List(
                    ActionRequirement(
                      "scouts",
                      available,
                      PheromoneTrails.requiredScouts(next),
                      isCost = true
                    )
                  ) ++
                    Option.when(current < PheromoneTrails.Capacity)(
                      ActionRequirement(
                        "workers",
                        ants.availableWorkersCount,
                        PheromoneTrails.Step,
                        isCost = true
                      )
                    )
                }
              ),
              div(
                className := "trail-assignment-blocker small text-danger",
                child.text <-- snapshotSignal.map { data =>
                  if (PheromoneTrails.canAdjust(data, route.id, PheromoneTrails.Step)) ""
                  else if (
                    PheromoneTrails.target(data.exploration, route.id) >= PheromoneTrails.Capacity
                  ) "Trail crew full"
                  else if (data.ants.availableWorkersCount < PheromoneTrails.Step)
                    s"Needs ${data.ants.availableWorkersLabel}"
                  else "Needs scouts"
                }.distinct
              ),
              activityStatus(
                snapshotSignal.map(data =>
                  PheromoneTrails
                    .forecast(
                      data.ants,
                      data.exploration,
                      data.upgrades,
                      data.basicResources,
                      data.statistics
                    )
                    .find(_.destination == route.id)
                    .map(_.status)
                    .getOrElse("No workers assigned")
                ),
                "trail-status small"
              ),
            )
          )
        ),
    )
    def workerPicker(route: ExplorationDestination) = {
      val fitted = snapshotSignal.map { data =>
        if (RaidProgression.isRaid(route))
          Option.when(data.ants.availableWorkersCount >= route.minimumWorkers)(
            ExplorationProgression.selectedWorkers(
              ExplorationProgression.MaxWorkers,
              data.ants.availableWorkersCount
            )
          )
        else ExplorationHelper.fitCrew(data, route)
      }.distinct
      div(
        className := "site-worker-controls my-2",
        div(
          className := "explore-control-row",
          ResourceIcons.icon(ResourceIcons.Workers),
          button(
            className := "btn btn-outline-secondary",
            typ := "button",
            aria.label := "Decrease collection workers",
            "−5",
            disabled <-- workers.map(_ <= ExplorationProgression.MinWorkers),
            onClick --> (_ =>
              useSignalValue[Long](
                workers,
                n =>
                  requestedWorkers.set(
                    Math
                      .max(ExplorationProgression.MinWorkers, n - ExplorationProgression.WorkerStep)
                  )
              )
            )
          ),
          input(
            idAttr := "map-worker-count",
            aria.label := "Collection crew",
            className := "form-control",
            typ := "number",
            inputMode := "numeric",
            minAttr := "5",
            stepAttr := "5",
            maxAttr <-- availableWorkersCountSignal.map(n =>
              (ExplorationProgression
                .selectedWorkers(ExplorationProgression.MaxWorkers, n) / u).toString
            ),
            disabled <-- availableWorkersCountSignal.map(_ < ExplorationProgression.MinWorkers),
            value <-- workers.map(n => (n / u).toString),
            onChange --> (event => {
              val field = event.target.asInstanceOf[HTMLInputElement]
              useSignalValue[Long](
                availableWorkersCountSignal,
                available => {
                  val count = ExplorationProgression
                    .selectedWorkers(field.value.toLongOption.getOrElse(5L) * u, available)
                  field.value = (count / u).toString
                  requestedWorkers.set(count)
                }
              )
            })
          ),
          button(
            className := "btn btn-outline-secondary",
            typ := "button",
            aria.label := "Increase collection workers",
            "+5",
            disabled <-- workers.combineWith(availableWorkersCountSignal).map {
              case (count, available) =>
                count >= ExplorationProgression
                  .selectedWorkers(ExplorationProgression.MaxWorkers, available)
            },
            onClick --> (_ =>
              useSignalValue[Long](
                workers,
                n =>
                  requestedWorkers.set(
                    Math
                      .min(ExplorationProgression.MaxWorkers, n + ExplorationProgression.WorkerStep)
                  )
              )
            )
          ),
          button(
            className := "btn btn-outline-secondary",
            typ := "button",
            "Max",
            disabled <-- fitted.combineWith(workers).map { case (crew, current) =>
              crew.forall(_ == current)
            },
            onClick --> (_ => useSignalValue[Option[Long]](fitted, _.foreach(requestedWorkers.set)))
          ),
          span(
            className := "explore-duration",
            prettyTimeFromTicks(ExplorationProgression.duration(route, 0))
          ),
        ),
      )
    }
    def haul(route: ExplorationDestination): ReactiveHtmlElement[HTMLDivElement] = {
      val resource = ExplorationHelper.resource(route)
      val resourceIcon = ResourceIcons.forLabel(resource)
      val noSpace =
        snapshotSignal.map(data => ExplorationHelper.free(data, resource) == 0L).distinct
      div(
        className := "site-haul explore-action-card",
        div(
          className := "explore-control-row justify-content-between",
          b(
            className := "map-action-title",
            MapIcons("collect"),
            if (RaidProgression.isRaid(route)) "Raid" else "Collect"
          ),
          span(
            className := "explore-remaining",
            exploreAmount(
              resourceIcon,
              explorationSignal.map(data =>
                s"${prettyNumberStr(ExplorationMap.remaining(data, route.id))} $resource remaining"
              )
            )
          )
        ),
        workerPicker(route),
        if (RaidProgression.isRaid(route))
          div(
            className := "text-danger raid-risk",
            child.text <-- workers.map(RaidProgression.risk)
          )
        else emptyNode,
        if (RaidProgression.isRaid(route))
          div(
            className := "text-danger raid-aging small",
            child.text <-- antsSignal.combineWith(currentTickSignal, workers).map {
              case (ants, tick, count) => RaidProgression.ageWarning(ants, tick, count, 0)
            }
          )
        else emptyNode,
        actionButton(
          descriptionSignal = Val(None),
          name = route.actionName,
          accessibleName = Some(route.actionName),
          showDetails = false,
          buttonContent = Some(
            span(
              span(
                display <-- noSpace.map(full => if (full) "" else "none"),
                s"No space for $resource"
              ),
              span(
                className := "explore-launch-label",
                display <-- noSpace.map(full => if (full) "none" else ""),
                if (RaidProgression.isRaid(route)) "Raid · " else "Collect ",
                exploreAmount(
                  resourceIcon,
                  snapshotSignal
                    .combineWith(workers)
                    .map { case (data, count) =>
                      ExplorationHelper.haulPreview(data, count, route)
                    }
                    .distinct
                )
              )
            )
          ),
          enabledSignal = snapshotSignal.combineWith(workers).map { case (data, count) =>
            ExplorationHelper.canStart(data, count, route)
          },
          costSignal = workers.map(ExplorationHelper.cost(_, route)),
          bonusSignal = Val(ActionBonus.empty),
          requirementsSignal =
            workers.combineWith(explorationSignal, upgradesSignal, availableScoutsSignal).map {
              case (count, data, upgrades, scouts) =>
                List(
                  ActionRequirement(
                    "mission slots",
                    Math
                      .max(
                        0,
                        ExplorationProgression
                          .maxParties(data, upgrades) - data.explorationParties.size
                      )
                      .toLong * u
                  )
                ) ++
                  Option.when(count < route.minimumWorkers)(
                    ActionRequirement("party workers", count, route.minimumWorkers)
                  ) ++
                  Option.when(route.scouts > 0L)(
                    ActionRequirement("scout guide", scouts, route.scouts, isCost = true)
                  )
            },
          onClickAction = _ => useSignalValue[Long](workers, Actions.explore(_, route)),
        ).amend(className := "col-12"),
        trailControls(route),
      )
    }
    def relocateAphids: ReactiveHtmlElement[HTMLDivElement] = div(
      className := "aphid-relocation",
      div(
        className := "aphid-housing small mb-1",
        child.text <-- nestSignal.combineWith(explorationSignal).map { case (nest, data) =>
          val incoming = AphidRelocation.reserved(data)
          s"${nest.aphids}/${AphidHusbandry.housing(nest)} aphids" + (if (incoming > 0L)
                                                                        s" · $incoming returning"
                                                                      else "")
        }
      ),
      div(
        className := "small",
        s"${prettyTimeFromTicks(AphidRelocation.Duration)} · +${AphidRelocation.Batch} aphids"
      ),
      actionButton(
        name = "Relocate Aphids",
        enabledSignal = snapshotSignal.map(AphidRelocation.canStart),
        descriptionSignal = Val(
          Some(
            div(
              "One tender per 10 aphids. The first chamber receives 10; further groups are relocated from Woodland Edge."
            )
          )
        ),
        costSignal = Val(AphidRelocation.Cost),
        bonusSignal = Val(ActionBonus.empty),
        requirementsSignal = snapshotSignal.map { data =>
          List(
            ActionRequirement(
              "scouts",
              ScoutProgression.available(data.ants, data.exploration),
              AphidRelocation.Scouts,
              isCost = true
            ),
            ActionRequirement(
              "aphid places",
              AphidRelocation.free(data.nestAttributes, data.exploration) * u,
              AphidRelocation.Batch * u,
              isCost = true
            ),
            ActionRequirement(
              "mission slots",
              Math
                .max(
                  0,
                  ExplorationProgression.maxParties(
                    data.exploration,
                    data.upgrades
                  ) - data.exploration.explorationParties.size
                )
                .toLong * u,
              isCost = true
            )
          )
        },
        onClickAction = _ => Actions.relocateAphids(),
      ).amend(className := "col-12"),
    )
    def sitePanel(id: String): ReactiveHtmlElement[HTMLDivElement] = {
      if (id == "surroundings")
        div(
          className := "map-site-card card p-3",
          h5("Nest surroundings"),
          p(child.text <-- explorationSignal.combineWith(antsSignal).map { case (data, ants) =>
            s"${data.revealedSites} / ${ExplorationMap.availableSites(ants).size} hexagons revealed"
          }),
          child.maybe <-- explorationSignal
            .combineWith(antsSignal)
            .map { case (data, ants) =>
              data.revealedSites < ExplorationMap.availableSites(ants).size
            }
            .distinct
            .map(more => Option.when(more)(scoutAction(id))),
          child.maybe <-- explorationSignal
            .combineWith(antsSignal)
            .map { case (data, ants) =>
              data.revealedSites >= ExplorationMap.availableSites(ants).size
            }
            .distinct
            .map(done => Option.when(done)(span("Current range mapped"))),
        )
      else {
        val site = ExplorationMap.site(id)
        val unlockLabel: String = DiscoveryProgression.all
          .find(_.destination == id)
          .map(d => s"3 searches: ${d.title} research")
          .getOrElse("")
        div(
          className := "map-site-card",
          h5(hexLabel(site)),
          if (ExplorationMap.outerSites.exists(_.id == id))
            div(className := "site-unlock small mb-2", unlockLabel)
          else emptyNode,
          div(
            className := "explore-action-card",
            div(
              className := "explore-control-row justify-content-between",
              b(className := "map-action-title", MapIcons("study"), "Search"),
              span(
                className := "site-knowledge explore-remaining",
                exploreAmount(
                  Some(ResourceIcons.Knowledge),
                  explorationSignal.map(data =>
                    s"${prettyNumberStr(ExplorationMap.knowledgeRemaining(data, id))} knowledge remaining"
                  )
                )
              )
            ),
            child.maybe <-- explorationSignal
              .map(data => ExplorationMap.studyReward(data, id) > 0L)
              .distinct
              .map(more => Option.when(more)(scoutAction(id))),
            surveyOrderControls(id)
          ),
          site.routes.flatMap(route => ExplorationDestination.all.find(_.id == route)).map {
            route =>
              child.maybe <-- snapshotSignal
                .map(data => routeAvailable(route, data))
                .distinct
                .map(available => Option.when(available)(haul(route)))
          },
          if (id == "woodland-edge")
            child.maybe <-- snapshotSignal
              .map(AphidRelocation.visible)
              .distinct
              .map(visible => Option.when(visible)(relocateAphids))
          else emptyNode,
          child.maybe <-- explorationSignal
            .map(data => ExplorationMap.exhausted(data, id))
            .distinct
            .map(done => Option.when(done)(p("Site exhausted"))),
        )
      }
    }
    div(
      className := "row exploration-layout hex-exploration",
      selected.signal.changes --> (id => UiPreferences.set("explore.site", id)),
      onMountCallback { _ =>
        useSignalValue[AllData](
          snapshotSignal,
          data => {
            val saved = UiPreferences
              .get("explore.site")
              .filter(validSelection(_, data.exploration, data.ants))
              .getOrElse("surroundings")
            selected.set(saved)
          }
        )
      },
      explorationSignal.combineWith(antsSignal.map(_.lineage).distinct).changes --> {
        case (data, lineage) =>
          if (!validSelection(selected.now(), data, AntsData(lineage = lineage)))
            selected.set("surroundings")
      },
      div(
        className := "col-12 p-3 explore-map-section",
        div(
          className := "hex-map",
          className("expanded") <-- antsSignal.map(_.lineage.beyondWoodland),
          styleAttr <-- antsSignal
            .map(_.lineage.beyondWoodland)
            .distinct
            .map(expanded =>
              s"--hex-width:${mapHexWidth(expanded)}%;--hex-height:${mapHexHeight(expanded)}%"
            ),
          role := "group",
          aria.label := "Exploration map",
          button(
            className := "map-hex nest-hex",
            typ := "button",
            dataAttr("q") := "0",
            dataAttr("r") := "0",
            left.percent := 50,
            top.percent <-- antsSignal.map(a => if (a.lineage.beyondWoodland) 62 else 50),
            aria.label := "Nest surroundings",
            aria.pressed <-- selected.signal.map(id => (id == "surroundings").toString),
            MapIcons("nest"),
            span("Nest"),
            small(
              className := "map-state",
              title <-- feedback("surroundings").map(_.label),
              child.text <-- feedback("surroundings").map(f =>
                f.percent.map(p => s"$p%").getOrElse("")
              )
            ),
            mapProgress("surroundings"),
            onClick --> (_ => selected.set("surroundings"))
          ),
          children <-- antsSignal.map(_.lineage.beyondWoodland).distinct.map { expanded =>
            ExplorationMap
              .availableSites(AntsData(lineage = Lineage(beyondWoodland = expanded)))
              .map { site =>
                val actions = hexActions(site)
                button(
                  className := "map-hex",
                  typ := "button",
                  dataAttr("site") := site.id,
                  dataAttr("q") := site.q.toString,
                  dataAttr("r") := site.r.toString,
                  left.percent := 50.0 + .75 * mapHexWidth(expanded) * site.q,
                  top.percent := (if (expanded) 62.0 else 50.0) + mapHexHeight(
                    expanded
                  ) * (site.r + site.q / 2.0),
                  disabled <-- explorationSignal
                    .map(data => !ExplorationMap.revealed(data, site.id)),
                  aria.label <-- actions
                    .combineWith(feedback(site.id))
                    .map {
                      case (_, state) if state.kind == "unknown" => "Unexplored hexagon"
                      case (items, state) =>
                        val status = Option.when(state.percent.isDefined || state.kind == "trail")(
                          state.label
                        )
                        (hexLabel(site) :: (items.map(_._2) ++ status)).mkString(", ")
                    }
                    .distinct,
                  dataAttr("activity") <-- feedback(site.id).map(_.kind),
                  aria.pressed <-- selected.signal.map(id => (id == site.id).toString),
                  className <-- explorationSignal.map(data =>
                    if (!ExplorationMap.revealed(data, site.id)) "unexplored"
                    else if (ExplorationMap.exhausted(data, site.id)) "exhausted"
                    else "revealed"
                  ),
                  span(
                    className := "map-hex-actions",
                    children <-- actions
                      .map(items => items.map { case (icon, _) => MapIcons(icon) })
                  ),
                  small(
                    className := "map-state",
                    title <-- feedback(site.id).map(_.label),
                    child.text <-- feedback(site.id).map(f =>
                      f.percent
                        .map(p => s"$p%")
                        .getOrElse(
                          if (f.kind == "trail") "Trail" else ""
                        )
                    )
                  ),
                  mapProgress(site.id),
                  onClick --> (_ => selected.set(site.id)),
                )
              }
          },
        ),
        div(
          className := "map-action-legend small mb-3",
          List(
            "study" -> "Search",
            "collect" -> "Collect",
            "fog" -> "Unexplored",
            "exhausted" -> "Exhausted"
          ).map { case (icon, label) => span(MapIcons(icon), label) }
        ),
        child <-- selected.signal.map(sitePanel),
        div(
          className := "mission-status my-3",
          b("Missions "),
          span(child.text <-- explorationSignal.combineWith(upgradesSignal).map { case (data, upgrades) =>
            s"${data.explorationParties.size} / ${ExplorationProgression.maxParties(data, upgrades)}"
          }),
          children <-- explorationSignal.map(
            _.explorationParties.map(party =>
              div(
                className := "map-mission p-2",
                b(
                  if (party.mission == "aphids") s"Relocating ${party.aphids} aphids"
                  else if (party.mission == "reveal") "Exploring surroundings"
                  else if (party.mission == "study")
                    s"Searching ${hexLabel(ExplorationMap.site(party.destination))}"
                  else ExplorationDestination.forParty(party).name
                ),
                s" · ${party.scouts / u} scouts · ${party.amountWorkers / u} workers · ",
                span(
                  child.text <-- currentTickSignal.map(tick =>
                    prettyTimeFromTicks(Math.max(0L, party.targetTick - tick))
                  )
                ),
                if (
                  party.mission == "haul" && RaidProgression
                    .isRaid(ExplorationDestination.forParty(party))
                )
                  div(className := "text-danger small", RaidProgression.risk(party.amountWorkers))
                else emptyNode,
              )
            )
          ),
        ),
      ),
      child.maybe <-- ifUnlockedOpt(explorationSignal.map(_.carcassDiscovered)) {
        div(
          className := "col-12 px-3 py-2 carcass-panel",
          h5("Discovered"),
          children <-- explorationSignal.map(_.discoveredCarcasses.zipWithIndex.map {
            case (carcass, index) =>
              div(
                className := "card p-2 my-2 discovered-carcass",
                b("Dead insect"),
                div(
                  s"Sugar: ${prettyNumberStr(carcass.totalFood)} · Protein: ${prettyNumberStr(carcass.proteinReward)}"
                ),
                div(
                  className := "row",
                  actionButton(
                    name = "Retrieve Carcass",
                    requirementsSignal = explorationSignal
                      .combineWith(nestSignal)
                      .map { case (exploration, nest) =>
                        List(
                          ActionRequirement(
                            "carcass slots",
                            Math.max(
                              0L,
                              nest.maxCarcasses.toLong - CarcassProgression.occupiedSlots(
                                exploration
                              )
                            ) * u
                          )
                        )
                      }
                      .distinct,
                    enabledSignal = antsSignal
                      .combineWith(nestSignal, explorationSignal)
                      .map { case (ants, nest, exploration) =>
                        CarcassProgression.canRetrieve(ants, nest, exploration, carcass)
                      },
                    descriptionSignal = Val(
                      Some(
                        div(
                          s"${prettyTimeFromTicks(carcass.retrievalTicks)} · ",
                          prettyNumberInt(carcass.requiredWorkers),
                          " carriers"
                        )
                      )
                    ),
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
              child.text <-- currentTickSignal.map(tick =>
                prettyTimeFromTicks(Math.max(0L, retrieval.targetTick - tick))
              ),
            )
          }),
          infoHeading(
            "Carcass storage",
            div("Returning parties reserve a slot. Carcass sugar is consumed before stored sugar.")
          ),
          small(
            className := "text-body-secondary carcass-capacity",
            child.text <-- explorationSignal
              .combineWith(nestSignal)
              .map { case (exploration, nest) =>
                s"${CarcassProgression.occupiedSlots(exploration)} / ${nest.maxCarcasses}"
              }
          ),
          div(
            className := "text-body-secondary carcass-storage-summary",
            child.text <-- explorationSignal.map(data =>
              s"${data.carcasses.size} stored · ${data.retrievals.size} returning"
            ),
          ),
          children <-- explorationSignal.map { exploration =>
            if (exploration.carcasses.isEmpty) Seq(p("No carcasses at home."))
            else
              exploration.carcasses.map { carcass =>
                div(
                  className := "card p-2 my-2 stored-carcass",
                  b("Dead insect"),
                  div(
                    s"Sugar remaining: ${prettyNumberStr(carcass.foodRemaining)} / ${prettyNumberStr(carcass.totalFood)}"
                  ),
                  div(
                    className := "progress my-2",
                    div(
                      className := "progress-bar",
                      role := "progressbar",
                      aria.label := "Carcass sugar remaining",
                      width.percent := (100.0 * carcass.foodRemaining / carcass.totalFood)
                    )
                  ),
                )
              }
          },
        )
      },
    )
  }

}
