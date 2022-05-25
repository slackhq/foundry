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
import org.gradle.api.Project
import slack.gradle.util.booleanProperty
import slack.gradle.util.getOrCreateExtra
import slack.gradle.util.intProperty
import slack.gradle.util.optionalStringProperty
import slack.gradle.util.safeProperty

/**
 * (Mostly Gradle) properties for configuration of SlackPlugin.
 *
 * Order attempted as described by [safeProperty].
 */
public class SlackProperties private constructor(private val project: Project) {

  private fun presenceProperty(key: String): Boolean = optionalStringProperty(key) != null

  private fun fileProperty(key: String): File? = optionalStringProperty(key)?.let(project::file)

  private fun intProperty(key: String, defaultValue: Int = -1): Int =
    project.intProperty(key, defaultValue = defaultValue)

  private fun booleanProperty(key: String, defaultValue: Boolean = false): Boolean =
    project.booleanProperty(key, defaultValue = defaultValue)

  private fun stringProperty(key: String): String =
    optionalStringProperty(key)
      ?: error("No property for $key found and no default value was provided.")

  private fun stringProperty(key: String, defaultValue: String): String =
    optionalStringProperty(key, defaultValue)!!

  private fun optionalStringProperty(key: String, defaultValue: String? = null): String? =
    project.optionalStringProperty(key, defaultValue = defaultValue)

  internal val versions: SlackVersions by lazy {
    project.rootProject.getOrCreateExtra("slack-versions") {
      SlackVersions(project.rootProject.getVersionsCatalog(this))
    }
  }

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

  /** Opt-in flag to enable snapshots repos, used for the dependencies build shadow job. */
  public val enableSnapshots: Boolean
    get() = booleanProperty("slack.gradle.config.enableSnapshots")

  /** Opt-in flag to enable mavenLocal repos, used for local testing. */
  public val enableMavenLocal: Boolean
    get() = booleanProperty("slack.gradle.config.enableMavenLocal")

  /**
   * Flag to indicate that that this project should have no api dependencies, such as if it's solely
   * an annotation processor.
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

  /** Toggle for enabling Jetpack Compose in Android subprojects. */
  public val enableCompose: Boolean
    get() =
      booleanProperty(
        "slack.enableCompose",
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
  public val ndkVersion: String
    get() = stringProperty("slack.ndkVersion")

  /** Flag to enable verbose logging in unit tests. */
  public val testVerboseLogging: Boolean
    get() =
      booleanProperty(
        "slack.test.verboseLogging",
      )

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

  /** Flag to indicate that we're currently running a baseline update. */
  public val lintUpdateBaselines: Boolean
    get() = booleanProperty("slack.lint.update-baselines")

  /** Flag to enable/disable KSP. */
  public val allowKsp: Boolean
    get() = booleanProperty("slack.allow-ksp")

  /** Flag to enable/disable Moshi-IR. */
  public val allowMoshiIr: Boolean
    get() = booleanProperty("slack.allow-moshi-ir")

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
   * Flag to enable the new K2 compiler. Plumbed into the kotlinOptions.useK2 property and here to
   * allow for use from a `local.properties` file.
   */
  public val useK2: Boolean
    get() = booleanProperty("slack.useK2", defaultValue = false)

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

  /**
   * Location for robolectric-core to be referenced by app. Temporary till we have a better solution
   * for "always add these" type of deps.
   *
   * Should be `:path:to:robolectric-core` format
   */
  public val robolectricCoreProject: Project
    get() = project.project(stringProperty("slack.location.robolectric-core"))

  /**
   * Location for slack-platform project to be referenced by projects.
   *
   * Should be `:path:to:slack-platform` format
   */
  public val slackPlatformProject: Project
    get() = project.project(stringProperty("slack.location.slack-platform"))

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

  /* Controls for Java/JVM/JDK versions uses in compilations and execution of tests. */

  /** Flag to enable strict JDK mode, forcing some things like JAVA_HOME. */
  public val strictJdk: Boolean
    get() = booleanProperty("slackToolchainsStrict")

  /** The JDK version to use for compilations. */
  public val jdkVersion: Int
    get() = intProperty("slackToolchainsJdk")

  /** The JDK runtime to target for compilations. */
  public val jvmTarget: Int
    get() = intProperty("slackToolchainsJvmTarget", defaultValue = 8)

  /** Android cache fix plugin. */
  public val enableAndroidCacheFix: Boolean = booleanProperty("slack.plugins.android-cache-fix")

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

  /** Specific toggle for validating the presence of `.kt` files in Kotlin projects. */
  public val strictValidateKtFilePresence: Boolean
    get() = booleanProperty("slack.strict.validateKtFiles", defaultValue = true)

  /** Specified the name of the versions catalog to use for bom management. */
  public val versionCatalogName: String
    get() = stringProperty("slack.catalog", defaultValue = "libs")

  public val compileSdkVersion: String
    get() = stringProperty("slack.compileSdkVersion")
  public val minSdkVersion: String
    get() = stringProperty("slack.minSdkVersion")
  public val targetSdkVersion: String
    get() = stringProperty("slack.targetSdkVersion")

  public companion object {
    /**
     * The Slack-specific kotlin.daemon.jvmargs computed by bootstrap.
     *
     * We don't just blanket use `kotlin.daemon.jvmargs` alone because we don't want to pollute
     * other projects.
     */
    public const val KOTLIN_DAEMON_ARGS_KEY: String = "slack.kotlin.daemon.jvmargs"

    /** Experimental flag to enable logging thermal throttling on macOS devices. */
    // Key-only because it's used in a task init without a project instance
    public const val LOG_THERMALS: String = "slack.log-thermals"

    /** Minimum xmx value for the Gradle daemon. Value is an integer and unit is gigabytes. */
    // Key-only because it's used in a task init without a project instance
    public const val MIN_GRADLE_XMX: String = "slack.bootstrap.minGradleXmx"

    private const val CACHED_PROVIDER_EXT_NAME = "slack.properties.provider"

    public operator fun invoke(project: Project): SlackProperties =
      project.getOrCreateExtra(CACHED_PROVIDER_EXT_NAME, ::SlackProperties)
  }
}
