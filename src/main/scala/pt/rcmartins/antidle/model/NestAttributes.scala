package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.game.Constants._
import zio.json._

case class NestAttributes(
    chambers: AllChamberData = AllChamberData(),
    buildQueue: List[BuildTask] = Nil,
//    dirt: Long = 0,
//    deadAnts: Long = 0,
//    detritus: Long = 0,
    maxEggs: Long = QueenChamberBonusMaxEggs,
    maxWorkers: Long = NestUpgradeBonusMaxWorkers,
    maxBuildQueue: Int = 1,
    maxScouts: Long = 0L,
) {

  def maxEggsCount: Int = (maxEggs / u).toInt
  // Legacy save fields now contribute to a single colony capacity.
  def maxAnts: Long = maxWorkers + maxScouts
  def maxAntsCount: Int = (maxAnts / u).toInt

}

object NestAttributes {

  implicit val decoder: JsonDecoder[NestAttributes] = DeriveJsonDecoder.gen[NestAttributes]
  implicit val encoder: JsonEncoder[NestAttributes] = DeriveJsonEncoder.gen[NestAttributes]

  val initial: NestAttributes = NestAttributes()

}
