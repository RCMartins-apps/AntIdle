package pt.rcmartins.antidle.balance

import scala.scalajs.js
import zio.json._
import pt.rcmartins.antidle.game.Constants
import pt.rcmartins.antidle.game.saves.AllDataForSave
import pt.rcmartins.antidle.model.AllData

object BalanceMain {
  def main(ignored: Array[String]): Unit = {
    val argv = js.Dynamic.global.process.argv.asInstanceOf[js.Array[String]]
    val args = argv.slice(2, argv.length).toArray
    require(args.length == 2, "Usage: node main.js config.json output-directory")
    val fs = js.Dynamic.global.require("fs")
    val config = fs.readFileSync(args(0), "utf8").asInstanceOf[String].fromJson[Config].fold(e => throw new IllegalArgumentException(e), identity)
    require(config.seeds.nonEmpty && config.policies.nonEmpty && config.maxTicks > 0L && config.maxTicks <= 50000000L)
    require(config.decisionTicks > 0 && config.sampleTicks > 0 && config.openingClicks >= 0 && config.openingClicks <= 1000)
    require(config.clicksPerSecond > 0 && config.clicksPerSecond <= Constants.TicksPerSecond && config.foundings > 0)
    require(config.policies.map(_.name).distinct.size == config.policies.size && config.seeds.distinct.size == config.seeds.size)
    config.policies.foreach { p =>
      require(p.name.matches("[a-zA-Z0-9_-]+") && p.earlyPopulation >= 5 && p.population >= 5 && p.royalPopulation >= 5)
      require(p.partyWorkers >= 1 && p.partyWorkers <= pt.rcmartins.antidle.game.ExplorationProgression.MaxWorkers / Constants.u && p.extraScouts >= 0 && p.extraScouts <= pt.rcmartins.antidle.game.ExplorationProgression.MaxExtraScouts && p.tunnelers >= 0 && p.nurses >= 0 && p.aphids >= 0)
      require(p.upgradeOrder.distinct.size == p.upgradeOrder.size && p.upgradeOrder.forall(n => pt.rcmartins.antidle.model.UpgradesData.UpgradeType.all.exists(_.name == n)), "Unknown or repeated upgrade")
    }
    def write(path: String, text: String): Unit = {
      val full = s"${args(1)}/$path"
      fs.mkdirSync(full.substring(0, full.lastIndexOf('/')), js.Dynamic.literal(recursive = true))
      fs.writeFileSync(full, text)
    }
    val runs = for (p <- config.policies; seed <- config.seeds) yield BalanceSimulator.run(config, p, seed,
      (path, data) => write(s"checkpoints/$path", AllDataForSave.fromAllData(data).toJson), println)
    write("report.json", Report(1, AllData.CurrentVersion, Constants.TicksPerSecond, config, runs.toVector).toJson)
  }
}
