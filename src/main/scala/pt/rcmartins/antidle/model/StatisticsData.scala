package pt.rcmartins.antidle.model

import pt.rcmartins.antidle.model.StatisticsData._
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class StatisticsData(
    sugarU: StatLong = StatLong(),
    tunnelingSpaceU: StatLong = StatLong(),
    knowledgeU: StatLong = StatLong(),
    proteinU: StatLong = StatLong(),
    waterU: StatLong = StatLong(),
    resinU: StatLong = StatLong(),
    royalEggsRaised: StatInt = StatInt(),
    daughterQueensMatured: StatInt = StatInt(),
    explorationsCompleted: StatInt = StatInt(),
    carcassesRetrieved: StatInt = StatInt(),
    workersMatured: StatInt = StatInt(),
    scoutsMatured: StatInt = StatInt(),
    workersLostToAging: StatInt = StatInt(),
    scoutsLostToAging: StatInt = StatInt(),
    starvationDeaths: StatInt = StatInt(),
    raidDeaths: StatInt = StatInt(),
    repletesPromoted: StatInt = StatInt(),
    repletesLostToAging: StatInt = StatInt(),
    eggsCount: StatInt = StatInt(),
    maxAliveWorkers: StatInt = StatInt(),
    explorationsCount: StatInt = StatInt(),
    explorationsWorkersSentU: StatLong = StatLong(),
)

object StatisticsData {

  implicit val decoder: JsonDecoder[StatisticsData] = DeriveJsonDecoder.gen[StatisticsData]
  implicit val encoder: JsonEncoder[StatisticsData] = DeriveJsonEncoder.gen[StatisticsData]

  case class StatInt(thisRun: Int = 0, total: Int = 0) {

    def addValue(value: Long): StatInt = {
      def add(current: Int): Int = (current.toLong + Math.min(Int.MaxValue.toLong, Math.max(0L, value))).min(Int.MaxValue.toLong).toInt
      copy(thisRun = add(thisRun), total = add(total))
    }

    def max(value: Int): StatInt =
      copy(thisRun = Math.max(thisRun, value), total = Math.max(total, value))

  }

  object StatInt {
    implicit val decoder: JsonDecoder[StatInt] = DeriveJsonDecoder.gen[StatInt]
    implicit val encoder: JsonEncoder[StatInt] = DeriveJsonEncoder.gen[StatInt]
  }

  case class StatLong(thisRun: Long = 0, total: Long = 0) {

    def addValue(value: Long): StatLong = {
      def add(current: Long): Long = current + Math.min(Math.max(0L, value), Long.MaxValue - current)
      copy(thisRun = add(thisRun), total = add(total))
    }

  }

  object StatLong {
    implicit val decoder: JsonDecoder[StatLong] = DeriveJsonDecoder.gen[StatLong]
    implicit val encoder: JsonEncoder[StatLong] = DeriveJsonEncoder.gen[StatLong]
  }

  def newColony(stats: StatisticsData): StatisticsData = stats.copy(
    sugarU = stats.sugarU.copy(thisRun = 0),
    tunnelingSpaceU = stats.tunnelingSpaceU.copy(thisRun = 0),
    knowledgeU = stats.knowledgeU.copy(thisRun = 0),
    proteinU = stats.proteinU.copy(thisRun = 0),
    waterU = stats.waterU.copy(thisRun = 0),
    resinU = stats.resinU.copy(thisRun = 0),
    royalEggsRaised = stats.royalEggsRaised.copy(thisRun = 0),
    daughterQueensMatured = stats.daughterQueensMatured.copy(thisRun = 0),
    explorationsCompleted = stats.explorationsCompleted.copy(thisRun = 0),
    carcassesRetrieved = stats.carcassesRetrieved.copy(thisRun = 0),
    workersMatured = stats.workersMatured.copy(thisRun = 0),
    scoutsMatured = stats.scoutsMatured.copy(thisRun = 0),
    workersLostToAging = stats.workersLostToAging.copy(thisRun = 0),
    scoutsLostToAging = stats.scoutsLostToAging.copy(thisRun = 0),
    starvationDeaths = stats.starvationDeaths.copy(thisRun = 0),
    raidDeaths = stats.raidDeaths.copy(thisRun = 0),
    repletesPromoted = stats.repletesPromoted.copy(thisRun = 0),
    repletesLostToAging = stats.repletesLostToAging.copy(thisRun = 0),
    eggsCount = stats.eggsCount.copy(thisRun = 0),
    maxAliveWorkers = stats.maxAliveWorkers.copy(thisRun = 0),
    explorationsCount = stats.explorationsCount.copy(thisRun = 0),
    explorationsWorkersSentU = stats.explorationsWorkersSentU.copy(thisRun = 0)
  )

  val initial: StatisticsData = StatisticsData()

}
