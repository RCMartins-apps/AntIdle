package pt.rcmartins.antidle.balance

import zio.json._

case class Policy(name: String, earlyPopulation: Int, population: Int, royalPopulation: Int,
    partyWorkers: Int, extraScouts: Int, tunnelers: Int, nurses: Int, aphids: Int,
    upgradeOrder: List[String])
object Policy { implicit val decoder: JsonDecoder[Policy] = DeriveJsonDecoder.gen[Policy]; implicit val encoder: JsonEncoder[Policy] = DeriveJsonEncoder.gen[Policy] }
case class Config(seeds: List[Long], policies: List[Policy], maxTicks: Long, decisionTicks: Int,
    openingClicks: Int, clicksPerSecond: Int, foundings: Int, sampleTicks: Int)
object Config { implicit val decoder: JsonDecoder[Config] = DeriveJsonDecoder.gen[Config]; implicit val encoder: JsonEncoder[Config] = DeriveJsonEncoder.gen[Config] }
case class Milestone(colony: Int, name: String, tick: Long, colonyTicks: Long, checkpoint: String)
object Milestone { implicit val encoder: JsonEncoder[Milestone] = DeriveJsonEncoder.gen[Milestone] }
case class Event(tick: Long, colony: Int, action: String, category: String)
object Event { implicit val encoder: JsonEncoder[Event] = DeriveJsonEncoder.gen[Event] }
case class Wait(start: Long, end: Long, colony: Int, goal: String, missing: List[String])
object Wait { implicit val encoder: JsonEncoder[Wait] = DeriveJsonEncoder.gen[Wait] }
case class Sample(tick: Long, colony: Int, workers: Int, smallWorkers: Int, scouts: Int,
    sugar: Double, protein: Double, knowledge: Double, water: Double, resin: Double,
    tunneling: Double, sugarWorkers: Int, foodWorkers: Int, careWorkers: Int,
    tunnelers: Int, awayWorkers: Int, starvation: Boolean)
object Sample { implicit val encoder: JsonEncoder[Sample] = DeriveJsonEncoder.gen[Sample] }
case class ResourceTime(name: String, emptyTicks: Long, fullTicks: Long)
object ResourceTime { implicit val encoder: JsonEncoder[ResourceTime] = DeriveJsonEncoder.gen[ResourceTime] }
case class Run(policy: Policy, seed: Long, status: String, ticks: Long, completedFoundings: Int,
    actions: Long, manualClicks: Int, eventTraceTruncated: Boolean, events: Vector[Event],
    milestones: Vector[Milestone], waits: Vector[Wait], samples: Vector[Sample],
    resourceTimes: List[ResourceTime], starvationDeaths: Int, agingDeaths: Int)
object Run { implicit val encoder: JsonEncoder[Run] = DeriveJsonEncoder.gen[Run] }
case class Report(schemaVersion: Int, gameSaveVersion: Int, ticksPerSecond: Int, config: Config, runs: Vector[Run])
object Report { implicit val encoder: JsonEncoder[Report] = DeriveJsonEncoder.gen[Report] }
