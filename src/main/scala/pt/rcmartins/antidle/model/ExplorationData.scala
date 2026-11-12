package pt.rcmartins.antidle.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class ExplorationData(
    explorationParties: List[ExplorationParty] = Nil,
    carcasses: List[Carcass] = Nil,
    discoveredCarcasses: List[Carcass] = Nil,
    retrievals: List[CarcassRetrieval] = Nil,
    carcassDiscovered: Boolean = false,
    proteinDiscovered: Boolean = false,
    revealedSites: Int = 0,
    depleted: Map[String, Long] = Map.empty,
    suppliesLevel: Int = 0, // Legacy save input only; migrated to standard upgrades and cleared on load.
    surveys: Map[String, Int] = Map.empty,
    sugarTrail: SugarTrail = SugarTrail(), // Legacy reserved nectar, returned on load.
    collectionTrails: List[CollectionTrail] = Nil,
    territory: Territory = Territory(),
)

object ExplorationData {

  implicit val decoder: JsonDecoder[ExplorationData] = DeriveJsonDecoder.gen[ExplorationData]
  implicit val encoder: JsonEncoder[ExplorationData] = DeriveJsonEncoder.gen[ExplorationData]

  val initial: ExplorationData = ExplorationData()

}
