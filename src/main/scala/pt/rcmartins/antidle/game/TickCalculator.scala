package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLDivElement, HTMLElement}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.UIUtils.setTooltip
import pt.rcmartins.antidle.game.UINumbersUtils.signedRateStr
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.{ActionCost, AntTask}
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

object TickCalculator {

  case class ResourceOrigin(name: String, tickResourceU: Long) {
    def resourceUSec: Long = tickResourceU * TicksPerSecond
  }

  case class BonusOriginGroup(contributions: Signal[List[ResourceOrigin]]) {
    val sum: Signal[Long] = contributions.map(_.iterator.map(_.tickResourceU).sum)
  }

  val larvalFeedingCost: Signal[ActionCost] =
    antsSignal
      .combineWith(
        nestSignal,
        upgradesSignal,
        resourcesSignal,
        explorationSignal,
        statisticsData.signal
      )
      .map { case (ants, nest, upgrades, resources, exploration, stats) =>
        BroodProgression.forecastCost(ants, nest, upgrades, exploration, resources, stats)
      }
      .distinct

  val sugarTickGain: BonusOriginGroup =
    BonusOriginGroup(
      antsSignal
        .combineWith(
          upgradesSignal,
          explorationSignal,
          nestSignal,
          resourcesSignal,
          statisticsData.signal,
          larvalFeedingCost
        )
        .map { case (ants, upgrades, exploration, nest, resources, stats, larvalCost) =>
          val afterTrails =
            PheromoneTrails.forecastResources(ants, exploration, upgrades, resources, stats)
          val production =
            SugarProduction.contributions(ants, nest, upgrades, afterTrails.protein.amount, stats)
          List(
            ResourceOrigin(AntTask.SugarCollector.name, production.foraging),
            ResourceOrigin("Research", production.research),
            ResourceOrigin("Inherited production", production.inherited),
            ResourceOrigin("Collection trails", afterTrails.sugar.amount - resources.sugar.amount),
            ResourceOrigin("Aphid Tenders", production.aphids),
            ResourceOrigin("Milestones", production.milestones),
            ResourceOrigin("Ants Demand", -ScoutProgression.upkeepPerTick(ants, exploration)),
            ResourceOrigin(
              "Royal brood",
              -RoyalBroodProgression
                .forecast(ants, nest, upgrades, exploration, resources, stats)
                .cost
                .sugar
            ),
            ResourceOrigin("Larval feeding", -larvalCost.sugar),
            ResourceOrigin("Carcass sugar", CarcassProgression.foodOffset(exploration, ants))
          ).filter(_.tickResourceU != 0L)
        }
    )

  val proteinTickGain: BonusOriginGroup = BonusOriginGroup(
    antsSignal
      .combineWith(
        nestSignal,
        upgradesSignal,
        resourcesSignal,
        explorationSignal,
        statisticsData.signal,
        larvalFeedingCost
      )
      .map { case (ants, nest, upgrades, resources, exploration, stats, larvalCost) =>
        val afterTrails =
          PheromoneTrails.forecastResources(ants, exploration, upgrades, resources, stats)
        List(
          ResourceOrigin(
            "Collection trails",
            afterTrails.protein.amount - resources.protein.amount
          ),
          ResourceOrigin(
            "Aphid Tenders",
            -AphidHusbandry.proteinPerTick(ants, nest, upgrades, afterTrails.protein.amount)
          ),
          ResourceOrigin(
            "Seed Foragers",
            OutpostProgression.harvestPerTick(ants, nest, upgrades, afterTrails).protein
          ),
          ResourceOrigin(
            "Scraps Gatherers",
            BroodProgression.scavenged(
              ants,
              afterTrails.protein.freeSpace - OutpostProgression
                .harvestPerTick(ants, nest, upgrades, afterTrails)
                .protein + AphidHusbandry
                .proteinPerTick(ants, nest, upgrades, afterTrails.protein.amount),
              upgrades
            )
          ),
          ResourceOrigin("Larval feeding", -larvalCost.protein),
          ResourceOrigin(
            "Royal brood",
            -RoyalBroodProgression
              .forecast(ants, nest, upgrades, exploration, resources, stats)
              .cost
              .protein
          )
        )
      }
  )

