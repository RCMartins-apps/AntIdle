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

  val sugarTickGain: BonusOriginGroup =
    BonusOriginGroup(
      antTaskCountSignal(AntTask.SugarCollector)
        .combineWith(
          upgradesSignal,
          antsSignal,
          explorationSignal,
          nestSignal,
          proteinSignal,
        )
        .map {
          case (
                sugarCollectorAmount,
                upgrades,
                ants,
                exploration,
                nest,
                protein,
              ) =>
            List(
              Some(
                ResourceOrigin(
                  AntTask.SugarCollector.name,
                  Foraging.basePerTick(sugarCollectorAmount, upgrades),
                )
              ),
              Some(PercentOrigin(ImproveSugarCollectorTask1.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask1).unlocked),
              Some(PercentOrigin(ImproveSugarCollectorTask2.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask2).unlocked),
              Some(PercentOrigin(ImproveSugarCollectorTask3.title, SugarImprovementBonusPerc))
                .filter(_ => upgrades(ImproveSugarCollectorTask3).unlocked),
              Some(ResourceOrigin("Aphid Tenders", AphidHusbandry.productionPerTick(ants, nest, upgrades, protein.amount)))
                .filter(_.tickResourceU > 0),
              Some(
                ResourceOrigin(
                  "Ants Demand",
                  -ScoutProgression.upkeepCount(ants, exploration) * antsSugarUpkeepTick,
                )
              ),
              Some(ResourceOrigin("Carcass sugar", CarcassProgression.foodOffset(exploration, ants)))
                .filter(_.tickResourceU > 0),
            ).flatten
        }
    )

  val proteinTickGain: BonusOriginGroup = BonusOriginGroup(
    antsSignal.combineWith(nestSignal, upgradesSignal, proteinSignal).map { case (ants, nest, upgrades, protein) =>
      List(ResourceOrigin("Aphid Tenders", -AphidHusbandry.proteinPerTick(ants, nest, upgrades, protein.amount)))
    }
  )

  val tunnelingSpaceTickGain: BonusOriginGroup =
    BonusOriginGroup(
      antTaskCountSignal(AntTask.Tunneler)
        .combineWith(upgradesSignal)
        .map { case (tunnelersAmount, upgrades) =>
          List(
            Some(
              ResourceOrigin(
                AntTask.Tunneler.name,
                tunnelersAmount * ColonyDevelopment.tunnelingPerWorkerTick(upgrades),
              )
            ),
          ).flatten
        }
    )

  def prettyResourceEstDiv(
      bonusGroup: BonusOriginGroup,
      resource: Signal[BasicResource],
  ): ReactiveHtmlElement[HTMLDivElement] = {
    div(
      child <--
        bonusGroup.sum.combineWith(resource).map { case (production, current) =>
          val sumU = if (current.atCapacity && production > 0) 0L else production
          val useMinutes: Boolean = sumU != 0 && Math.abs(sumU) < 20
          val minutesMultiplier: Int = if (useMinutes) 60 else 1

          span(
            if (sumU > 0) "+" else if (sumU < 0) "-" else "",
            UINumbersUtils.prettyNumberStr(minutesMultiplier * Math.abs(sumU) * TicksPerSecond),
            if (useMinutes) "/m" else "/s",
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
                case resource @ ResourceOrigin(_, _) if resource.resourceUSec > 0 =>
                  s"+${UINumbersUtils.prettyNumberStr(resource.resourceUSec)}/sec"
                case resource @ ResourceOrigin(_, _) if resource.resourceUSec < 0 =>
                  s"${UINumbersUtils.prettyNumberStr(resource.resourceUSec)}/sec"
                case ResourceOrigin(_, _) =>
                  s"0/sec"
                case PercentOrigin(_, percent) if percent > 0 =>
                  f"+${percent - 1.0}%.2f%%"
                case PercentOrigin(_, percent) if percent < 0 =>
                  f"${percent - 1.0}%.2f%%"
                case PercentOrigin(_, _) =>
                  "0.00%"
              }
            )
          )
        }
      },
      hr(),
      child <-- resource.combineWith(sumSignal).map { case (resource, sumU) =>
        div(
          className := "d-flex justify-content-between",
          if (sumU >= 0) "To Full: " else "To Empty: ",
          span(
            // TODO deal this small numbers in minutes
            if (sumU > 0) {
              val timeToFull: Long = resource.freeSpace / sumU
              if (timeToFull == 0)
                "0s"
              else
                s"${UINumbersUtils.prettyTimeFromTicks(timeToFull)}"
            } else if (sumU < 0) {
              val timeToEmpty: Long = resource.amount / -sumU
              if (timeToEmpty == 0)
                "0s"
              else
                s"${UINumbersUtils.prettyTimeFromTicks(timeToEmpty)}"
            } else {
              "Never"
            }
          )
        )
      },
    )

}
