package pt.rcmartins.antidle.game.balance

import pt.rcmartins.antidle.game.{Constants, DiscoveryProgression, ResourceIcons}
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType
import scala.annotation.tailrec
import zio.Chunk
import zio.json._
import zio.json.ast.Json

/** Resolve per-field balance overrides against the same definitions used in play. */
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
    val defaults = UpgradeBalance.defaults
    resolve(defaults, candidate).map(effective =>
      Catalog(
        Constants.u,
        DiscoveryProgression.all.map(d => d.id -> d.title).toMap,
        costIcons,
        UpgradeType.all.map(id => Entry(id.name, defaults(id), effective(id)))
      )
    )
  }

  private[balance] def resolve(
      defaults: Map[UpgradeType, UpgradeDefinition],
      candidate: String
  ): Either[String, Map[UpgradeType, UpgradeDefinition]] =
    candidate.fromJson[Json].flatMap {
      case Json.Obj(fields) if fields.isEmpty => Right(defaults)
      case Json.Obj(fields) =>
        val ids = defaults.keysIterator.map(t => t.name -> t).toMap
        val changed = unique(fields, "Upgrades").flatMap { _ =>
          fields.foldLeft[Either[String, Map[UpgradeType, UpgradeDefinition]]](Right(defaults)) {
            case (result, (name, change: Json.Obj)) =>
              for {
                current <- result
                id <- ids.get(name).toRight(s"Unknown upgrade: $name")
                source <- current(id).toJson.fromJson[Json]
                base <- source match {
                  case obj: Json.Obj => Right(obj)
                  case _             => Left(s"$name: expected an upgrade object")
                }
                patched <- merge(base, change, name)
                decoded <- patched.toJson
                  .fromJson[UpgradeDefinition]
                  .left
                  .map(error => s"$name: $error")
                validated <- validate(decoded, name)
              } yield current.updated(id, validated)
            case (_, (name, _)) => Left(s"$name: expected an object")
          }
        }
        changed.flatMap(definitions => checkCycles(definitions).map(_ => definitions))
      case _ => Left("Expected an object keyed by upgrade ID")
    }

  private def unique(fields: Chunk[(String, Json)], path: String): Either[String, Unit] =
    if (fields.map(_._1).distinct.size == fields.size) Right(())
    else Left(s"$path: repeated field")

  private def merge(base: Json.Obj, changes: Json.Obj, path: String): Either[String, Json.Obj] =
    unique(changes.fields, path).flatMap { _ =>
      changes.fields.foldLeft[Either[String, Json.Obj]](Right(base)) {
        case (current, (key, change)) =>
          val field = s"$path.$key"
          current.flatMap { obj =>
            obj.fields.find(_._1 == key).map(_._2).toRight(s"Unknown field: $field").flatMap {
              original =>
                val patched = (original, change) match {
                  case (a: Json.Obj, b: Json.Obj)   => merge(a, b, field)
                  case (_: Json.Obj, _)             => Left(s"$field: expected an object")
                  case (Json.Null, value: Json.Str) => Right(value)
                  case (_: Json.Str, Json.Null) if key == "discovery" => Right(Json.Null)
                  case (_: Json.Bool, value: Json.Bool)               => Right(value)
                  case (_: Json.Num, value: Json.Num)                 => Right(value)
                  case (_: Json.Str, value: Json.Str)                 => Right(value)
                  case (_: Json.Arr, value: Json.Arr)                 => Right(value)
                  case (Json.Null, Json.Null)                         => Right(Json.Null)
                  case _ => Left(s"$field: unexpected value type")
                }
                patched.map(value =>
                  Json.Obj(obj.fields.map { case (name, old) =>
                    name -> (if (name == key) value else old)
                  })
                )
            }
          }
      }
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
