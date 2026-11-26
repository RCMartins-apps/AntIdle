package pt.rcmartins.antidle.game.balance

import munit.FunSuite
import pt.rcmartins.antidle.game.Constants.u
import pt.rcmartins.antidle.model.ActionCost
import pt.rcmartins.antidle.model.UpgradesData.UpgradeType._

class UpgradeTuningSuite extends FunSuite {
  private val defaults = UpgradeBalance.defaults
  private def tune(json: String) = UpgradeTuning.resolve(defaults, json)

  test("empty overrides preserve all currently shipped balance") {
    assertEquals(tune("{}"), Right(defaults))
  }

  test("the editor catalog provides an actual game icon for every cost field") {
    val catalog = UpgradeTuning.catalog("{}").fold(error => fail(error), identity)
    assertEquals(catalog.icons.keySet, ActionCost.empty.productElementNames.toSet)
    assert(catalog.icons.values.forall(_.path.nonEmpty))
  }

  test("overrides retain other fields and use stable prerequisite IDs and discovery IDs") {
    val edited = tune(
      """{"StandardWorkers":{"cost":{"sugar":805000},"requirements":{"minimumSmallWorkers":6,"earlyResearch":true}},
        |"ImproveSugarCollectorTask2":{"requirements":{"upgrades":["UnlockQueensChamber"],"discovery":"resin-seep"}},
        |"RepleteStorage":{"requirements":{"discovery":null}}}""".stripMargin
    ).fold(error => fail(error), identity)
    val before = defaults(StandardWorkers)
    assertEquals(edited(StandardWorkers).cost, before.cost.copy(sugar = 80L * u + u / 2L))
    assertEquals(
      edited(StandardWorkers).requirements,
      before.requirements.copy(minimumSmallWorkers = 6, earlyResearch = true)
    )
    assertEquals(
      edited(ImproveSugarCollectorTask2).requirements.upgrades,
      List(UnlockQueensChamber)
    )
    assertEquals(edited(ImproveSugarCollectorTask2).requirements.discovery, Some("resin-seep"))
    assertEquals(edited(RepleteStorage).requirements.discovery, None)
    assertEquals(edited(BroodCare), defaults(BroodCare))
  }

  test("invalid references, numbers and fields fail before being applied") {
    Seq(
      """{"Missing":{"title":"Anything"}}""",
      """{"StandardWorkers":{"cost":{"suggar":3}}}""",
      """{"StandardWorkers":{"cost":{"sugar":-1}}}""",
      """{"StandardWorkers":{"cost":{"sugar":1.5}}}""",
      """{"StandardWorkers":{"requirements":{"minimumSmallWorkers":-2}}}""",
      """{"StandardWorkers":{"requirements":{"upgrades":["Missing"]}}}""",
      """{"StandardWorkers":{"requirements":{"discovery":"missing"}}}""",
      """{"StandardWorkers":{"title":null}}""",
    ).foreach(candidate => assert(tune(candidate).isLeft, candidate))
  }

  test("purchase dependency cycles are rejected") {
    assert(
      tune(
        """{"UnlockQueensChamber":{"requirements":{"upgrades":["UnlockFoodStorageChamber"]}}}"""
      ).left.toOption.exists(_.contains("Dependency cycle"))
    )
    assert(
      tune(
        """{"UnlockExplorerTask":{"requirements":{"upgrades":["ExpeditionSupplies1"]}}}"""
      ).left.toOption.exists(_.contains("Dependency cycle"))
    )
  }

}
