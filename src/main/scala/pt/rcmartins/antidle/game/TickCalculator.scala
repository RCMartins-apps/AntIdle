package pt.rcmartins.antidle.game

import com.raquo.laminar.api.L.{u => _, _}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.{HTMLDivElement, HTMLElement}
import pt.rcmartins.antidle.game.Constants._
import pt.rcmartins.antidle.game.TickCalculator.BonusOrigin.{PercentOrigin, ResourceOrigin}
import pt.rcmartins.antidle.game.UIUtils.setTooltip
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.{ActionCost, AntTask}
import pt.rcmartins.antidle.model.BasicResources.BasicResource
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

import scala.annotation.tailrec

object TickCalculator {

  sealed trait BonusOrigin {

    def name: String

  }

  object BonusOrigin {

    case class ResourceOrigin(
        name: String,
        tickResourceU: Long,
    ) extends BonusOrigin {

      def resourceUSec: Long = tickResourceU * TicksPerSecond

    }

    case class PercentOrigin(
        name: String,
        percent: Double,
    ) extends BonusOrigin

  }

  case class BonusOriginGroup(
      bonusListSignal: Signal[List[BonusOrigin]],
  ) {

    val sum: Signal[Long] = {
      @tailrec
      def calcLoop(list: List[BonusOrigin], sum: Long): Long =
        list match {
          case Nil =>
            sum
          case head :: tail =>
            head match {
              case ResourceOrigin(_, resourceTickU) =>
                calcLoop(tail, sum + resourceTickU)
              case PercentOrigin(_, percent) =>
                calcLoop(tail, (BigDecimal(sum) * BigDecimal.decimal(percent)).toLong)
            }
        }

      bonusListSignal.map(calcLoop(_, 0L))
    }

  }

  private def antTaskCountSignal(antTask: AntTask): Signal[Long] =
    antsSignal.map(_.tasks.find(_._1 == antTask).map(_._2 / u).getOrElse(0L)).distinct

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
      antTaskCountSignal(AntTask.SugarCollector)
        .combineWith(
          upgradesSignal,
          antsSignal,
          explorationSignal,
          nestSignal,
          resourcesSignal,
          statisticsData.signal,
          larvalFeedingCost,
        )
        .map {
          case (
                sugarCollectorAmount,
                upgrades,
                ants,
                exploration,
                nest,
                resources,
                stats,
                larvalCost,
              ) =>
            val afterTrails =
              PheromoneTrails.forecastResources(ants, exploration, upgrades, resources, stats)
            List(
              Some(
                ResourceOrigin(
                  AntTask.SugarCollector.name,
                  Foraging.mixedBasePerTick(ants, upgrades),
                )
              ),
              Some(PercentOrigin(ImproveSugarCollectorTask1.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask1).unlocked),
              Some(PercentOrigin(ImproveSugarCollectorTask2.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask2).unlocked),
              Some(PercentOrigin(ImproveSugarCollectorTask3.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask3).unlocked),
              Some(PercentOrigin("Inherited production", 1.0 + ants.lineage.foundings * 0.1))
                .filter(_ => ants.lineage.foundings > 0),
              Some(
                ResourceOrigin(
                  "Collection trails",
                  PheromoneTrails
                    .forecastResources(ants, exploration, upgrades, resources, stats)
                    .sugar
                    .amount - resources.sugar.amount
                )
              ).filter(_.tickResourceU != 0),
              Some(
                ResourceOrigin(
                  "Aphid Tenders",
                  AphidHusbandry.productionPerTick(ants, nest, upgrades, afterTrails.protein.amount)
                )
              )
                .filter(_.tickResourceU > 0),
              Some(
                ResourceOrigin(
                  "Milestones",
                  ColonyMilestones.sugar(
                    Foraging.production(ants, upgrades) + AphidHusbandry
                      .productionPerTick(ants, nest, upgrades, afterTrails.protein.amount),
                    stats
                  ) -
                    Foraging.production(ants, upgrades) - AphidHusbandry
                      .productionPerTick(ants, nest, upgrades, afterTrails.protein.amount)
                )
              )
                .filter(_.tickResourceU > 0),
              Some(
                ResourceOrigin(
                  "Ants Demand",
                  -ScoutProgression.upkeepPerTick(ants, exploration),
                )
              ),
              Some(
                ResourceOrigin(
                  "Royal brood",
                  -RoyalBroodProgression
                    .forecast(ants, nest, upgrades, exploration, resources, stats)
                    .cost
                    .sugar
                )
              )
                .filter(_.tickResourceU < 0),
              Some(ResourceOrigin("Larval feeding", -larvalCost.sugar)).filter(_.tickResourceU < 0),
              Some(
                ResourceOrigin("Carcass sugar", CarcassProgression.foodOffset(exploration, ants))
              )
                .filter(_.tickResourceU > 0),
            ).flatten
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
                .proteinPerTick(ants, nest, upgrades, afterTrails.protein.amount)
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
          ResourceOrigin("Dew Gatherers", OutpostProgression.dewPerTick(ants)),
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

  private def smoothNet(rate: Signal[Long]): Signal[Long] =
    currentTickSignal
      .combineWith(rate)
      .scanLeft(sample => Vector(sample)) { case (history, (tick, net)) =>
        val recent = if (history.lastOption.exists(_._1 > tick)) Vector.empty else history
        recent.filter { case (previous, _) =>
          previous < tick && tick - previous < 2L * TicksPerSecond
        } :+ (tick -> net)
      }
      .map(samples =>
        if (samples.isEmpty) 0L
        else math.round(samples.iterator.map(_._2.toDouble).sum / samples.size)
      )
      .distinct

  val displayedSugarRate: Signal[Long] = smoothNet(sugarTickGain.sum)
  val displayedProteinRate: Signal[Long] = smoothNet(proteinTickGain.sum)

  def prettyResourceEstDiv(
      bonusGroup: BonusOriginGroup,
      resource: Signal[BasicResource],
      displayedRate: Option[Signal[Long]] = None,
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
      setTooltip(elem, prettyResourceTooltip(bonusGroup, bonusGroup.sum, resource))
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
      children <-- bonusGroup.bonusListSignal.map {
        _.map { bonusOrigin =>
          div(
            className := "d-flex justify-content-between",
            span(
              className := "pe-3",
              s"${bonusOrigin.name}:",
            ),
            span(
              // TODO deal this small numbers in minutes
              bonusOrigin match {
                case resource: ResourceOrigin =>
                  s"${UINumbersUtils.signedRateStr(resource.resourceUSec)}/sec"
                case PercentOrigin(_, percent) if percent > 0 =>
                  f"+${(percent - 1.0) * 100.0}%.2f%%"
                case PercentOrigin(_, percent) if percent < 0 =>
                  f"${(percent - 1.0) * 100.0}%.2f%%"
                case PercentOrigin(_, _) =>
                  "0.00%"
              }
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
