package pt.rcmartins.antidle.game

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

  val LayEggSugarCost: Long = 10 * u

  val (defaultTaskCollectSugarTick: Long, defaultTaskCollectSugarSecond: Long) =
    tickAndSeconds(perSecondValue = (0.4 * u).toLong)

  val (sugarCollectorPenaltyTick: Long, sugarCollectorPenaltySecond: Long) =
    tickAndSeconds(perSecondValue = (0.005 * u).toLong)

  val (defaultTaskTunnelingSpaceTick: Long, defaultTaskTunnelingSpaceSecond: Long) =
    tickAndSeconds(perSecondValue = (1.0 * u).toLong)

  val (antsSugarUpkeepTick: Long, antsSugarUpkeepSecond: Long) =
    tickAndSeconds(perSecondValue = (0.2 * u).toLong)

  val NestUpgradeBonusMaxTunnelingSpace: Long = 500 * u
  val NestUpgradeBonusMaxAnts: Long = 10 * u
  val QueenChamberBonusMaxAnts: Long = 5 * u
  val QueenChamberBonusMaxEggs: Long = 2 * u
  val FoodStorageChamberBonusMaxSugar: Long = 1000 * u

  val AntDeathRiskThreshold: Long = 10 * u

  val LayEggAction = "Raise Worker Egg"

  val CarcassFood: Long = 120 * u
  val CarcassProtein: Long = 5 * u
  val CarcassCarriers: Long = 5 * u
  val CarcassRetrievalTicks: Long = 10 * TicksPerSecond
  val InitialCarcassSlots: Int = 1
  val NurseryBonusMaxAnts: Long = 3 * u
  val ScoutSugarCost: Long = 50 * u
  val ScoutProteinCost: Long = 2 * u
  val ScoutGrowthTicks: Long = 30 * TicksPerSecond
  val DistantExplorationTicks: Long = 45 * TicksPerSecond

}
