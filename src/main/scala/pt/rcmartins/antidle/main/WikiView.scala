package pt.rcmartins.antidle.main

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom.HTMLDivElement
import pt.rcmartins.antidle.game.{BroodProgression, Midden, PopulationAging}
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType

private[main] object WikiView {
  def render: ReactiveHtmlElement[HTMLDivElement] =
    div(
      className := "settings-wiki",
      sectionTag(
        h5("Brood"),
        h6("Egg orders"),
        p("Egg orders share the queen’s laying queue. Supplies are paid when laying starts. All development stages occupy brood places."),
        h6("Larval care"),
        p(
          className := "brood-feeding",
          child.text <-- antsSignal.map { ants =>
            if (ants.standardWorkersUnlocked)
              "Workers and scouts need food during larval care. Food shortages pause care without losing progress."
            else "Small workers develop without food during larval care."
          }.distinct
        ),
        child.maybe <-- ifUnlockedOpt(nestSignal.map(BroodProgression.priorityAvailable).distinct)(
          div(
            h6("Brood priority"),
            p("When care is limited, the chosen brood gets a larger share. Other brood still gets care. Egg orders keep their place.")
          )
        ),
        child.maybe <-- ifUnlockedOpt(upgradesSignal.map(_(UpgradeType.QueensRoutine).unlocked).distinct)(
          div(
            h6("Queen’s Routine"),
            p("The routine keeps your worker target while respecting the sugar reserve. Adults, developing workers, and manual worker orders count toward the target. Scout orders stay manual."),
            p("Aging vacancies are refilled from the idle or default pool. If the colony runs out of workers, the queen can gather supplies to raise one emergency worker without an open brood place.")
          )
        )
      ),
      child.maybe <-- ifUnlockedOpt(antsSignal.map(_.ages.active).distinct)(
        sectionTag(
          h5("Aging"),
          p(child.text <-- upgradesSignal.map(upgrades =>
            s"New adults live ${PopulationAging.lifespanText(upgrades)} minutes of simulated time. Aging continues during expeditions and carcass retrievals. Overdue ants die on return."
          )),
          p("Stored-time multipliers advance aging with the colony. Deaths are summarized once per minute.")
        )
      ),
      child.maybe <-- ifUnlockedOpt(nestSignal.map(Midden.visible).distinct)(
        sectionTag(
          h5("Refuse"),
          p("Larval feeding creates refuse. High refuse closes brood places to new eggs. Refuse Carriers clear it.")
        )
      )
    )
}
