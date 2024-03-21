/*
 * Copyright (C) 2022 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slack.gradle

import java.io.File
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import slack.gradle.anvil.AnvilMode
import slack.gradle.artifacts.SgpArtifact
import slack.gradle.util.PropertyResolver
import slack.gradle.util.getOrCreateExtra
import slack.gradle.util.sneakyNull

/**
 * (Mostly Gradle) properties for configuration of SlackPlugin.
 *
 * Order attempted as described by [PropertyResolver.providerFor].
 */
public class SlackProperties
internal constructor(
  private val project: Project,
  startParameterProperty: (String) -> Provider<String>,
  globalLocalProperty: (String) -> Provider<String>,
) {
  private val resolver = PropertyResolver(project, startParameterProperty, globalLocalProperty)

  private fun presenceProperty(key: String): Boolean = optionalStringProperty(key) != null

  private fun fileProperty(key: String): File? = optionalStringProperty(key)?.let(project::file)

  private fun intProperty(key: String, defaultValue: Int = -1): Int =
    resolver.intValue(key, defaultValue = defaultValue)

  private fun booleanProperty(key: String, defaultValue: Boolean = false): Boolean =
    resolver.booleanValue(key, defaultValue = defaultValue)

  private fun stringProperty(key: String): String =
    optionalStringProperty(key)
      ?: error("No property for $key found and no default value was provided.")

  private fun stringProperty(key: String, defaultValue: String): String =
    optionalStringProperty(key, defaultValue)!!

  private fun optionalStringProperty(
    key: String,
    defaultValue: String? = null,
    blankIsNull: Boolean = false,
  ): String? =
    resolver.optionalStringValue(key, defaultValue = defaultValue)?.takeUnless {
      blankIsNull && it.isBlank()
    }

  internal val versions: SlackVersions by lazy { SlackVersions(project.getVersionsCatalog()) }

  /** Indicates that this android library project has variants. Flag-only, value is ignored. */
  public val libraryWithVariants: Boolean
    get() = booleanProperty("slack.gradle.config.libraryWithVariants")

  /**
   * Indicates that the gradle versions plugin should allow unstable versions. By default unstable
   * versions are excluded due to the frequent androidx alpha/beta/rc cycle noise. Flag-only, value
   * is ignored.
   */
  public val versionsPluginAllowUnstable: Boolean
    get() = booleanProperty("slack.gradle.config.versionsPluginAllowUnstable")

  /** Opt-out flag to skip the androidx dependency check. Should only be used for debugging. */
  public val skipAndroidxCheck: Boolean
    get() = booleanProperty("slack.gradle.skipAndroidXCheck")

  /** Version code used for debug APK outputs. */
  public val debugVersionCode: Int
    get() = intProperty("slack.gradle.debugVersionCode", 90009999)

  /** User string used for debug APK outputs. */
  public val debugUserString: String
    get() = stringProperty("slack.gradle.debugUserString", "debug")

  /** Opt-in flag to enable snapshots repos, used for the dependencies build shadow job. */
  public val enableSnapshots: Boolean
    get() = booleanProperty("slack.gradle.config.enableSnapshots")

  /** Opt-in flag to enable mavenLocal repos, used for local testing. */
  public val enableMavenLocal: Boolean
    get() = booleanProperty("slack.gradle.config.enableMavenLocal")

  /**
   * Flag to indicate that this project should have no api dependencies, such as if it's solely an
   * annotation processor.
   */
  public val rakeNoApi: Boolean
    get() = booleanProperty("slack.gradle.config.rake.noapi")

  /**
   * Flag to enable the Gradle Dependency Analysis Plugin, which is disabled by default due to
   * https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/204
   */
  public val enableAnalysisPlugin: Boolean
    get() = booleanProperty("slack.gradle.config.enableAnalysisPlugin")

  /**
   * Flag to indicate this project should be exempted from platforms, usually platform projects
   * themselves.
   */
  public val noPlatform: Boolean
    get() = booleanProperty("slack.gradle.config.noPlatform")

  /** Property corresponding to the supported languages in GA builds */
  public val supportedLanguages: String
    get() = stringProperty("slack.supportedLanguages")

  /** Property corresponding to the supported languages in Internal builds */
  public val supportedLanguagesInternal: String
    get() = stringProperty("slack.supportedLanguagesInternal")

  /** Property corresponding to the supported languages in Beta builds */
  public val supportedLanguagesBeta: String
    get() = stringProperty("slack.supportedLanguagesBeta")

  /**
   * Property corresponding to the file path of a custom versions.json file for use with
   * dependencies shadow jobs.
   */
  public val versionsJson: File?
    get() = fileProperty("slack.versionsJson")

  /**
   * An alias name to a libs.versions.toml bundle for common Android Compose dependencies that
   * should be added to android projects with compose enabled
   */
  public val defaultComposeAndroidBundleAlias: String?
    get() = optionalStringProperty("slack.compose.android.defaultBundleAlias")

  /**
   * Enables live literals. Note that they are disabled by default due to
   * https://issuetracker.google.com/issues/274207650 and
   * https://issuetracker.google.com/issues/274231394.
   */
  public val composeEnableLiveLiterals: Boolean
    get() = booleanProperty("slack.compose.android.enableLiveLiterals", false)

  /**
   * Common compose compiler options.
   *
   * Format is a comma-separated list of key-value pairs, e.g. "key1=value1,key2=value2". Keys
   * should be the simple name of the compose compiler option, no prefixes needed.
   */
  public val composeCommonCompilerOptions: Provider<List<String>>
    get() =
      resolver
        .providerFor("sgp.compose.commonCompilerOptions")
        .map { value -> value.split(",").map { it.trim() } }
        .orElse(emptyList())

  /**
   * If true, uses the AndroidX compose compiler [SlackVersions.composeCompiler] for Compose
   * Multiplatform compilations rather than the Jetbrains one. This can be useful in testing where
   * AndroidX's compiler is farther ahead.
   */
  public val forceAndroidXComposeCompilerForComposeMultiplatform: Boolean
    get() = booleanProperty("sgp.compose.multiplatform.forceAndroidXComposeCompiler", false)

  /**
   * When this property is present, the "internalRelease" build variant will have an application id
   * of "com.Slack.prototype", instead of "com.Slack.internal".
   *
   * We build and distribute "prototype" builds that are equivalent to the "internalRelease" build
   * variants, except with a different application id so they can be installed side-by-side. To
   * avoid adding a new flavor & flavor dimension (or other somewhat hacky solutions like sharing
   * source sets), we swap the application id suffix at configuration time.
   */
  public val usePrototypeAppId: Boolean
    get() = presenceProperty("slack.usePrototypeAppId")

  /**
   * Property corresponding to the SDK versions we test in Robolectric tests. Its value should be a
   * comma-separated list of SDK ints to download.
   */
  public val robolectricTestSdks: List<Int>
    get() =
      stringProperty("slack.robolectricTestSdks").splitToSequence(",").map { it.toInt() }.toList()

  /** Property corresponding to the preinstrumented jars version (the `-i2` suffix in jars). */
  public val robolectricIVersion: Int
    get() = intProperty("slack.robolectricIVersion")

  /** Opt out for -Werror, should only be used for prototype projects. */
  public val allowWarnings: Boolean
    get() = booleanProperty("slack.allowWarnings")

  /**
   * Anvil generator projects that should always be included when Anvil is enabled.
   *
   * This should be semicolon-delimited Gradle project paths.
   */
  public val anvilGeneratorProjects: String?
    get() = optionalStringProperty("slack.anvil.generatorProjects")

  /**
   * Anvil runtime projects that should always be included when Anvil is enabled.
   *
   * This should be semicolon-delimited Gradle project paths.
   */
  public val anvilRuntimeProjects: String?
    get() = optionalStringProperty("slack.anvil.runtimeProjects")

  /** Log Slack extension configuration state verbosely. */
  public val slackExtensionVerbose: Boolean
    get() = booleanProperty("slack.extension.verbose")

  /**
   * Flag for Error-Prone auto-patching. Enable when running an auto-patch of EP, such as when it's
   * being introduced to a new module or upgrading EP itself.
   */
  public val errorProneAutoPatch: Boolean
    get() = booleanProperty("slack.epAutoPatch")

  /**
   * Error-Prone checks that should be considered errors.
   *
   * This should be colon-delimited string.
   *
   * Example: "AnnotationMirrorToString:AutoValueSubclassLeaked"
   */
  public val errorProneCheckNamesAsErrors: String?
    get() = optionalStringProperty("slack.epCheckNamesAsErrors")

  /**
   * Flag for Nullaway baselining. When enabled along with [errorProneAutoPatch], existing
   * nullability issues will be baselined with a `castToNonNull` call to wrap it.
   */
  public val nullawayBaseline: Boolean
    get() = booleanProperty("slack.nullaway.baseline")

  /**
   * Ndk version to use for android projects.
   *
   * Latest versions can be found at https://developer.android.com/ndk/downloads
   */
  public val ndkVersion: String?
    get() = optionalStringProperty("slack.ndkVersion")

  /**
   * Enables verbose logging in miscellaneous places of SGP. This is intended to be a less noisy
   * alternative to running gradle with `--info` or `--debug`.
   */
  public val verboseLogging: Boolean
    get() = resolver.booleanValue("sgp.logging.verbose")

  /** Flag to enable verbose logging in unit tests. */
  public val testVerboseLogging: Boolean
    get() = booleanProperty("slack.test.verboseLogging") || verboseLogging

  /**
   * Flag to enable kapt in tests. By default these are disabled due to this undesirable (but
   * surprisingly intented) behavior of running kapt + stub generation even if no processors are
   * present.
   *
   * See https://youtrack.jetbrains.com/issue/KT-29481#focus=Comments-27-4651462.0-0
   */
  public val enableKaptInTests: Boolean
    get() = booleanProperty("slack.enabled-kapt-in-tests")

  /** Flag to enable errors only in lint checks. */
  public val lintErrorsOnly: Boolean
    get() = booleanProperty("slack.lint.errors-only")

  /** File name to use for a project's lint baseline. */
  public val lintBaselineFileName: String?
    get() = optionalStringProperty("slack.lint.baseline-file-name", blankIsNull = true)

  /** Flag to control whether or not lint checks test sources. */
  public val lintCheckTestSources: Boolean
    get() = booleanProperty("sgp.lint.checkTestSources", true)

  /** Flag to control whether or not lint checks ignores test sources. */
  public val lintIgnoreTestSources: Boolean
    get() = booleanProperty("sgp.lint.ignoreTestSources", false)

  /**
   * Flag to control which agp version should be used for lint. Optional. Value should be a version
   * key in `libs.versions.toml`,
   */
  public val lintVersionOverride: String?
    get() = optionalStringProperty("sgp.lint.agpVersion")

  /**
   * Flag to indicate whether this project is a test library (such as test utils, test fixtures,
   * etc).
   */
  public val isTestLibrary: Boolean
    get() = booleanProperty("sgp.isTestLibrary", false) || project.name == "test-fixtures"

  /**
   * At the time of writing, AGP does not support running lint on `com.android.test` projects. This
   * is a flag to eventually support this in the future.
   *
   * https://issuetracker.google.com/issues/208765813
   */
  public val enableLintInAndroidTestProjects: Boolean
    get() = booleanProperty("sgp.lint.enableOnAndroidTestProjects", false)

  /** Flag to enable/disable KSP. */
  public val allowKsp: Boolean
    get() = booleanProperty("slack.allow-ksp")

  /** Flag to enable/disable Moshi-IR. */
  public val allowMoshiIr: Boolean
    get() = booleanProperty("slack.allow-moshi-ir")

  /** Flag to enable/disable moshi proguard rule gen. */
  public val moshixGenerateProguardRules: Boolean
    get() = booleanProperty("moshix.generateProguardRules", defaultValue = true)

  /** Flag to enable/disable Napt. */
  public val allowNapt: Boolean
    get() = booleanProperty("slack.allow-napt")

  /** Flag to enable/disable Dagger KSP. */
  public val allowDaggerKsp: Boolean
    get() = booleanProperty("slack.ksp.allow-dagger")

  /** Flag to connect SqlDelight sources to KSP. */
  public val kspConnectSqlDelight: Boolean
    get() = booleanProperty("sgp.ksp.connect.sqldelight")

  /** Flag to connect ViewBinding sources to KSP. */
  public val kspConnectViewBinding: Boolean
    get() = booleanProperty("sgp.ksp.connect.viewbinding")

  /** Variants that should be disabled in a given subproject. */
  public val disabledVariants: String?
    get() = optionalStringProperty("slack.disabledVariants")

  /**
   * The Slack-specific kotlin.daemon.jvmargs computed by bootstrap.
   *
   * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute other
   * projects.
   */
  public val kotlinDaemonArgs: String
    get() = stringProperty(KOTLIN_DAEMON_ARGS_KEY, defaultValue = "")

  /**
   * Flag to enable ciUnitTest on this project. Default is true.
   *
   * When enabled, a task named "ciUnitTest" will be created in this project, which will depend on
   * the unit test task for a single build variant (e.g. "testReleaseUnitTest").
   */
  public val ciUnitTestEnabled: Boolean
    get() = booleanProperty("slack.ci-unit-test.enable", defaultValue = true)

  /** CI unit test variant (Android only). Defaults to `release`. */
  public val ciUnitTestVariant: String
    get() = stringProperty("slack.ci-unit-test.variant", "release")

  /** If enabled, applies the kotlinx-kover plugin to projects using ciUnitTest. */
  public val ciUnitTestEnableKover: Boolean
    get() = booleanProperty("slack.ci-unit-test.enableKover", false)

  /**
   * Parallelism multiplier to use for unit tests. This should be a float value that is multiplied
   * by the number of cores. The value can be a fraction. Default is 0.5.
   */
  public val unitTestParallelismMultiplier: Float
    get() {
      val rawValue = stringProperty("slack.unit-test.parallelismMultiplier", "0.5")
      val floatValue = rawValue.toFloatOrNull()
      require(floatValue != null && floatValue > 0) {
        "Invalid value for slack.unit-test.parallelismMultiplier: '$rawValue'"
      }
      return floatValue
    }

  /** Controls how often to fork the JVM in unit tests. Default is 1000. */
  public val unitTestForkEvery: Long
    get() = intProperty("slack.unit-test.forkEvery", 1000).toLong()

  /**
   * Flag to enable ciLint on a project. Default is true.
   *
   * When enabled, a task named "ciLint" will be created in this project, which will depend on the
   * all the lint tasks in the project.
   */
  public val ciLintEnabled: Boolean
    get() = booleanProperty("slack.ci-lint.enable", defaultValue = true)

  /**
   * Comma-separated list of CI lint variants to run (Android only). Default when unspecified will
   * lint all variants.
   */
  public val ciLintVariants: String?
    get() = optionalStringProperty("slack.ci-lint.variants")

  /** Flag for enabling test orchestrator. */
  public val useOrchestrator: Boolean
    get() = booleanProperty("orchestrator")

  /**
   * Location for robolectric-core to be referenced by app. Temporary till we have a better solution
   * for "always add these" type of deps.
   *
   * Should be `:path:to:robolectric-core` format
   */
  public val robolectricCoreProject: Project
    get() = project.project(stringProperty("slack.location.robolectric-core"))

  /**
   * Gradle path to a platform project to be referenced by other projects.
   *
   * Should be `:path:to:slack-platform` format
   *
   * @see Platforms
   */
  public val platformProjectPath: String?
    get() = optionalStringProperty("slack.location.slack-platform")

  /**
   * Opt-in path for commit hooks in the consuming repo that should be automatically installed
   * automatically. This is passed into [org.gradle.api.Project.file] from the root project.
   *
   * Corresponds to git's `core.hooksPath`.
   */
  public val gitHooksFile: File?
    get() = fileProperty("slack.git.hooksPath")

  /**
   * Opt-in path for a pre-commit hook in the consuming repo that should be automatically installed
   * automatically. This is passed into [org.gradle.api.Project.file] from the root project.
   *
   * Corresponds to git's `blame.ignoreRevsFile`.
   */
  public val gitIgnoreRevsFile: File?
    get() = fileProperty("slack.git.ignoreRevsFile")

  /**
   * Optional file location for an `affected_projects.txt` file that contains a list of projects
   * affected in this build.
   */
  public val affectedProjects: File?
    get() = fileProperty("slack.avoidance.affectedProjectsFile")

  /* Controls for Java/JVM/JDK versions uses in compilations and execution of tests. */

  /** Flag to enable strict JDK mode, forcing some things like JAVA_HOME. */
  public val strictJdk: Boolean
    get() = booleanProperty("slackToolchainsStrict", defaultValue = true)

  /** The JDK version to use for compilations. */
  public val jdkVersion: Int
    get() = versions.jdk

  /** The JDK runtime to target for compilations. */
  public val jvmTarget: Int
    get() = versions.jvmTarget

  /** Android cache fix plugin. */
  public val enableAndroidCacheFix: Boolean = booleanProperty("slack.plugins.android-cache-fix")

  /**
   * Optional override for buildToolsVersion in Android projects. Sometimes temporarily necessary to
   * pick up new fixes.
   */
  public val buildToolsVersionOverride: String? =
    optionalStringProperty("sgp.android.buildToolsVersionOverride")

  /* Controls for auto-applied plugins. */
  public val autoApplyTestRetry: Boolean
    get() = booleanProperty("slack.auto-apply.test-retry", defaultValue = true)

  public val autoApplySpotless: Boolean
    get() = booleanProperty("slack.auto-apply.spotless", defaultValue = true)

  public val autoApplyDetekt: Boolean
    get() = booleanProperty("slack.auto-apply.detekt", defaultValue = true)

  public val autoApplyNullaway: Boolean
    get() = booleanProperty("slack.auto-apply.nullaway", defaultValue = true)

  public val autoApplyCacheFix: Boolean
    get() = booleanProperty("slack.auto-apply.cache-fix", defaultValue = true)

  public val autoApplySortDependencies: Boolean
    get() = booleanProperty("slack.auto-apply.sort-dependencies", defaultValue = true)

  /* Test retry controls. */
  public enum class TestRetryPluginType {
    RETRY_PLUGIN,
    GE
  }

  public val testRetryPluginType: TestRetryPluginType
    get() =
      stringProperty("slack.test.retry.pluginType", TestRetryPluginType.RETRY_PLUGIN.name)
        .let(TestRetryPluginType::valueOf)

  public val testRetryFailOnPassedAfterRetry: Provider<Boolean>
    get() =
      resolver.booleanProvider("slack.test.retry.failOnPassedAfterRetry", defaultValue = false)

  public val testRetryMaxFailures: Provider<Int>
    get() = resolver.intProvider("slack.test.retry.maxFailures", defaultValue = 20)

  public val testRetryMaxRetries: Provider<Int>
    get() = resolver.intProvider("slack.test.retry.maxRetries", defaultValue = 1)

  /* Detekt configs. */
  /** Detekt config files, evaluated from rootProject.file(...). */
  public val detektConfigs: List<String>?
    get() = optionalStringProperty("slack.detekt.configs")?.split(",")
  /** Detekt baseline file, evaluated from project.layout.projectDirectory.file(...). */
  public val detektBaselineFileName: String?
    get() = optionalStringProperty("slack.detekt.baseline-file-name", blankIsNull = true)
  /** Enables full detekt mode (with type resolution). Off by default due to performance issues. */
  public val enableFullDetekt: Boolean
    get() = booleanProperty("slack.detekt.full")

  /** Comma-separated set of projects to ignore in sorting dependencies. */
  public val sortDependenciesIgnore: String?
    get() = optionalStringProperty("slack.sortDependencies.ignore")

  /** Enables verbose debug logging across the plugin. */
  public val debug: Boolean
    get() = booleanProperty("slack.debug", defaultValue = false)

  /** A comma-separated list of configurations to use in affected project detection. */
  public val affectedProjectConfigurations: String?
    get() = optionalStringProperty("slack.avoidance.affected-project-configurations")

  /**
   * Flag to, when true, makes [affectedProjectConfigurations] build upon the defaults rather than
   * replace them.
   */
  public val buildUponDefaultAffectedProjectConfigurations: Boolean
    get() = booleanProperty("slack.avoidance.build-upon-default-affected-project-configurations")

  /**
   * Global control for enabling stricter validation of projects, such as ensuring Kotlin projects
   * have at least one `.kt` source file.
   *
   * Note that these are expected to be slow and not used anywhere outside of debugging or CI.
   *
   * Granular controls should depend on this check + include their own opt-out check as-needed.
   */
  public val strictMode: Boolean
    get() = booleanProperty("slack.strict", defaultValue = false)

  /** Specific toggle for validating manifests in androidTest sources. */
  public val strictValidateAndroidTestManifest: Boolean
    get() = booleanProperty("slack.strict.validateAndroidTestManifests", defaultValue = true)

  /**
   * Always enables resources in android unit tests. Only present for benchmarking purposes and
   * should otherwise be off.
   */
  public val alwaysEnableResourcesInTests: Boolean
    get() = booleanProperty("slack.gradle.config.test.alwaysEnableResources", defaultValue = false)

  /** Global toggle to enable bugsnag. Note this still respects variant filters. */
  public val bugsnagEnabled: Provider<Boolean>
    get() = resolver.booleanProvider("slack.gradle.config.bugsnag.enabled")

  /** Branch pattern for git branches Bugsnag should be enabled on. */
  public val bugsnagEnabledBranchPattern: Provider<String>
    get() = resolver.optionalStringProvider("slack.gradle.config.bugsnag.enabledBranchPattern")

  /** Global boolean that controls whether mod score is enabled on this project. */
  public val modScoreGlobalEnabled: Boolean
    get() = resolver.booleanValue("slack.gradle.config.modscore.enabled")

  /**
   * Per-project boolean that allows for excluding this project from mod score.
   *
   * Note this should only be applied to projects that cannot be depended on.
   */
  public val modScoreIgnore: Boolean
    get() = resolver.booleanValue("slack.gradle.config.modscore.ignore")

  /** Experimental flag to enable logging thermal throttling on macOS devices. */
  public val logThermals: Boolean
    get() = resolver.booleanValue("slack.log-thermals", defaultValue = false)

  /**
   * Enables applying common build tags. We are likely to remove these in favor of Gradle's
   * first-party plugin.
   */
  public val applyCommonBuildTags: Boolean
    get() = resolver.booleanValue("sgp.ge.apply-common-build-tags", defaultValue = true)

  /**
   * Enables eager configuration of [SgpArtifact] publishing in subprojects. This is behind a flag
   * as a failsafe while we try different approaches to allow lenient resolution.
   *
   * @see StandardProjectConfigurations.setUpSubprojectArtifactPublishing
   */
  public val eagerlyConfigureArtifactPublishing: Boolean
    get() = resolver.booleanValue("sgp.artifacts.configure-eagerly", defaultValue = false)

  /**
   * Force-disables Anvil regardless of `SlackExtension.dagger()` settings, useful for K2 testing
   * where Anvil is unsupported.
   */
  public val disableAnvilForK2Testing: Boolean
    get() = resolver.booleanValue("sgp.anvil.forceDisable", defaultValue = false)

  /**
   * Defines the [AnvilMode] to use with this compilation. See the docs on that class for more
   * details.
   */
  public val anvilMode: AnvilMode
    get() =
      resolver.stringValue("sgp.anvil.mode", defaultValue = AnvilMode.K1_EMBEDDED.name).let {
        AnvilMode.valueOf(it.uppercase(Locale.US))
      }

  /** Defines a required vendor for JDK toolchains. */
  public val jvmVendor: Provider<String>
    get() =
      resolver.optionalStringProvider("sgp.config.jvmVendor").map {
        if (jvmVendorOptOut) {
          sneakyNull()
        } else {
          it
        }
      }

  /** Flag to disable JVM vendor setting locally. */
  public val jvmVendorOptOut: Boolean
    get() = booleanProperty("sgp.config.jvmVendor.optOut", defaultValue = false)

  internal fun requireAndroidSdkProperties(): AndroidSdkProperties {
    val compileSdk = compileSdkVersion ?: error("slack.compileSdkVersion not set")
    val minSdk = minSdkVersion?.toInt() ?: error("slack.minSdkVersion not set")
    val targetSdk = targetSdkVersion?.toInt() ?: error("slack.targetSdkVersion not set")
    return AndroidSdkProperties(compileSdk, minSdk, targetSdk)
  }

  internal data class AndroidSdkProperties(
    val compileSdk: String,
    val minSdk: Int,
    val targetSdk: Int,
  )

  public val compileSdkVersion: String?
    get() = optionalStringProperty("slack.compileSdkVersion")

  public fun latestCompileSdkWithSources(defaultValue: Int): Int =
    intProperty("slack.latestCompileSdkWithSources", defaultValue = defaultValue)

  private val minSdkVersion: String?
    get() = optionalStringProperty("slack.minSdkVersion")

  private val targetSdkVersion: String?
    get() = optionalStringProperty("slack.targetSdkVersion")

  public companion object {
    /**
     * The Slack-specific kotlin.daemon.jvmargs computed by bootstrap.
     *
     * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute
     * other projects.
     */
    public const val KOTLIN_DAEMON_ARGS_KEY: String = "slack.kotlin.daemon.jvmargs"

    /** Minimum xmx value for the Gradle daemon. Value is an integer and unit is gigabytes. */
    // Key-only because it's used in a task init without a project instance
    public const val MIN_GRADLE_XMX: String = "slack.bootstrap.minGradleXmx"

    private const val CACHED_PROVIDER_EXT_NAME = "slack.properties.provider"

    public operator fun invoke(
      project: Project,
      slackTools: Provider<SlackTools>? = project.slackToolsProvider(),
    ): SlackProperties {
      return project.getOrCreateExtra(CACHED_PROVIDER_EXT_NAME) { p ->
        SlackProperties(
          project = p,
          startParameterProperty = { key ->
            slackTools?.flatMap { tools -> tools.globalStartParameterProperty(key) }
              ?: p.provider { null }
          },
          globalLocalProperty = { key ->
            slackTools?.flatMap { tools -> tools.globalLocalProperty(key) } ?: p.provider { null }
          },
        )
      }
    }
  }
}