  val waterTickGain: BonusOriginGroup = BonusOriginGroup(
    antsSignal
      .combineWith(
        nestSignal,
        upgradesSignal,
        resourcesSignal,
        explorationSignal,
        statisticsData.signal
      )
      .map { case (ants, nest, upgrades, resources, exploration, stats) =>
        val afterTrails =
          PheromoneTrails.forecastResources(ants, exploration, upgrades, resources, stats)
        List(
          ResourceOrigin("Collection trails", afterTrails.water.amount - resources.water.amount),
          ResourceOrigin("Dew Gatherers", OutpostProgression.dewPerTick(ants, upgrades)),
          ResourceOrigin(
            "Seed Foragers",
            -OutpostProgression.harvestPerTick(ants, nest, upgrades, afterTrails).water
          ),
          ResourceOrigin(
            "Royal brood",
            -RoyalBroodProgression
              .forecast(ants, nest, upgrades, exploration, resources, stats)
              .cost
              .water
          )
        )
      }
  )

  val resinTickGain: BonusOriginGroup = BonusOriginGroup(
    antsSignal
      .combineWith(explorationSignal, upgradesSignal, resourcesSignal, statisticsData.signal)
      .map { case (ants, exploration, upgrades, resources, stats) =>
        List(
          ResourceOrigin(
            "Collection trails",
            PheromoneTrails
              .forecastResources(ants, exploration, upgrades, resources, stats)
              .resin
              .amount - resources.resin.amount
          )
        )
      }
  )

  val tunnelingSpaceTickGain: BonusOriginGroup =
    BonusOriginGroup(
      antsSignal
        .combineWith(upgradesSignal)
        .map { case (ants, upgrades) =>
          List(
            Some(
              ResourceOrigin(
                AntTask.Tunneler.name,
                (ants.taskWorkers(
                  AntTask.Tunneler
                ) / u * 2L + ants.smallTunnelers / u) * ColonyDevelopment.tunnelingPerWorkerTick(
                  upgrades
                ) / 2L,
              )
            ),
          ).flatten
        }
    )

  private def recentSamples[A](source: Signal[A]): Signal[Vector[A]] =
    currentTickSignal
      .combineWith(source)
      .scanLeft(sample => Vector(sample)) { case (history, (tick, value)) =>
        val recent = if (history.lastOption.exists(_._1 > tick)) Vector.empty else history
        recent.filter { case (previous, _) =>
          previous < tick && tick - previous < 2L * TicksPerSecond
        } :+ (tick -> value)
      }
      .map(_.map(_._2))

  private val sugarSamples = recentSamples(sugarTickGain.contributions)

  val constructionResourceRates: Signal[ActionCost] = sugarTickGain.sum
    .combineWith(
      proteinTickGain.sum,
      waterTickGain.sum,
      resinTickGain.sum,
      tunnelingSpaceTickGain.sum
    )
    .map { case (sugar, protein, water, resin, space) =>
      ActionCost(
        sugar = sugar,
        protein = protein,
        water = water,
        resin = resin,
        tunnelingSpace = space
      )
    }

  val displayedSugarRate: Signal[Long] = sugarSamples.map { samples =>
    math.round(
      samples.iterator.map(_.iterator.map(_.tickResourceU.toDouble).sum).sum / samples.size
    )
  }.distinct

  val displayedProteinRate: Signal[Long] = recentSamples(proteinTickGain.sum).map { samples =>
    math.round(samples.iterator.map(_.toDouble).sum / samples.size)
  }.distinct

