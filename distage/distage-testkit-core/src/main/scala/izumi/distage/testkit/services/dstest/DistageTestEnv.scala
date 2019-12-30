package izumi.distage.testkit.services.dstest

import izumi.distage.framework.activation.PruningPlanMergingPolicyLoggedImpl
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.definition.{Activation, BootstrapModuleDef}
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.TestConfig
import izumi.fundamentals.platform.cache.SyncCache
import izumi.fundamentals.platform.language.unused
import izumi.logstage.api.IzLogger

trait DistageTestEnv {
  protected[distage] def loadEnvironment(logger: IzLogger, testConfig: TestConfig): TestEnvironment = {
    val roles = loadRoles(logger)
    val mergeStrategy = makeMergeStrategy(logger)

    import izumi.fundamentals.platform.strings.IzString._
    def doMake(): TestEnvironment = {
      makeEnv(logger, testConfig, roles, mergeStrategy)
    }

    if (DebugProperties.`izumi.distage.testkit.environment.cache`.asBoolean(true)) {
      DistageTestEnv.cache.getOrCompute(DistageTestEnv.EnvCacheKey(testConfig, roles, mergeStrategy), doMake())
    } else {
      doMake()
    }
  }

  private def makeEnv(logger: IzLogger, testConfig: TestConfig, roles: RolesInfo, mergeStrategy: PluginMergeStrategy): TestEnvironment = {
    val plugins = testConfig.pluginSource.load()
    val appModule = mergeStrategy.merge(plugins.app)
    val bootstrapModule = mergeStrategy.merge(plugins.bootstrap)
    val availableActivations = ActivationInfoExtractor.findAvailableChoices(logger, appModule)
    val activation = testConfig.activation

    val bsModule = bootstrapModule overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicyLoggedImpl]
      make[ActivationInfo].fromValue(availableActivations)
      make[Activation].fromValue(activation)
    }

    TestEnvironment(
      bsModule = bsModule,
      appModule = appModule,
      roles = roles,
      activationInfo = availableActivations,
      activation = activation,
      memoizationRoots = testConfig.memoizationRoots,
    )
  }

  protected def loadRoles(@unused logger: IzLogger): RolesInfo = {
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def makeMergeStrategy(@unused lateLogger: IzLogger): PluginMergeStrategy = {
    SimplePluginMergeStrategy
  }

}

object DistageTestEnv {

  private lazy val cache = new SyncCache[EnvCacheKey, TestEnvironment]

  case class EnvCacheKey(config: TestConfig, rolesInfo: RolesInfo, mergeStrategy: PluginMergeStrategy)

}