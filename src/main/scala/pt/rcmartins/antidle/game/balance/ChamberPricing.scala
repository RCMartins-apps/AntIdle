package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.game.Constants.u
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
  private val MaxSlots = 1000

  case class BonusField(
      kind: String,
      targetKey: String,
      target: String,
      valueKey: String,
      label: String,
      scale: Long,
      max: Long
  )

  object BonusField {
    implicit val encoder: JsonEncoder[BonusField] = DeriveJsonEncoder.gen[BonusField]
  }

  private def storage(resource: String, label: String): BonusField =
    BonusField("storageCapacity", "resource", resource, "amount", label, u, MaxBase)

  private def slots(slot: String, label: String): BonusField =
    BonusField("slotCapacity", "slot", slot, "count", label, 1L, MaxSlots.toLong)

  val bonusFields: Map[String, List[BonusField]] = Map(
    "Nest" -> List(storage("tunnelingSpace", "Tunneling Space")),
    "FoodStorage" -> List(storage("sugar", "Sugar"), storage("protein", "Protein (with research)")),
    "Nursery" -> List(
      slots("brood", "Brood places"),
      slots("broodNurse", "Brood Nurse jobs"),
      slots("carcass", "Carcass slots")
    )
  )

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
            val targets = bonuses.collect {
              case UpgradeBonus.StorageCapacity(resource, _) => resource
              case UpgradeBonus.SlotCapacity(slot, _)        => slot
            }
            def supports(kind: String, target: String): Boolean =
              bonusFields.getOrElse(chamber, Nil).exists(f => f.kind == kind && f.target == target)

            val invalidBonus = targets.distinct.size != bonuses.size || bonuses.exists {
              case UpgradeBonus.StorageCapacity(resource, amount) =>
                !supports("storageCapacity", resource) || !validAmount(amount)
              case UpgradeBonus.SlotCapacity(slot, count) =>
                !supports("slotCapacity", slot) || count < 0 || count > MaxSlots
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

  def capacityBonus(kind: ChamberType, resource: String): Long =
    prices(kind.toString).bonuses
      .collectFirst { case UpgradeBonus.StorageCapacity(`resource`, amount) =>
        amount
      }
      .getOrElse(0L)

  def nurserySlotsPerLevel(slot: String): Int =
    prices("Nursery").bonuses
      .collectFirst { case UpgradeBonus.SlotCapacity(`slot`, count) =>
        count
      }
      .getOrElse(0)

}
