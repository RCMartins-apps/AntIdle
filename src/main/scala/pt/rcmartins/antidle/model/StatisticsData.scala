package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.model.StatisticsData._
import zio.json.{jsonAliases, DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class StatisticsData(
    sugarU: StatLong = StatLong(),
    tunnelingSpaceU: StatLong = StatLong(),
    @jsonAliases("colonyPointsU") knowledgeU: StatLong = StatLong(),
    eggsCount: StatInt = StatInt(),
    maxAliveWorkers: StatInt = StatInt(),
    explorationsCount: StatInt = StatInt(),
    explorationsWorkersSentU: StatLong = StatLong(),
)

object StatisticsData {

  implicit val decoder: JsonDecoder[StatisticsData] = DeriveJsonDecoder.gen[StatisticsData]
  implicit val encoder: JsonEncoder[StatisticsData] = DeriveJsonEncoder.gen[StatisticsData]

  case class StatInt(thisRun: Int = 0, total: Int = 0) {

    def addValue(value: Int): StatInt =
      copy(thisRun = thisRun + value, total = total + value)

    def max(value: Int): StatInt =
      copy(thisRun = Math.max(thisRun, value), total = Math.max(total, value))

  }

  object StatInt {
    implicit val decoder: JsonDecoder[StatInt] = DeriveJsonDecoder.gen[StatInt]
    implicit val encoder: JsonEncoder[StatInt] = DeriveJsonEncoder.gen[StatInt]
  }

  case class StatLong(thisRun: Long = 0, total: Long = 0) {

    def addValue(sugarGained: Long): StatLong =
      copy(thisRun = thisRun + sugarGained, total = total + sugarGained)

  }

  object StatLong {
    implicit val decoder: JsonDecoder[StatLong] = DeriveJsonDecoder.gen[StatLong]
    implicit val encoder: JsonEncoder[StatLong] = DeriveJsonEncoder.gen[StatLong]
  }

  val initial: StatisticsData = StatisticsData()

}
