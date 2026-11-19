package pt.rcmartins.antidle.game

import pt.rcmartins.antidle.model._

object SurveyOrders {
  def nextDiscovery(data: ExplorationData, site: String): Option[DiscoveryProgression.Discovery] =
    DiscoveryProgression.all.find(d => d.destination == site && !d.found(data) && d.trips <= 3)

  def cancel(data: AllData): AllData = data.copy(exploration = data.exploration.copy(surveyOrder = None))

  def start(data: AllData, site: String, scouts: Int): AllData = {
    val discovery = nextDiscovery(data.exploration, site)
    if (data.exploration.surveyOrder.nonEmpty || !ExplorationMap.canScout(data, site, scouts) ||
        discovery.forall(_.trips - ExplorationMap.studies(data.exploration, site) < 2)) data
    else {
      val started = ExplorationMap.scout(data, site, scouts)
      started.copy(exploration = started.exploration.copy(surveyOrder = discovery.map(d => SurveyOrder(site, scouts, d.trips))))
    }
  }

  def tick(data: AllData): AllData = data.exploration.surveyOrder match {
    case Some(order) if ExplorationMap.studies(data.exploration, order.site) >= order.target ||
        !ExplorationMap.availableSites(data.ants).exists(_.id == order.site) ||
        !ExplorationMap.revealed(data.exploration, order.site) ||
        order.scouts < 1 || order.scouts > ExplorationMap.MaxSurveyScouts ||
        !nextDiscovery(data.exploration, order.site).exists(_.trips == order.target) => cancel(data)
    case Some(order) => ExplorationMap.scout(data, order.site, order.scouts)
    case None => data
  }
}
