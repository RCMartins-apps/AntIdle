package pt.rcmartins.antidle.model

case class ActionBonus(
    sugar: Long = 0L,
    eggs: Long = 0L,
    maxSugar: Long = 0L,
    maxTunnelingSpace: Long = 0L,
    maxEggs: Long = 0L,
    maxAnts: Long = 0L,
    maxCarcasses: Int = 0,
)

object ActionBonus {

  val empty: ActionBonus = ActionBonus()

}
