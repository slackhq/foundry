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
package foundry.gradle

import foundry.common.FoundryKeys
import foundry.gradle.anvil.AnvilMode
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.properties.PropertyResolver
import foundry.gradle.properties.getOrCreateExtra
import foundry.gradle.properties.sneakyNull
import java.io.File
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * (Mostly Gradle) properties for configuration of Foundry Gradle Plugin.
 *
 * Order attempted as described by [PropertyResolver.providerFor].
 */
// TODO allow sourcing from a custom resolver or Properties
public class FoundryProperties
internal constructor(
  private val projectName: String,
  private val resolver: PropertyResolver,
  private val regularFileProvider: (String) -> RegularFile,
  private val rootDirFileProvider: (String) -> RegularFile,
  internal val versions: FoundryVersions,
) {

  private fun presenceProperty(key: String): Boolean = optionalStringProperty(key) != null

  private fun fileProperty(key: String, useRoot: Boolean = false): File? =
    optionalStringProperty(key)
      ?.let(if (useRoot) rootDirFileProvider else regularFileProvider)
      ?.asFile

  private fun fileProvider(key: String, useRoot: Boolean = false): Provider<RegularFile> =
    resolver
      .optionalStringProvider(key)
      .map(if (useRoot) rootDirFileProvider else regularFileProvider)

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

  /** Indicates that this android library project has variants. Flag-only, value is ignored. */
  public val libraryWithVariants: Boolean
    get() = booleanProperty("foundry.android.libraryWithVariants")

  /** Default namespace prefix for android projects if one isn't specified. */
  public val defaultNamespacePrefix: String
    get() = optionalStringProperty("foundry.android.defaultNamespacePrefix") ?: defaultPackagePrefix

  /** Default package prefix for JVM projects if one isn't specified. */
  public val defaultPackagePrefix: String
    get() = stringProperty(FoundryKeys.DEFAULT_PACKAGE_PREFIX)

  /**
   * Indicates that the gradle versions plugin should allow unstable versions. By default, unstable
   * versions are excluded due to the frequent androidx alpha/beta/rc cycle noise. Flag-only, value
   * is ignored.
   */
  public val versionsPluginAllowUnstable: Boolean
    get() = booleanProperty("foundry.versionsPlugin.allowUnstable")

  /** Opt-out flag to skip the androidx dependency check. Should only be used for debugging. */
  public val skipAndroidxCheck: Boolean
    get() = booleanProperty("foundry.android.skipAndroidXCheck")

  /** Version code used for debug APK outputs. */
  public val debugVersionCode: Int
    get() = intProperty("foundry.android.debugVersionCode", 90009999)

  /** User string used for debug APK outputs. */
  public val debugUserString: String
    get() = stringProperty("foundry.android.debugUserString", "debug")

  /** Opt-in flag to enable snapshots repos, used for the dependencies build shadow job. */
  public val enableSnapshots: Boolean
    get() = booleanProperty("foundry.gradle.enableSnapshots")

  /** Opt-in flag to enable mavenLocal repos, used for local testing. */
  public val enableMavenLocal: Boolean
    get() = booleanProperty("foundry.gradle.enableMavenLocal")

  /**
   * Flag to indicate that this project should have no api dependencies, such as if it's solely an
   * annotation processor.
   */
  public val rakeNoApi: Boolean
    get() = booleanProperty("foundry.rake.noapi")

  /**
   * Flag to enable the Gradle Dependency Analysis Plugin, which is disabled by default due to
   * https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/204
   */
  public val enableAnalysisPlugin: Boolean
    get() = booleanProperty("foundry.enableAnalysisPlugin")

  /**
   * Flag to indicate this project should be exempted from platforms, usually platform projects
   * themselves.
   */
  public val noPlatform: Boolean
    get() = booleanProperty("foundry.config.noPlatform")

  /** Property corresponding to the supported languages in GA builds */
  public val supportedLanguages: String
    get() = stringProperty("foundry.android.supportedLanguages")

  /** Property corresponding to the supported languages in Internal builds */
  public val supportedLanguagesInternal: String
    get() = stringProperty("foundry.android.supportedLanguagesInternal")

  /** Property corresponding to the supported languages in Beta builds */
  public val supportedLanguagesBeta: String
    get() = stringProperty("foundry.android.supportedLanguagesBeta")

  /**
   * Property corresponding to the file path of a custom versions.json file for use with
   * dependencies shadow jobs.
   */
  public val versionsJson: File?
    get() = fileProperty("foundry.versionsJson", useRoot = true)

  /**
   * An alias name to a libs.versions.toml bundle for common Android Compose dependencies that
   * should be added to android projects with compose enabled
   */
  public val defaultComposeAndroidBundleAlias: String?
    get() = optionalStringProperty("foundry.compose.android.defaultBundleAlias")

  /**
   * Enables live literals. Note that they are disabled by default due to
   * https://issuetracker.google.com/issues/274207650 and
   * https://issuetracker.google.com/issues/274231394.
   */
  public val composeEnableLiveLiterals: Boolean
    get() = booleanProperty("foundry.compose.android.enableLiveLiterals", false)

  /**
   * Common compose compiler options.
   *
   * Format is a comma-separated list of key-value pairs, e.g. "key1=value1,key2=value2". Keys
   * should be the simple name of the compose compiler option, no prefixes needed.
   */
  public val composeCommonCompilerOptions: Provider<List<String>>
    get() =
      resolver
        .providerFor("foundry.compose.commonCompilerOptions")
        .map { value -> value.split(",").map { it.trim() } }
        .orElse(emptyList())

  /** Relative path to a Compose stability configuration file from the _root_ project. */
  public val composeStabilityConfigurationPath: Provider<RegularFile>
    get() =
      resolver.providerFor("foundry.compose.stabilityConfigurationPath").map(rootDirFileProvider)

  /**
   * Use a workaround for compose-compiler's `includeInformation` option on android projects.
   *
   * On android projects, the compose compiler gradle plugin annoyingly no-ops
   *
   * @see <a href="https://issuetracker.google.com/issues/362780328#comment4">Upstream issue</a>
   */
  public val composeUseIncludeInformationWorkaround: Boolean
    get() =
      resolver.booleanValue("foundry.compose.useIncludeInformationWorkaround", defaultValue = true)

  /**
   * By default, Compose on android only enables source information in debug variants. This is a bit
   * silly in large projects because we generally make all libraries single-variant as "release",
   * and can result in libraries not having source information. Instead, we rely on R8 to strip out
   * this information in release builds as needed.
   *
   * @see <a href="https://issuetracker.google.com/issues/362780328">Upstream issue</a>
   */
  public val composeIncludeSourceInformationEverywhereByDefault: Boolean
    get() =
      resolver.booleanValue(
        "foundry.compose.includeSourceInformationEverywhereByDefault",
        defaultValue = true,
      )

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
    get() = presenceProperty("foundry.android.usePrototypeAppId")

  /**
   * Property corresponding to the SDK versions we test in Robolectric tests. Its value should be a
   * comma-separated list of SDK ints to download.
   */
  public val robolectricTestSdks: List<Int>
    get() =
      stringProperty("foundry.android.robolectric.testSdks")
        .splitToSequence(",")
        .map { it.toInt() }
        .toList()

  /** Opt out for -Werror. */
  public val allowWarnings: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.kotlin.allowWarnings", defaultValue = false)

  /** Opt out for -Werror in tests. */
  public val allowWarningsInTests: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.kotlin.allowWarningsInTests", defaultValue = false)

  /**
   * Anvil generator projects that should always be included when Anvil is enabled.
   *
   * This should be semicolon-delimited Gradle project paths.
   */
  public val anvilGeneratorProjects: String?
    get() = optionalStringProperty("foundry.anvil.generatorProjects")

  /**
   * Anvil runtime projects that should always be included when Anvil is enabled.
   *
   * This should be semicolon-delimited Gradle project paths.
   */
  public val anvilRuntimeProjects: String?
    get() = optionalStringProperty("foundry.anvil.runtimeProjects")

  /** Flag to enable use of the Anvil KSP fork. https://github.com/ZacSweers/anvil */
  public val anvilUseKspFork: Boolean
    get() = booleanProperty("foundry.anvil.useKspFork", defaultValue = false)

  /** Log Foundry extension configuration state verbosely. */
  public val foundryExtensionVerbose: Boolean
    get() = booleanProperty("foundry.extension.verbose")

  /**
   * Flag for Error-Prone auto-patching. Enable when running an auto-patch of EP, such as when it's
   * being introduced to a new module or upgrading EP itself.
   */
  public val errorProneAutoPatch: Boolean
    get() = booleanProperty("foundry.errorprone.autoPatch")

  /**
   * Error-Prone checks that should be considered errors.
   *
   * This should be colon-delimited string.
   *
   * Example: "AnnotationMirrorToString:AutoValueSubclassLeaked"
   */
  public val errorProneCheckNamesAsErrors: String?
    get() = optionalStringProperty("foundry.errorprone.checkNamesAsErrors")

  /**
   * Flag for Nullaway baselining. When enabled along with [errorProneAutoPatch], existing
   * nullability issues will be baselined with a `castToNonNull` call to wrap it.
   */
  public val nullawayBaseline: Boolean
    get() = booleanProperty("foundry.errorprone.nullaway.baseline")

  /**
   * Ndk version to use for android projects.
   *
   * Latest versions can be found at https://developer.android.com/ndk/downloads
   */
  public val ndkVersion: String?
    get() = optionalStringProperty("foundry.android.ndkVersion")

  /**
   * Enables verbose logging in miscellaneous places of SGP. This is intended to be a less noisy
   * alternative to running gradle with `--info` or `--debug`.
   */
  public val verboseLogging: Boolean
    get() = resolver.booleanValue("foundry.logging.verbose")

  /** Flag to enable verbose logging in unit tests. */
  public val testVerboseLogging: Boolean
    get() = booleanProperty("foundry.test.verboseLogging") || verboseLogging

  /**
   * Flag to enable kapt in tests. By default these are disabled due to this undesirable (but
   * surprisingly intented) behavior of running kapt + stub generation even if no processors are
   * present.
   *
   * See https://youtrack.jetbrains.com/issue/KT-29481#focus=Comments-27-4651462.0-0
   */
  public val enableKaptInTests: Boolean
    get() = booleanProperty("foundry.kapt.enabled-in-tests")

  /** Flag to enable errors only in lint checks. */
  public val lintErrorsOnly: Boolean
    get() = booleanProperty("foundry.lint.errors-only")

  /** File name to use for a project's lint baseline. */
  public val lintBaselineFileName: String?
    get() = optionalStringProperty("foundry.lint.baseline-file-name", blankIsNull = true)

  /** Flag to control whether or not lint checks test sources. */
  public val lintCheckTestSources: Boolean
    get() = booleanProperty("foundry.lint.checkTestSources", true)

  /** Flag to control whether or not lint checks ignores test sources. */
  public val lintIgnoreTestSources: Boolean
    get() = booleanProperty("foundry.lint.ignoreTestSources", false)

  /**
   * Flag to control which agp version should be used for lint. Optional. Value should be a version
   * key in `libs.versions.toml`,
   */
  public val lintVersionOverride: String?
    get() = optionalStringProperty("foundry.lint.agpVersion")

  /**
   * Flag to indicate whether this project is a test library (such as test utils, test fixtures,
   * etc).
   */
  public val isTestLibrary: Boolean
    get() = booleanProperty("foundry.isTestLibrary", false) || projectName == "test-fixtures"

  /**
   * At the time of writing, AGP does not support running lint on `com.android.test` projects. This
   * is a flag to eventually support this in the future.
   *
   * https://issuetracker.google.com/issues/208765813
   */
  public val enableLintInAndroidTestProjects: Boolean
    get() = booleanProperty("foundry.lint.enableOnAndroidTestProjects", false)

  /** If enabled, enables emulator.wtf for androidTest() uses. */
  public val enableEmulatorWtfForAndroidTest: Boolean
    get() = booleanProperty("foundry.emulatorwtf.enable", false)

  /**
   * If enabled, enables per-test videos for emulator.wtf.
   *
   * https://github.com/emulator-wtf/test-runtime-android
   */
  public val enableEmulatorWtfPerTestVideo: Boolean
    get() =
      enableEmulatorWtfForAndroidTest &&
        booleanProperty("foundry.emulatorwtf.enablePerTestVideo", false)

  /** Flag to enable/disable KSP. */
  public val enableKsp: Boolean
    get() = booleanProperty("foundry.ksp.enable")

  /** Flag to enable/disable Moshi-IR. */
  public val enableMoshiIr: Boolean
    get() = booleanProperty("foundry.moshi.ir.enable")

  /** Flag to enable/disable moshi proguard rule gen. */
  public val moshixGenerateProguardRules: Boolean
    get() = booleanProperty("moshix.generateProguardRules", defaultValue = true)

  /** Flag to connect SqlDelight sources to KSP. */
  public val kspConnectSqlDelight: Boolean
    get() = booleanProperty("foundry.ksp.connect.sqldelight")

  /** Flag to connect ViewBinding sources to KSP. */
  public val kspConnectViewBinding: Boolean
    get() = booleanProperty("foundry.ksp.connect.viewbinding")

  /** Variants that should be disabled in a given subproject. */
  public val disabledVariants: String?
    get() = optionalStringProperty("foundry.android.disabledVariants")

  /**
   * The project-specific kotlin.daemon.jvmargs computed by bootstrap.
   *
   * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute other
   * projects.
   */
  public val kotlinDaemonArgs: List<String>?
    get() =
      optionalStringProperty(
          KOTLIN_DAEMON_ARGS_KEY,
          defaultValue = optionalStringProperty(KOTLIN_DAEMON_ARGS_KEY_OLD, defaultValue = null),
        )
        ?.split(" ")
        ?.map(String::trim)
        ?.filterNot(String::isBlank)
        ?.takeUnless(List<*>::isEmpty)

  /**
   * Flag to enable ciUnitTest on this project. Default is true.
   *
   * When enabled, a task named "ciUnitTest" will be created in this project, which will depend on
   * the unit test task for a single build variant (e.g. "testReleaseUnitTest").
   */
  public val ciUnitTestEnabled: Boolean
    get() = booleanProperty("foundry.ci-unit-test.enable", defaultValue = true)

  /** CI unit test variant (Android only). Defaults to `release`. */
  public val ciUnitTestVariant: String
    get() = stringProperty("foundry.ci-unit-test.variant", "release")

  /** If enabled, applies the kotlinx-kover plugin to projects using ciUnitTest. */
  public val ciUnitTestEnableKover: Boolean
    get() = booleanProperty("foundry.ci-unit-test.enableKover", false)

  /**
   * Parallelism multiplier to use for unit tests. This should be a float value that is multiplied
   * by the number of cores. The value can be a fraction. Default is 0.5.
   */
  public val unitTestParallelismMultiplier: Float
    get() {
      val rawValue = stringProperty("foundry.unit-test.parallelismMultiplier", "0.5")
      val floatValue = rawValue.toFloatOrNull()
      require(floatValue != null && floatValue > 0) {
        "Invalid value for foundry.unit-test.parallelismMultiplier: '$rawValue'"
      }
      return floatValue
    }

  /** Controls how often to fork the JVM in unit tests. Default is 1000. */
  public val unitTestForkEvery: Long
    get() = intProperty("foundry.unit-test.forkEvery", 1000).toLong()

  /**
   * Flag to enable ciLint on a project. Default is true.
   *
   * When enabled, a task named "ciLint" will be created in this project, which will depend on the
   * all the lint tasks in the project.
   */
  public val ciLintEnabled: Boolean
    get() = booleanProperty("foundry.ci-lint.enable", defaultValue = true)

  /**
   * Comma-separated list of CI lint variants to run (Android only). Default when unspecified will
   * lint all variants.
   */
  public val ciLintVariants: String?
    get() = optionalStringProperty("foundry.ci-lint.variants")

  /** Flag for enabling test orchestrator. */
  public val useOrchestrator: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.android.test.orchestrator", false)

  /**
   * Location for robolectric-core to be referenced by app. Temporary till we have a better solution
   * for "always add these" type of deps.
   *
   * Should be `:path:to:robolectric-core` format
   */
  public val robolectricCoreProject: String?
    get() = optionalStringProperty("foundry.location.robolectric-core")

  /**
   * Gradle path to a platform project to be referenced by other projects.
   *
   * Should be `:path:to:foundry-platform` format
   *
   * @see Platforms
   */
  public val platformProjectPath: String?
    get() = optionalStringProperty("foundry.location.foundry-platform")

  /**
   * Optional file location for an `affected_projects.txt` file that contains a list of projects
   * affected in this build.
   */
  public val affectedProjects: File?
    get() = fileProperty("foundry.avoidance.affectedProjectsFile", useRoot = true)

  /* Controls for Java/JVM/JDK versions uses in compilations and execution of tests. */

  /** Flag to enable strict JDK mode, forcing some things like JAVA_HOME. */
  public val strictJdk: Boolean
    get() = booleanProperty("foundryToolchainsStrict", defaultValue = true)

  /** Android cache fix plugin. */
  public val enableAndroidCacheFix: Boolean = booleanProperty("foundry.plugins.android-cache-fix")

  /**
   * Optional override for buildToolsVersion in Android projects. Sometimes temporarily necessary to
   * pick up new fixes.
   */
  public val buildToolsVersionOverride: String? =
    optionalStringProperty("foundry.android.buildToolsVersionOverride")

  /**
   * Performance optimization to relocate the entire project build directory to a location outside
   * the IDE's view. This prevents the IDE from tracking these files and improves IDE performance.
   */
  public val relocateBuildDir: Boolean = betaFeature("foundry.perf.relocateBuildDir")

  /** Opt-in for beta SGP features. */
  public val enableBetaFeatures: Boolean = booleanProperty("foundry.beta", defaultValue = false)

  /**
   * Shorthand helper for checking features that are in beta or falling back to their specific flag.
   */
  private fun betaFeature(key: String): Boolean {
    return enableBetaFeatures || booleanProperty(key, defaultValue = false)
  }

  /* Controls for auto-applied plugins. */
  public val autoApplyTestRetry: Boolean
    get() = booleanProperty("foundry.auto-apply.test-retry", defaultValue = true)

  public val autoApplySpotless: Boolean
    get() = booleanProperty("foundry.auto-apply.spotless", defaultValue = true)

  public val autoApplyDetekt: Boolean
    get() = booleanProperty("foundry.auto-apply.detekt", defaultValue = true)

  public val autoApplyNullaway: Boolean
    get() = booleanProperty("foundry.auto-apply.nullaway", defaultValue = true)

  public val autoApplyCacheFix: Boolean
    get() = booleanProperty("foundry.auto-apply.cache-fix", defaultValue = true)

  public val autoApplySortDependencies: Boolean
    get() = booleanProperty("foundry.auto-apply.sort-dependencies", defaultValue = true)

  /* Test retry controls. */
  public enum class TestRetryPluginType {
    RETRY_PLUGIN,
    GE,
  }

  public val testRetryPluginType: TestRetryPluginType
    get() =
      stringProperty("foundry.test.retry.pluginType", TestRetryPluginType.RETRY_PLUGIN.name)
        .let(TestRetryPluginType::valueOf)

  public val testRetryFailOnPassedAfterRetry: Provider<Boolean>
    get() =
      resolver.booleanProvider("foundry.test.retry.failOnPassedAfterRetry", defaultValue = false)

  public val testRetryMaxFailures: Provider<Int>
    get() = resolver.intProvider("foundry.test.retry.maxFailures", defaultValue = 20)

  public val testRetryMaxRetries: Provider<Int>
    get() = resolver.intProvider("foundry.test.retry.maxRetries", defaultValue = 1)

  /* Detekt configs. */
  /** Detekt config files, evaluated from rootProject.file(...). */
  public val detektConfigs: List<String>?
    get() = optionalStringProperty("foundry.detekt.configs")?.split(",")

  /** Detekt baseline file, evaluated from project.layout.projectDirectory.file(...). */
  public val detektBaselineFileName: String?
    get() = optionalStringProperty("foundry.detekt.baseline-file-name", blankIsNull = true)

  /** Enables full detekt mode (with type resolution). Off by default due to performance issues. */
  public val enableFullDetekt: Boolean
    get() = booleanProperty("foundry.detekt.full")

  /** Comma-separated set of projects to ignore in sorting dependencies. */
  public val sortDependenciesIgnore: String?
    get() = optionalStringProperty("foundry.sortDependencies.ignore")

  /** Enables verbose debug logging across the plugin. */
  public val debug: Boolean
    get() = booleanProperty("foundry.debug", defaultValue = false)

  /** A comma-separated list of configurations to use in affected project detection. */
  public val affectedProjectConfigurations: String?
    get() = optionalStringProperty("foundry.avoidance.affected-project-configurations")

  /**
   * Flag to, when true, makes [affectedProjectConfigurations] build upon the defaults rather than
   * replace them.
   */
  public val buildUponDefaultAffectedProjectConfigurations: Boolean
    get() = booleanProperty("foundry.avoidance.build-upon-default-affected-project-configurations")

  /**
   * Global control for enabling stricter validation of projects, such as ensuring Kotlin projects
   * have at least one `.kt` source file.
   *
   * Note that these are expected to be slow and not used anywhere outside of debugging or CI.
   *
   * Granular controls should depend on this check + include their own opt-out check as-needed.
   */
  public val strictMode: Boolean
    get() = booleanProperty("foundry.strict", defaultValue = false)

  /** Specific toggle for validating manifests in androidTest sources. */
  public val strictValidateAndroidTestManifest: Boolean
    get() =
      booleanProperty("foundry.android.strict.validateAndroidTestManifests", defaultValue = true)

  /**
   * Always enables resources in android unit tests. Only present for benchmarking purposes and
   * should otherwise be off.
   */
  public val alwaysEnableResourcesInTests: Boolean
    get() = booleanProperty("foundry.android.test.alwaysEnableResources", defaultValue = false)

  /** Global toggle to enable bugsnag. Note this still respects variant filters. */
  public val bugsnagEnabled: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.android.bugsnag.enabled")

  /** Branch pattern for git branches Bugsnag should be enabled on. */
  public val bugsnagEnabledBranchPattern: Provider<String>
    get() = resolver.optionalStringProvider("foundry.android.bugsnag.enabledBranchPattern")

  /** Global boolean that controls whether mod score is enabled on this project. */
  public val modScoreGlobalEnabled: Boolean
    get() = resolver.booleanValue("foundry.modscore.enabled")

  /**
   * Per-project boolean that allows for excluding this project from mod score.
   *
   * Note this should only be applied to projects that cannot be depended on.
   */
  public val modScoreIgnore: Boolean
    get() = resolver.booleanValue("foundry.modscore.ignore")

  /** Experimental flag to enable logging thermal throttling on macOS devices. */
  public val logThermals: Boolean
    get() = resolver.booleanValue("foundry.logging.thermals", defaultValue = false)

  /**
   * Enables eager configuration of [FoundryArtifact] publishing in subprojects. This is behind a
   * flag as a failsafe while we try different approaches to allow lenient resolution.
   *
   * @see StandardProjectConfigurations.setUpSubprojectArtifactPublishing
   */
  public val eagerlyConfigureArtifactPublishing: Boolean
    get() = resolver.booleanValue("foundry.artifacts.configure-eagerly", defaultValue = false)

  /**
   * Force-disables Anvil regardless of `FoundryExtension.dagger()` settings, useful for K2 testing
   * where Anvil is unsupported.
   */
  public val disableAnvilForK2Testing: Boolean
    get() = resolver.booleanValue("foundry.anvil.forceDisable", defaultValue = false)

  /**
   * Defines the [AnvilMode] to use with this compilation. See the docs on that class for more
   * details.
   */
  public val anvilMode: AnvilMode
    get() =
      resolver.stringValue("foundry.anvil.mode", defaultValue = AnvilMode.K1_EMBEDDED.name).let {
        AnvilMode.valueOf(it.uppercase(Locale.US))
      }

  /** Overrides the kotlin language version if present. */
  public val kaptLanguageVersion: Provider<KotlinVersion>
    get() =
      resolver.optionalStringProvider("foundry.kapt.languageVersion").map {
        KotlinVersion.fromVersion(it)
      }

  /** Defines a required vendor for JDK toolchains. */
  public val jvmVendor: Provider<String>
    get() =
      resolver.optionalStringProvider("foundry.jvm.vendor").map {
        if (jvmVendorOptOut) {
          sneakyNull()
        } else {
          it
        }
      }

  /** Flag to disable JVM vendor setting locally. */
  public val jvmVendorOptOut: Boolean
    get() = booleanProperty("foundry.jvm.vendor.optOut", defaultValue = false)

  /** Optional link to JDK configuration */
  public val jdkDocsLink: String?
    get() = optionalStringProperty("foundry.jdk.docsLink")

  /** Optional error message to show when the JDK configuration is invalid. */
  public val jdkErrorMessage: String?
    get() = optionalStringProperty("foundry.jdk.errorMessage")

  /**
   * Option to force a specific kotlin language version. By default defers to the KGP default the
   * build is running with.
   */
  public val kotlinLanguageVersionOverride: Provider<String>
    get() = resolver.optionalStringProvider("foundry.kotlin.languageVersionOverride")

  /**
   * Free compiler arguments to pass to Kotlin's `freeCompilerArgs` property in all compilations.
   * Should not include opt-in arguments or `-progressive`.
   */
  public val kotlinFreeArgs: Provider<List<String>>
    get() =
      resolver
        .optionalStringProvider("foundry.kotlin.freeArgs")
        .map { it.split(',') }
        // Super important to default if absent due to
        // https://docs.gradle.org/8.7/release-notes.html#build-authoring-improvements
        .orElse(emptyList())

  /**
   * Free compiler arguments to pass to Kotlin's `freeCompilerArgs` property in JVM compilations.
   * Should not include opt-in arguments or `-progressive`.
   */
  public val kotlinJvmFreeArgs: Provider<List<String>>
    get() =
      resolver
        .optionalStringProvider("foundry.kotlin.jvmFreeArgs")
        .map { it.split(',') }
        // Super important to default if absent due to
        // https://docs.gradle.org/8.7/release-notes.html#build-authoring-improvements
        .orElse(emptyList())

  /** Opt-in annotations to pass to Kotlin's `optIn` property. */
  public val kotlinOptIn: Provider<List<String>>
    get() =
      resolver
        .optionalStringProvider("foundry.kotlin.optIns")
        .map { it.split(',') }
        // Super important to default if absent due to
        // https://docs.gradle.org/8.7/release-notes.html#build-authoring-improvements
        .orElse(emptyList())

  /** Default for Kotlin's `progressive` mode. Defaults to enabled. */
  public val kotlinProgressive: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.kotlin.progressive", defaultValue = true)

  /** Property to enable auto-fixing in topography validation. */
  public val topographyAutoFix: Provider<Boolean>
    get() = resolver.booleanProvider("foundry.topography.validation.autoFix", defaultValue = false)

  /**
   * Property pointing at a features config JSON file for
   * [foundry.gradle.topography.ModuleFeaturesConfig].
   */
  public val topographyFeaturesConfig: Provider<RegularFile>
    get() = fileProvider("foundry.topography.features.config", useRoot = true)

  internal fun requireAndroidSdkProperties(): AndroidSdkProperties {
    val compileSdk = compileSdkVersion ?: error("foundry.android.compileSdkVersion not set")
    val minSdk = minSdkVersion?.toInt() ?: error("foundry.android.minSdkVersion not set")
    val targetSdk = targetSdkVersion?.toInt() ?: error("foundry.android.targetSdkVersion not set")
    return AndroidSdkProperties(compileSdk, minSdk, targetSdk)
  }

  internal data class AndroidSdkProperties(
    val compileSdk: String,
    val minSdk: Int,
    val targetSdk: Int,
  )

  public val compileSdkVersion: String?
    get() = optionalStringProperty("foundry.android.compileSdkVersion")

  public fun latestCompileSdkWithSources(defaultValue: Int): Int =
    intProperty("foundry.android.latestCompileSdkWithSources", defaultValue = defaultValue)

  private val minSdkVersion: String?
    get() = optionalStringProperty("foundry.android.minSdkVersion")

  private val targetSdkVersion: String?
    get() = optionalStringProperty("foundry.android.targetSdkVersion")

  public companion object {
    /**
     * The project-specific kotlin.daemon.jvmargs computed by bootstrap.
     *
     * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute
     * other projects.
     */
    public const val KOTLIN_DAEMON_ARGS_KEY: String = "foundry.kotlin.daemon.jvmargs"
    public const val KOTLIN_DAEMON_ARGS_KEY_OLD: String = "slack.kotlin.daemon.jvmargs"

    /** Minimum xmx value for the Gradle daemon. Value is an integer and unit is gigabytes. */
    // Key-only because it's used in a task init without a project instance
    public const val MIN_GRADLE_XMX: String = "foundry.bootstrap.minGradleXmx"

    /** Minimum xms value for the Gradle daemon. Value is an integer and unit is gigabytes. */
    // Key-only because it's used in a task init without a project instance
    public const val MIN_GRADLE_XMS: String = "foundry.bootstrap.minGradleXms"

    internal const val CACHED_PROVIDER_EXT_NAME = "foundry.properties.provider"

    public operator fun invoke(project: Project): FoundryProperties = project.foundryProperties

    public fun getOrCreateRoot(
      project: Project,
      startParameterProperty: (String) -> Provider<String>,
      globalLocalProperty: (String) -> Provider<String>,
    ): FoundryProperties {
      check(project.isRootProject) { "getOrCreate can only run in the root project!" }
      return project.getOrCreateExtra(CACHED_PROVIDER_EXT_NAME) { p ->
        val resolver =
          PropertyResolver.createForRootProject(
            p,
            startParameterProperty = startParameterProperty,
            globalLocalProperty = globalLocalProperty,
          )
        val versions = FoundryVersions(p.getVersionsCatalog())
        create(p, resolver, versions)
      }
    }

    public fun getOrCreate(
      project: Project,
      foundryTools: Provider<FoundryTools> = project.foundryToolsProvider(),
    ): FoundryProperties {
      return project.getOrCreateExtra(CACHED_PROVIDER_EXT_NAME) { p ->
        val globalProperties = foundryTools.get().globalConfig.globalFoundryProperties
        val resolver = PropertyResolver(project, globalResolver = globalProperties.resolver)
        val versions = globalProperties.versions
        create(p, resolver, versions)
      }
    }

    private fun create(
      project: Project,
      resolver: PropertyResolver,
      versions: FoundryVersions,
    ): FoundryProperties {
      return FoundryProperties(
        projectName = project.name,
        resolver = resolver,
        regularFileProvider = project.layout.projectDirectory::file,
        rootDirFileProvider = project.rootProject.layout.projectDirectory::file,
        versions = versions,
      )
    }
  }
}

public val Project.foundryProperties: FoundryProperties
  get() = FoundryProperties.getOrCreate(project)
