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
  private val capacityResources = Map("Nest" -> "tunnelingSpace", "FoodStorage" -> "sugar")

  @jsonNoExtraFields
  case class Definition(costs: Map[String, Price], bonuses: List[UpgradeBonus] = Nil)

  object Definition {
    implicit val decoder: JsonDecoder[Definition] = DeriveJsonDecoder.gen[Definition]
  }

  private[balance] def load(json: String): Either[String, Map[String, Definition]] =
    json.fromJson[Map[String, Definition]].flatMap { catalog =>
      if (catalog.keySet != chambers) Left("Expected every known chamber ID")
      else {
        val invalid = catalog.iterator
          .flatMap { case (chamber, definition) =>
            val bonuses = definition.bonuses
            val invalidBonus = bonuses.size > 1 || bonuses.exists {
              case UpgradeBonus.StorageCapacity(resource, amount) =>
                !capacityResources.get(chamber).contains(resource) || !validAmount(amount)
              case _ => true
            }
            if (invalidBonus) Some(s"Invalid capacity bonus for $chamber")
            else
              definition.costs.iterator.collectFirst {
                case (resource, price)
                    if !resources(resource) ||
                      !validAmount(price.base) || !validAmount(price.perLevel) ||
                      !(price.multiplier >= 1.0 && price.multiplier <= 10.0) ||
                      price.cap
                        .exists(cap => !validAmount(cap.base) || !validAmount(cap.perLevel)) =>
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
    val rules = prices(kind.toString).costs
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

  def capacityBonus(kind: ChamberType): Long =
    prices(kind.toString).bonuses
      .collectFirst { case UpgradeBonus.StorageCapacity(_, amount) =>
        amount
      }
      .getOrElse(0L)

}
