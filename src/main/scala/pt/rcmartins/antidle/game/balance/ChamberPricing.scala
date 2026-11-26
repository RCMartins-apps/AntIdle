package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.model.ActionCost
import pt.rcmartins.antidle.model.Chamber.ChamberType
import zio.json._

/** Chamber build prices in fixed-point units. Level zero is the first purchase. */
object ChamberPricing {
  @jsonNoExtraFields
  case class LinearCap(base: Long, perLevel: Long)

  object LinearCap {
    implicit val decoder: JsonDecoder[LinearCap] = DeriveJsonDecoder.gen[LinearCap]
  }

  @jsonNoExtraFields
  case class Price(
      base: Long,
      multiplier: Double = 1.0,
      perLevel: Long = 0L,
      cap: Option[LinearCap] = None
  ) {
    def at(level: Int): Long = {
      val n = Math.max(0, level).toLong
      val scaled = (base * Math.pow(multiplier, Math.min(n, 100L).toDouble)).toLong
      val raw = BigInt(scaled) + BigInt(perLevel) * n
      val limit =
        cap.map(c => BigInt(c.base) + BigInt(c.perLevel) * n).getOrElse(BigInt(Long.MaxValue))
      raw.min(limit).min(BigInt(Long.MaxValue)).toLong
    }

  }

  object Price {
    implicit val decoder: JsonDecoder[Price] = DeriveJsonDecoder.gen[Price]
  }

  private val chambers =
    Set("Nest", "Queen", "FoodStorage", "Nursery", "Aphid", "Replete", "RoyalNursery")

  private val resources = Set("sugar", "protein", "water", "tunnelingSpace", "knowledge", "resin")
  private val MaxBase = 9007199254740991L

  private[balance] def load(json: String): Either[String, Map[String, Map[String, Price]]] =
    json.fromJson[Map[String, Map[String, Price]]].flatMap { catalog =>
      if (catalog.keySet != chambers) Left("Expected every known chamber ID")
      else {
        val invalid = catalog.iterator
          .flatMap { case (chamber, costs) =>
            costs.iterator.collectFirst {
              case (resource, price)
                  if !resources(resource) ||
                    !validAmount(price.base) || !validAmount(price.perLevel) ||
                    !(price.multiplier >= 1.0 && price.multiplier <= 10.0) ||
                    price.cap.exists(cap => !validAmount(cap.base) || !validAmount(cap.perLevel)) =>
                s"Invalid price for $chamber.$resource"
            }
          }
          .nextOption()
        invalid.toLeft(catalog)
      }
    }

  private def validAmount(value: Long): Boolean = value >= 0L && value <= MaxBase

  def validate(json: String): Either[String, Unit] = load(json).map(_ => ())

  private lazy val prices = load(UpgradeOverrides.chambersJson)
    .fold(
      error => throw new IllegalArgumentException(s"src/main/balance/chambers.json: $error"),
      identity
    )

  def cost(kind: ChamberType, level: Int): ActionCost = {
    val rules = prices(kind.toString)
    def forResource(id: String): Long = rules.get(id).fold(0L)(_.at(level))
    ActionCost(
      sugar = forResource("sugar"),
      protein = forResource("protein"),
      water = forResource("water"),
      tunnelingSpace = forResource("tunnelingSpace"),
      knowledge = forResource("knowledge"),
      resin = forResource("resin")
    )
  }

}
