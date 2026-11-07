package pt.rcmartins.antidle.model

import com.raquo.airstream.state.Var
import com.softwaremill.quicklens.ModifyPimp
import pt.rcmartins.antidle.game.Utils._
import pt.rcmartins.antidle.model.AllData.MaxMessages

case class AllData(
    version: Int = AllData.CurrentVersion,
    world: WorldData,
    ants: AntsData,
    basicResources: BasicResources,
    nestAttributes: NestAttributes,
    unlocks: Unlocks,
    upgrades: UpgradesData,
    exploration: ExplorationData,
    statistics: StatisticsData,
    messages: Seq[String],
) {

  def currentTick: Long = world.currentTick

  /** Just checks if there are resources available */
  def hasResources(actionCost: ActionCost): Boolean =
    basicResources.hasResources(actionCost) && ants.hasIdleWorkersForCost(actionCost)

  def hasResourcesUpdate(actionCost: ActionCost)(updateIfPossibleF: AllData => AllData): AllData =
    if (hasResources(actionCost))
      updateIfPossibleF(spendResources(actionCost))
    else
      this

  // Count resources actually retained; wasted production at a full store is not gathered.
  def giveResources(
      sugar: Long = 0L,
      tunnelingSpace: Long = 0L,
      knowledge: Long = 0L,
      protein: Long = 0L,
      water: Long = 0L,
      resin: Long = 0L,
  ): AllData = {
    def received(resource: BasicResources.BasicResource, requested: Long): Long =
      Math.min(Math.max(0L, requested), Math.max(0L, resource.freeSpace))
    val gainedSugar = received(basicResources.sugar, sugar)
    val gainedSpace = received(basicResources.tunnelingSpace, tunnelingSpace)
    val gainedKnowledge = received(basicResources.knowledge, knowledge)
    val gainedProtein = received(basicResources.protein, protein)
    val gainedWater = received(basicResources.water, water)
    val gainedResin = received(basicResources.resin, resin)
    copy(
      basicResources = basicResources.copy(sugar = basicResources.sugar + gainedSugar,
        tunnelingSpace = basicResources.tunnelingSpace + gainedSpace, knowledge = basicResources.knowledge + gainedKnowledge,
        protein = basicResources.protein + gainedProtein, water = basicResources.water + gainedWater, resin = basicResources.resin + gainedResin),
      statistics = statistics.copy(sugarU = statistics.sugarU.addValue(gainedSugar),
        tunnelingSpaceU = statistics.tunnelingSpaceU.addValue(gainedSpace), knowledgeU = statistics.knowledgeU.addValue(gainedKnowledge),
        proteinU = statistics.proteinU.addValue(gainedProtein), waterU = statistics.waterU.addValue(gainedWater), resinU = statistics.resinU.addValue(gainedResin)),
    )
  }

  def spendResources(actionCost: ActionCost): AllData =
    copy(
      basicResources = basicResources.spendResources(actionCost),
    )

  def updateVars(): Unit =
    Var.set(
      worldData -> world,
      antsData -> ants,
      basicResourcesData -> basicResources,
      nestAttributesData -> nestAttributes,
      unlocksData -> unlocks,
      upgradesData -> upgrades,
      explorationData -> exploration,
      statisticsData -> statistics,
      messagesSeq -> messages,
    )

  def addMessage(message: String): AllData =
    this.modify(_.messages).using(oldMessages => (message +: oldMessages).take(MaxMessages))

}

object AllData {

  val CurrentVersion = 6

  private val MaxMessages = 10

  val initial: AllData =
    AllData(
      world = WorldData.initial(System.currentTimeMillis()),
      ants = AntsData.initial,
      basicResources = BasicResources.initial,
      nestAttributes = NestAttributes.initial,
      unlocks = Unlocks.initial,
      upgrades = UpgradesData.initial,
      exploration = ExplorationData.initial,
      statistics = StatisticsData.initial,
      messages = Seq.empty,
    )

  def simple(
      world: WorldData,
      ants: AntsData,
      basic: BasicResources,
      next: NestAttributes,
      unlocks: Unlocks,
      upgrades: UpgradesData,
      exploration: ExplorationData,
      statistics: StatisticsData,
      messages: Seq[String]
  ): AllData =
    AllData(
      version = CurrentVersion,
      world = world,
      ants = ants,
      basicResources = basic,
      nestAttributes = next,
      unlocks = unlocks,
      upgrades = upgrades,
      exploration = exploration,
      statistics = statistics,
      messages = messages,
    )

}
