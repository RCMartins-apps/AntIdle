package pt.rcmartins.antidle.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ExplorationParty(
    amountWorkers: Long,
    startingTick: Long,
    targetTick: Long,
    scouts: Long = 0L,
    destination: String = "surroundings",
    suppliesLevel: Int = 0,
    ages: TravelAges = TravelAges(),
    extraScouts: Int = 0,
    raidOutcome: Option[RaidOutcome] = None,
    mission: String = "haul",
    cargo: ActionCost = ActionCost(),
)

object ExplorationParty {

  implicit val decoder: JsonDecoder[ExplorationParty] = DeriveJsonDecoder.gen[ExplorationParty]
  implicit val encoder: JsonEncoder[ExplorationParty] = DeriveJsonEncoder.gen[ExplorationParty]

  def create(amountWorkers: Long, currentTick: Long, explorationTimeTicks: Long): ExplorationParty =
    ExplorationParty(
      amountWorkers = amountWorkers,
      startingTick = currentTick,
      targetTick = currentTick + explorationTimeTicks,
    )

}
