package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model.Chamber.ChamberType

object Constants {

  // UI Constants

  val ImportModalId: String = "importModal"
  val NewGameModalId: String = "newGameModal"
  val AlertTimeout: Int = 5000

  // Time Constants

  val TicksPerSecond: Int = 5
  val millsPerTick: Int = 1000 / TicksPerSecond
  val TicksPerDay: Int = 10 * TicksPerSecond // 10 seconds
  val TicksPerSeason: Int = TicksPerDay * 100 // 1000 seconds (16.6 minutes)
  val TicksPerYear: Int = TicksPerSeason * 4 // 4000 seconds (66.6 minutes)

  val MaxTicksCatchUpMult: Int = 2
  val MaxTicksStored: Int = TicksPerSecond * 60 * 60 * 1 // 1 hour of stored ticks?

  val seasonStr: IndexedSeq[String] = IndexedSeq("Spring", "Summer", "Autumn", "Winter")

  private def tickAndSeconds(perSecondValue: Long): (Long, Long) =
    (perSecondValue / TicksPerSecond, perSecondValue)

  def ExplorationTimeTicks: Long = 15 * TicksPerSecond

  // Other Constants

  @inline final val u: Long = 10000L

  val MaxExponent = 100
  val exponent0_95: IndexedSeq[Double] = (0 to MaxExponent).map(Math.pow(0.95, _))
  val exponent1_14: IndexedSeq[Double] = (0 to MaxExponent).map(Math.pow(1.14, _))
  val exponent1_25: IndexedSeq[Double] = (0 to MaxExponent).map(Math.pow(1.25, _))
  val exponent1_50: IndexedSeq[Double] = (0 to MaxExponent).map(Math.pow(1.50, _))
  val exponent1_75: IndexedSeq[Double] = (0 to MaxExponent).map(Math.pow(1.75, _))

  val LayEggSugarCost: Long = 10 * u

  val PlayerGatherSugarClickAmount: Long = 1 * u
  val (defaultTaskCollectSugarTick: Long, defaultTaskCollectSugarSecond: Long) =
    tickAndSeconds(perSecondValue = (0.4 * u).toLong)

  val (defaultTaskTunnelingSpaceTick: Long, defaultTaskTunnelingSpaceSecond: Long) =
    tickAndSeconds(perSecondValue = (1.0 * u).toLong)

  val (defaultNestLevelColonyPointsTick: Long, defaultNestLevelColonyPointsSecond: Long) =
    tickAndSeconds(perSecondValue = (0.01 * u).toLong)

  val (antsSugarUpkeepTick: Long, antsSugarUpkeepSecond: Long) =
    tickAndSeconds(perSecondValue = (0.2 * u).toLong)

  val NestUpgradeBonusMaxColonyPoints: Long = 500 * u
  val NestUpgradeBonusMaxWorkers: Long = 10 * u
  val QueenChamberBonusMaxWorkers: Long = 5 * u
  val QueenChamberBonusMaxEggs: Long = 2 * u
  val FoodStorageChamberBonusMaxSugar: Long = 1000 * u

  val AntDeathRiskThreshold: Long = 10 * u

  val GatherSugarAction = "Gather Sugar"
  val LayEggAction = "Lay Egg"

  def getChamberTunnelingSpaceNeeded(chamberType: ChamberType, level: Int): Long = {
    def cost(base: Long, multiplier: IndexedSeq[Double]): Long =
      (base * multiplier(level)).toLong

    chamberType match {
      case ChamberType.Nest        => cost(20 * u, exponent1_50)
      case ChamberType.Queen       => cost(25 * u, exponent1_50)
      case ChamberType.FoodStorage => cost(50 * u, exponent1_14)
      case ChamberType.Nursery     => cost(100 * u, exponent1_50)
    }
  }

  val CarcassFood: Long = 120 * u
  val CarcassProtein: Long = 5 * u
  val HarvesterFoodPerTick: Long = (0.8 * u / TicksPerSecond).toLong
  val MaxStoredCarcasses: Int = 3
  val NurseryProteinCost: Long = 3 * u
  val NurseryBonusMaxScouts: Long = 3 * u
  val ScoutSugarCost: Long = 50 * u
  val ScoutProteinCost: Long = 2 * u
  val ScoutGrowthTicks: Long = 30 * TicksPerSecond
  val DistantExplorationTicks: Long = 45 * TicksPerSecond

  val MaxExplorationParties: Int = 1

  val SugarImprovementBonusPerc: Double = 1.15

}
