package pt.rcmartins.antidle.model
import zio.json._
case class Territory(held: Boolean = false, targetWorkers: Int = 10, maintain: Boolean = false,
    nextContestTick: Long = 0L, seed: Long = 0L, round: Long = 0L,
    pendingProtein: Long = 0L, ages: List[AgeCohort] = Nil)
object Territory {
  implicit val encoder: JsonEncoder[Territory] = DeriveJsonEncoder.gen[Territory]
  implicit val decoder: JsonDecoder[Territory] = DeriveJsonDecoder.gen[Territory]
}
