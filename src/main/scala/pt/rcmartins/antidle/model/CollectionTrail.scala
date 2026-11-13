package pt.rcmartins.antidle.model

import zio.json._

/** Desired staffing in groups of five; ordinary home-worker aging still applies. */
case class CollectionTrail(destination: String, workers: Long)
object CollectionTrail {
  implicit val decoder: JsonDecoder[CollectionTrail] = DeriveJsonDecoder.gen[CollectionTrail]
  implicit val encoder: JsonEncoder[CollectionTrail] = DeriveJsonEncoder.gen[CollectionTrail]
}
