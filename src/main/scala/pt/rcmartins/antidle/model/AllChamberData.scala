package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.model.Chamber._
import zio.json._

case class AllChamberData(
    nestChamber: NestChamber = NestChamber(0),
    queenChamber: QueenChamber = QueenChamber(1),
    foodStorageChamber: FoodStorageChamber = FoodStorageChamber(1),
    nurseryChamber: NurseryChamber = NurseryChamber(0),
    aphidChamber: AphidChamber = AphidChamber(0),
    repleteChamber: RepleteChamber = RepleteChamber(0),
)

object AllChamberData {

  implicit val decoder: JsonDecoder[AllChamberData] = DeriveJsonDecoder.gen[AllChamberData]
  implicit val encoder: JsonEncoder[AllChamberData] = DeriveJsonEncoder.gen[AllChamberData]

}
