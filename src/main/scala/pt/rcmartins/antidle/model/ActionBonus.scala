package pt.rcmartins.antidle.model

case class ActionBonus(
    sugar: Long = 0L,
    eggs: Long = 0L,
    colonyPointsEachWorker: Long = 0L,
    maxSugar: Long = 0L,
    maxColonyPoints: Long = 0L,
    maxEggs: Long = 0L,
    maxWorkers: Long = 0L,
    maxScouts: Long = 0L,
)

object ActionBonus {

  val empty: ActionBonus = ActionBonus()

}
