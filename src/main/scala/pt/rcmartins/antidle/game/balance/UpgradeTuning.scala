package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.game.{Constants, DiscoveryProgression, ResourceIcons}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import scala.annotation.tailrec
import zio.Chunk
import zio.json._
import zio.json.ast.Json

/** Load the shared catalog and validate the editor's unsaved changes. */
object UpgradeTuning {
  case class Entry(id: String, base: UpgradeDefinition, current: UpgradeDefinition)
  object Entry {
    implicit val encoder: JsonEncoder[Entry] = DeriveJsonEncoder.gen[Entry]
  }

  case class CostIcon(id: String, path: String)
  object CostIcon {
    implicit val encoder: JsonEncoder[CostIcon] = DeriveJsonEncoder.gen[CostIcon]
  }

  private def costIcons: Map[String, CostIcon] = Map(
    "sugar" -> ResourceIcons.Sugar,
    "protein" -> ResourceIcons.Protein,
    "water" -> ResourceIcons.Water,
    "tunnelingSpace" -> ResourceIcons.Tunneling,
    "knowledge" -> ResourceIcons.Knowledge,
    "idleWorkers" -> ResourceIcons.Workers,
    "resin" -> ResourceIcons.Resin,
  ).map { case (field, icon) => field -> CostIcon(icon.id, icon.path) }

  case class Catalog(
      unit: Long,
      discoveries: Map[String, String],
      icons: Map[String, CostIcon],
      upgrades: Seq[Entry]
  )

  object Catalog {
    implicit val encoder: JsonEncoder[Catalog] = DeriveJsonEncoder.gen[Catalog]
  }

  def catalog(candidate: String): Either[String, Catalog] = {
    val defaults = UpgradeBalance.definitions
    load(candidate).map(effective =>
      Catalog(
        Constants.u,
        DiscoveryProgression.all.map(d => d.id -> d.title).toMap,
        costIcons,
        UpgradeType.all.map(id => Entry(id.name, defaults(id), effective(id)))
      )
    )
  }

  def load(candidate: String): Either[String, Map[UpgradeType, UpgradeDefinition]] =
    candidate.fromJson[Json].flatMap {
      case Json.Obj(fields) =>
        val ids = UpgradeType.all.iterator.map(id => id.name -> id).toMap
        unique(fields, "Upgrades").flatMap { _ =>
          if (fields.map(_._1).toSet != ids.keySet)
            Left("Catalog must contain exactly the known upgrade IDs")
          else
            fields
              .foldLeft[Either[String, Map[UpgradeType, UpgradeDefinition]]](Right(Map.empty)) {
                case (result, (name, raw)) =>
                  for {
                    entries <- result
                    decoded <- raw.toJson
                      .fromJson[UpgradeDefinition]
                      .left
                      .map(error => s"$name: $error")
                    expected <- decoded.toJson.fromJson[Json]
                    _ <- checkFields(raw, expected, name)
                    valid <- validate(decoded, name)
                  } yield entries.updated(ids(name), valid)
              }
              .flatMap(definitions => checkCycles(definitions).map(_ => definitions))
        }
      case _ => Left("Expected a complete object keyed by upgrade ID")
    }

  private def unique(fields: Chunk[(String, Json)], path: String): Either[String, Unit] =
    if (fields.map(_._1).distinct.size == fields.size) Right(())
    else Left(s"$path: repeated field")

  private def checkFields(raw: Json, expected: Json, path: String): Either[String, Unit] =
    (raw, expected) match {
      case (Json.Obj(actual), Json.Obj(required)) =>
        val known = required.toMap
        unique(actual, path).flatMap { _ =>
          val requiredKeys = known.filterNot { case (key, value) =>
            key == "bonuses" && value == Json.Arr()
          }.keySet
          val actualKeys = actual.map(_._1).toSet
          if (!requiredKeys.subsetOf(actualKeys) || !actualKeys.subsetOf(known.keySet))
            Left(s"$path: missing or unknown fields")
          else
            actual.foldLeft[Either[String, Unit]](Right(())) { case (result, (key, value)) =>
              result.flatMap(_ => checkFields(value, known(key), s"$path.$key"))
            }
        }
      case (Json.Arr(actual), Json.Arr(required)) =>
        actual.zip(required).zipWithIndex.foldLeft[Either[String, Unit]](Right(())) {
          case (result, ((value, schema), index)) =>
            result.flatMap(_ => checkFields(value, schema, s"$path[$index]"))
        }
      case _ => Right(())
    }

  private def validate(
      definition: UpgradeDefinition,
      name: String
  ): Either[String, UpgradeDefinition] = {
    val required = definition.requirements
    if (definition.title.trim.isEmpty) Left(s"$name: title must not be empty")
    else if (
      definition.cost.productIterator
        .collect { case amount: Long => amount }
        .exists(n => n < 0L || n > 9007199254740991L)
    )
      Left(s"$name: costs must be non-negative, safely representable fixed-point integers")
    else if (required.productIterator.collect { case n: Int => n }.exists(_ < 0))
      Left(s"$name: thresholds must be non-negative")
    else if (required.upgrades.distinct != required.upgrades)
      Left(s"$name: repeated upgrade prerequisite")
    else if (required.discovery.exists(id => !DiscoveryProgression.all.exists(_.id == id)))
      Left(s"$name: unknown discovery")
    else if (
      definition.bonuses.exists {
        case bonus: UpgradeBonus.ProductionPercent =>
          !Set("sugarCollectors", "scrapsGatherers").contains(bonus.source) ||
            bonus.percent < 1 || bonus.percent > 100
        case _: UpgradeBonus.StorageCapacity => true
      }
    ) Left(s"$name: production bonus needs a supported source and 1 to 100 percent")
    else Right(definition)
  }

  private def checkCycles(
      definitions: Map[UpgradeType, UpgradeDefinition]
  ): Either[String, Unit] = {
    val dependencies = definitions.map { case (id, definition) =>
      id -> definition.requirements.upgrades.toSet
    }

    @tailrec def dropReady(remaining: Set[UpgradeType]): Either[String, Unit] =
      if (remaining.isEmpty) Right(())
      else {
        val blocked = remaining.filter(id => dependencies(id).exists(remaining.contains))
        if (blocked == remaining)
          Left(s"Dependency cycle blocks: ${blocked.toList.map(_.name).sorted.mkString(", ")}")
        else dropReady(blocked)
      }
    dropReady(dependencies.keySet)
  }

}
