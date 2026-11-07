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

  case class Developing(initialTick: Long, kind: String = "worker", care: Long = 0L) extends AntBrood {
    def small: Boolean = kind == "small" || kind == "emergency-small"
    def totalCare: Long = (if (small) 75L else if (kind == "scout") 450L else 600L) * 10000L
    def eggEnd: Long = totalCare / 6L
    def larvaEnd: Long = totalCare * 5L / 6L
    def isEgg: Boolean = care < eggEnd
    def isLarvae: Boolean = care >= eggEnd && care < larvaEnd
    def isPupae: Boolean = care >= larvaEnd
    def name: String = (if (kind == "emergency-small") "Emergency small worker" else if (small) "Small worker" else if (kind == "scout") "Scout" else "Worker") +
      (if (isEgg) " egg" else if (isLarvae) " larva" else " pupa")
  }

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