  def sugarRateDetails: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "resource-rate-details",
      children <-- sugarSamples.map { samples =>
        samples.flatten
          .groupBy(_.name)
          .iterator
          .map { case (name, entries) =>
            name -> math.round(entries.iterator.map(_.resourceUSec.toDouble).sum / samples.size)
          }
          .filter(_._2 != 0L)
          .toList
          .sortBy { case (name, rate) => (-rate, name) }
          .map { case (name, rate) =>
            div(
              className := "resource-rate-entry",
              span(name),
              span(className := (if (rate < 0L) "text-danger" else ""), s"${signedRateStr(rate)}/s")
            )
          }
      },
      div(
        className := "resource-rate-net",
        child.text <-- sugarSignal.combineWith(displayedSugarRate).map { case (resource, rate) =>
          val estimate = ResourceTiming.estimate(resource, rate).fold("")(e => s" · ${e.text}")
          val visibleRate = if (resource.atCapacity && rate > 0) 0L else rate
          s"Net ${signedRateStr(visibleRate * TicksPerSecond)}/s$estimate"
        },
      ),
    )

  def prettyResourceEstDiv(
      bonusGroup: BonusOriginGroup,
      resource: Signal[BasicResource],
      displayedRate: Option[Signal[Long]] = None,
      showTooltip: Boolean = true,
  ): ReactiveHtmlElement[HTMLDivElement] = {
    div(
      child <--
        displayedRate.getOrElse(bonusGroup.sum).combineWith(resource).map {
          case (production, current) =>
            val sumU = if (current.atCapacity && production > 0) 0L else production
            span(
              className := (if (sumU < 0L) "text-danger" else ""),
              UINumbersUtils.signedRateStr(sumU * TicksPerSecond),
              "/s",
            )
        }
    ).amendThis(elem =>
      Option.when(showTooltip)(
        setTooltip(
          elem,
          prettyResourceTooltip(bonusGroup, displayedRate.getOrElse(bonusGroup.sum), resource)
        )
      )
    )
  }

  def calculateTicksToResources(
      actionCost: ActionCost,
  ): ReactiveHtmlElement[HTMLElement] = {
    def getTicksRemaining(
        filter: ActionCost => Long,
        currentResource: Signal[BasicResource],
        resourceGainTickSignal: BonusOriginGroup,
    ): Signal[Long] = {
      val targetValue: Long = filter(actionCost)
      if (targetValue > 0)
        currentResource.combineWith(resourceGainTickSignal.sum).map { case (resource, valueTick) =>
          ResourceWait.ticks(targetValue, resource, valueTick)
        }
      else
        Val(0)
    }

    val allResourcesNeeded: Seq[Signal[Long]] =
      Seq(
        getTicksRemaining(_.sugar, sugarSignal, sugarTickGain),
        getTicksRemaining(_.tunnelingSpace, tunnelingSpaceSignal, tunnelingSpaceTickGain),
        getTicksRemaining(_.protein, proteinSignal, proteinTickGain),
        waterSignal.map(resource => if (resource.amount >= actionCost.water) 0L else -1L),
        resinSignal.map(resource => if (resource.amount >= actionCost.resin) 0L else -1L),
        knowledgeSignal.map(resource => if (resource.amount >= actionCost.knowledge) 0L else -1L),
      )

    span(
      child <--
        Signal
          .sequence(
            allResourcesNeeded
          )
          .map { ticksSeq =>
            if (ticksSeq.exists(_ < 0))
              -1
            else
              ticksSeq.max
          }
          .map {
            case -1 =>
              span(className := "text-danger", "Never")
            case 0 =>
              span("0s")
            case ticks =>
              span(UINumbersUtils.prettyTimeFromTicks(ticks))
          }
    )
  }

  // TODO some colors= green for positive, red for negative, orange for 0 ?
  private def prettyResourceTooltip(
      bonusGroup: BonusOriginGroup,
      sumSignal: Signal[Long],
      resource: Signal[BasicResource],
  ): ReactiveHtmlElement[HTMLDivElement] =
    div(
      children <-- bonusGroup.contributions.map {
        _.map { bonusOrigin =>
          div(
            className := "d-flex justify-content-between",
            span(
              className := "pe-3",
              s"${bonusOrigin.name}:",
            ),
            span(
              s"${signedRateStr(bonusOrigin.resourceUSec)}/sec"
            )
          )
        }
      },
      hr(),
      child <-- resource.combineWith(sumSignal).map { case (resource, sumU) =>
        val timing: String =
          ResourceTiming.estimate(resource, sumU).map(_.text).getOrElse("No capacity trend")
        div(timing)
      },
    )

}
