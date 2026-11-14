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
    maxAnts: Long = NestUpgradeBonusMaxAnts,
    maxBuildQueue: Int = 1,
    dryClearingOutpost: Boolean = false,
    dryClearingExtensions: Int = 0,
    aphids: Long = 0L, // Whole animals, not fixed-point worker units.
    aphidsInitialized: Boolean = false, // Missing in legacy saves; populate existing chambers once.
) {

  def maxCarcasses: Int = InitialCarcassSlots + chambers.nurseryChamber.level

  def maxEggsCount: Int = (maxEggs / u).toInt
  def maxAntsCount: Int = (maxAnts / u).toInt

  def royalBroodSlots: Int = Math.min(1, Math.max(0, chambers.royalNurseryChamber.level))

}

object NestAttributes {

  implicit val decoder: JsonDecoder[NestAttributes] = DeriveJsonDecoder.gen[NestAttributes]
  implicit val encoder: JsonEncoder[NestAttributes] = DeriveJsonEncoder.gen[NestAttributes]

  val initial: NestAttributes = NestAttributes(aphidsInitialized = true)

}
