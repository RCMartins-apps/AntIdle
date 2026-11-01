package pt.rcmartins.antidle.model

import zio.json._

sealed trait AntBrood {

  def initialTick: Long
  def name: String

  def isEgg: Boolean
  def isLarvae: Boolean
  def isPupae: Boolean

}

object AntBrood {

  implicit val decoder: JsonDecoder[AntBrood] = DeriveJsonDecoder.gen[AntBrood]
  implicit val encoder: JsonEncoder[AntBrood] = DeriveJsonEncoder.gen[AntBrood]

  case class Egg(
      initialTick: Long,
  ) extends AntBrood {
    val isEgg: Boolean = true
    val isLarvae: Boolean = false
    val isPupae: Boolean = false
    val name: String = "Egg"
  }

  case class ScoutEgg(initialTick: Long) extends AntBrood {
    val isEgg: Boolean = true
    val isLarvae: Boolean = false
    val isPupae: Boolean = false
    val name: String = "Scout Egg"
  }

  case class Larvae(
      initialTick: Long,
  ) extends AntBrood {
    val isEgg: Boolean = false
    val isLarvae: Boolean = true
    val isPupae: Boolean = false
    val name: String = "Larvae"
  }

  case class Pupae(
      initialTick: Long,
  ) extends AntBrood {
    val isEgg: Boolean = false
    val isLarvae: Boolean = false
    val isPupae: Boolean = true
    val name: String = "Pupae"
  }

  val defaultTicksToGrow: Long = 50

}
