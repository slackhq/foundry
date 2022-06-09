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
@file:Suppress("UnstableApiUsage")

package slack.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.BuildType
import com.autonomousapps.DependencyAnalysisSubExtension
import com.google.common.base.CaseFormat
import com.google.devtools.ksp.gradle.KspExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import slack.dependencyrake.RakeDependencies
import slack.gradle.AptOptionsConfig.AptOptionsConfigurer
import slack.gradle.AptOptionsConfigs.invoke
import slack.gradle.dependencies.KotlinBuildConfig
import slack.gradle.dependencies.SlackDependencies
import slack.gradle.permissionchecks.PermissionChecks
import slack.gradle.tasks.AndroidTestApksTask
import slack.gradle.tasks.CheckManifestPermissionsTask
import slack.gradle.util.booleanProperty

private const val LOG = "SlackPlugin:"

private fun Logger.logWithTag(message: String) {
  debug("$LOG $message")
}

/**
 * Standard [Project] configurations. This class will be iterated on over time as we grow out our
 * bootstrapping options for Gradle subprojects.
 *
 * Principles:
 * - Avoid duplicating work and allocations. This runs at configuration time and should be as low
 * overhead as possible.
 * - Do not resolve dependencies at configuration-time. Use appropriate callback APIs!
 * - Support Kotlin, Android, and Java projects.
 * - One-off configuration should be left to individual projects to declare.
 * - Use debug logging.
 */
@Suppress("TooManyFunctions")
internal class StandardProjectConfigurations {

  private val kotlinCompilerArgs =
    mutableListOf<String>()
      .apply {
        addAll(KotlinBuildConfig.kotlinCompilerArgs)
        // Left as a toe-hold for any future dynamic arguments
      }
      .distinct()

  fun applyTo(project: Project) {
    val slackProperties = SlackProperties(project)
    val globalConfig = project.slackTools().globalConfig
    project.applyJvmConfigurations(globalConfig, slackProperties)
  }

  @Suppress("unused")
  private fun Project.javaCompilerFor(version: Int): Provider<JavaCompiler> {
    return extensions.getByType<JavaToolchainService>().compilerFor {
      languageVersion.set(JavaLanguageVersion.of(version))
    }
  }

  private fun Project.applyJvmConfigurations(
    globalConfig: GlobalConfig,
    slackProperties: SlackProperties
  ) {
    val platformProjectPath = slackProperties.platformProjectPath
    if (platformProjectPath == null) {
      if (slackProperties.strictMode) {
        logger.warn(
          "slack.location.slack-platform is not set. Consider creating one to ensure consistent dependency versions across projects!"
        )
      }
    } else if (!slackProperties.noPlatform && path != platformProjectPath) {
      applyPlatforms(slackProperties.versions.boms, platformProjectPath)
    }

    if (slackProperties.enableAnalysisPlugin) {
      val buildFile = project.buildFile
      // This can run on some intermediate middle directories, like `carbonite` in
      // `carbonite:carbonite`
      if (buildFile.exists()) {
        // Configure rake
        plugins.withId("com.autonomousapps.dependency-analysis") {
          val isNoApi = slackProperties.rakeNoApi
          val rakeDependencies =
            tasks.register<RakeDependencies>("rakeDependencies") {
              buildFileProperty.set(project.buildFile)
              noApi.set(isNoApi)
              identifierMap.set(
                project.provider {
                  project.getVersionsCatalog(slackProperties).identifierMap().mapValues { (_, v) ->
                    "libs.$v"
                  }
                }
              )
            }
          configure<DependencyAnalysisSubExtension> { registerPostProcessingTask(rakeDependencies) }
        }
      }
    }

    val slackExtension = extensions.create<SlackExtension>("slack")
    checkAndroidXDependencies(slackProperties)
    configureAnnotationProcessors()
    val jdkVersion = jdkVersion()
    val jvmTargetVersion = jvmTargetVersion()

    pluginManager.onFirst(JVM_PLUGINS) {
      slackProperties.versions.bundles.commonAnnotations.ifPresent {
        dependencies.add("implementation", it)
      }

      slackProperties.versions.bundles.commonTest.ifPresent {
        dependencies.add("testImplementation", it)
      }
    }

    // TODO always configure compileOptions here
    configureAndroidProjects(globalConfig, slackExtension, jvmTargetVersion, slackProperties)

    configureKotlinProjects(globalConfig, jdkVersion, jvmTargetVersion, slackProperties)
    configureJavaProject(jdkVersion, jvmTargetVersion, slackProperties)
    slackExtension.configureFeatures(this, slackProperties)

    // TODO would be nice if we could apply this _only_ if compile-testing is on the test classpath
    tasks.withType<Test>().configureEach {
      // Required for Google compile-testing to work.
      // https://github.com/google/compile-testing/issues/222
      jvmArgs("--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
    }
  }

  /**
   * Applies platform()/bom dependencies for projects, right now only on known
   * [Configurations.Groups.PLATFORM].
   */
  private fun Project.applyPlatforms(
    boms: Set<Provider<MinimalExternalModuleDependency>>,
    platformProject: String
  ) {
    configurations.configureEach {
      if (isPlatformConfigurationName(name)) {
        dependencies {
          for (bom in boms) {
            add(name, platform(bom))
          }
          add(name, platform(project(platformProject)))
        }
      }
    }
  }

  /**
   * We reject new `com.android.support` dependencies to eliminate the dependence on Jetifier. Note
   * we apply this on all projects (not just android projects) because some android dependencies are
   * plain jars that can be used in standard JVM projects.
   */
  private fun Project.checkAndroidXDependencies(slackProperties: SlackProperties) {
    if (!slackProperties.skipAndroidxCheck) {
      configurations.configureEach {
        resolutionStrategy {
          eachDependency {
            if (requested.group == "com.android.support") {
              throw IllegalArgumentException(
                "Legacy support library dependencies are no longer " +
                  "supported. To trace this dependency, run './gradlew " +
                  "checkJetifier -Pandroid.enableJetifier=true --no-configuration-cache"
              )
            }
          }
        }
      }
    }
  }

  /** Adds common configuration for Java projects. */
  private fun Project.configureJavaProject(
    jdkVersion: Int,
    jvmTargetVersion: Int,
    slackProperties: SlackProperties
  ) {
    plugins.withType<JavaBasePlugin>().configureEach {
      extensions.configure<JavaPluginExtension> {
        val version = JavaVersion.toVersion(jvmTargetVersion)
        sourceCompatibility = version
        targetCompatibility = version
      }
      if (jdkVersion >= 9) {
        tasks.configureEach<JavaCompile> {
          if (!isAndroid) {
            logger.logWithTag("Configuring release option for $path")
            options.release.set(jvmTargetVersion)
          }
        }
      }
    }

    val javaToolchains by lazy { project.serviceOf<JavaToolchainService>() }

    tasks.withType<JavaCompile>().configureEach {
      // Keep parameter names, this is useful for annotation processors and static analysis tools
      options.compilerArgs.addAll(listOf("-parameters"))

      // Android is our lowest JVM target, so if we're an android project we'll always use that
      // source target.
      // TODO is this late enough to be safe?
      // TODO if we set it in android, does the config from this get safely ignored?
      // TODO re-enable in android at all after AGP 7.1
      if (!isAndroid) {
        val target = if (isAndroid) jvmTargetVersion else jdkVersion
        logger.logWithTag("Configuring toolchain for $path to $jdkVersion")
        javaCompiler.set(
          javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(target)) }
        )
      }
    }

    configureErrorProne(slackProperties)
  }

  /**
   * Adds common configuration for error prone on Java projects. Note that this still uses
   * application of the error prone plugin as an opt-in marker for now, and is not applied to every
   * project.
   */
  private fun Project.configureErrorProne(slackProperties: SlackProperties) {
    val autoPatchEnabled = slackProperties.errorProneAutoPatch
    pluginManager.withPlugin("net.ltgt.nullaway") {
      val nullawayBaseline = slackProperties.nullawayBaseline

      val nullawayDep =
        slackProperties.versions.catalog.findLibrary("errorProne-nullaway").orElseThrow {
          IllegalStateException("Could not find errorProne-nullaway in the catalog")
        }
      dependencies { add("errorprone", nullawayDep) }

      tasks.withType<JavaCompile>().configureEach {
        val nullAwaySeverity =
          if (name.contains("test", ignoreCase = true)) {
            CheckSeverity.OFF
          } else {
            CheckSeverity.ERROR
          }
        options.errorprone.nullaway {
          severity.set(nullAwaySeverity)
          annotatedPackages.add("slack")
          checkOptionalEmptiness.set(true)
          if (autoPatchEnabled && nullawayBaseline) {
            suggestSuppressions.set(true)
            autoFixSuppressionComment.set("Nullability issue auto-patched by NullAway.")
            castToNonNullMethod.set("slack.commons.JavaPreconditions.castToNotNull")
          }
        }
      }
    }
    pluginManager.withPlugin("net.ltgt.errorprone") {
      dependencies.add("errorprone", SlackDependencies.ErrorProne.core)

      val isAndroidProject = isAndroid

      tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
          disableWarningsInGeneratedCode.set(true)
          excludedPaths.set(".*/build/generated/.*") // The EP flag alone isn't enough
          // https://github.com/google/error-prone/issues/2092
          disable("HidingField")
          error(*slackTools().globalConfig.errorProneCheckNamesAsErrors.toTypedArray())

          if (isAndroidProject) {
            options.compilerArgs.add("-XDandroidCompatible=true")
          }

          // Enable autopatching via "-PepAutoPatch=true". This patches in-place and requires a
          // recompilation after.
          // This could be useful to enable on CI + a git porcelain check to see if there's any
          // patchable error prone
          // fixes.
          if (autoPatchEnabled) {
            // Always log this verbosely
            logger.lifecycle("Enabling error-prone auto-patching on ${project.path}:$name")
            errorproneArgs.addAll(
              "-XepPatchChecks:${ERROR_PRONE_CHECKS.joinToString(",")}",
              "-XepPatchLocation:IN_PLACE"
            )
          }
        }
      }
    }
  }

  @Suppress("LongMethod")
  private fun Project.configureAndroidProjects(
    globalConfig: GlobalConfig,
    slackExtension: SlackExtension,
    jvmTargetVersion: Int,
    slackProperties: SlackProperties
  ) {
    val javaVersion = JavaVersion.toVersion(jvmTargetVersion)
    val lintErrorsOnly = slackProperties.lintErrorsOnly

    val commonComponentsExtension =
      Action<AndroidComponentsExtension<*, *, *>> {
        val variantsToDisable =
          slackProperties.disabledVariants?.splitToSequence(",")?.associate {
            val (flavorName, buildType) = it.split("+")
            flavorName to buildType
          }
            ?: emptyMap()
        if (variantsToDisable.isNotEmpty()) {
          val isApp = this is ApplicationAndroidComponentsExtension
          for ((flavorName, buildType) in variantsToDisable) {
            val selector =
              selector().withBuildType(buildType).withFlavor("environment" to flavorName)
            beforeVariants(selector) { builder ->
              builder.enable = false
              builder.enableUnitTest = false
              if (builder is HasAndroidTestBuilder) {
                builder.enableAndroidTest =
                  slackExtension.androidHandler.featuresHandler.androidTest.getOrElse(false)
              }
            }
          }
          if (isApp) {
            beforeVariants { builder -> builder.enableUnitTest = false }
          }
        }
      }

    val shouldApplyCacheFixPlugin = slackProperties.enableAndroidCacheFix
    val sdkVersions by lazy { slackProperties.requireAndroidSdkProperties() }
    val commonBaseExtensionConfig: BaseExtension.() -> Unit = {
      if (shouldApplyCacheFixPlugin) {
        apply(plugin = "org.gradle.android.cache-fix")
      }

      compileSdkVersion(sdkVersions.compileSdk)
      slackProperties.ndkVersion?.let { ndkVersion = it }
      defaultConfig {
        // TODO this won't work with SDK previews but will fix in a followup
        minSdk = sdkVersions.minSdk
        vectorDrawables.useSupportLibrary = true
        // Default to the standard android runner, but note this is overridden in :app
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }

      compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        isCoreLibraryDesugaringEnabled = true
      }

      dependencies.add(
        Configurations.CORE_LIBRARY_DESUGARING,
        SlackDependencies.Google.coreLibraryDesugaring
      )

      testOptions {
        animationsDisabled = true

        if (booleanProperty("orchestrator")) {
          logger.info(
            "[android.testOptions]: Configured to run tests with Android Test Orchestrator"
          )
          execution = "ANDROIDX_TEST_ORCHESTRATOR"
        } else {
          logger.debug(
            "[android.testOptions]: Configured to run tests without Android Test Orchestrator"
          )
        }

        // Added to avoid unimplemented exceptions in some of the unit tests that have simple
        // android dependencies like checking whether code is running on main thread.
        // See https://developer.android.com/training/testing/unit-testing/local-unit-tests
        // #error-not-mocked for more details
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true

        // Configure individual Tests tasks.
        unitTests.all(
          typedClosureOf {
            //
            // Note that we can't configure this to _just_ be enabled for robolectric projects
            // based on dependencies unfortunately, as the task graph is already wired by the time
            // dependencies start getting resolved.
            //
            logger.debug("Configuring $name test task to depend on Robolectric jar downloads")
            dependsOn(globalConfig.updateRobolectricJarsTask)

            // Necessary for some OkHttp-using tests to work on JDK 11 in Robolectric
            // https://github.com/robolectric/robolectric/issues/5115
            systemProperty("javax.net.ssl.trustStoreType", "JKS")
          }
        )
      }
    }

    val objenesis2Version = slackProperties.versions.objenesis
    val prepareAndroidTestConfigurations = {
      configurations.configureEach {
        if (name.contains("androidTest", ignoreCase = true)) {
          // Cover for https://github.com/Kotlin/kotlinx.coroutines/issues/2023
          exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-debug")
          objenesis2Version?.let {
            // Cover for https://github.com/mockito/mockito/pull/2024, as objenesis 3.x is not
            // compatible with Android SDK <26
            resolutionStrategy.force("org.objenesis:objenesis:$it")
          }
        }
      }
    }

    val composeCompilerVersion by lazy {
      slackProperties.versions.composeCompiler
        ?: error("Missing `compose-compiler` version in catalog")
    }

    pluginManager.withPlugin("com.android.base") {
      if (slackProperties.enableCompose) {
        dependencies.add("implementation", SlackDependencies.Androidx.Compose.runtime)
      }
    }

    pluginManager.withPlugin("com.android.application") {
      // Add slack-lint as lint checks source
      slackProperties.versions.bundles.commonLint.ifPresent { dependencies.add("lintChecks", it) }
      prepareAndroidTestConfigurations()
      configure<ApplicationAndroidComponentsExtension> {
        commonComponentsExtension.execute(this)
        // Disable unit tests on release variants, since it's unused
        // TODO maybe we want to disable release androidTest by default? (i.e. for slack kit
        //  playground, samples, etc)
        // TODO would be nice if we could query just non-debuggable build types.
        project.configure<ApplicationAndroidComponentsExtension> {
          beforeVariants { builder ->
            if (builder.buildType == "release") {
              builder.enableUnitTest = false
            }
          }
        }
      }
      configure<BaseAppModuleExtension> {
        commonBaseExtensionConfig()
        if (slackProperties.enableCompose) {
          configureCompose(project, isApp = true, composeCompilerVersion = composeCompilerVersion)
        }
        defaultConfig {
          // TODO this won't work with SDK previews but will fix in a followup
          targetSdk = sdkVersions.targetSdk
        }
        lint {
          lintConfig = rootProject.layout.projectDirectory.file("config/lint/lint.xml").asFile
          sarifReport = true
        }
        lint {
          // This check is _never_ up to date and makes network requests!
          disable += "NewerVersionAvailable"
          // These store qualified gradle caches in their paths and always change in baselines
          disable += "ObsoleteLintCustomCheck"
          // https://groups.google.com/g/lint-dev/c/Bj0-I1RIPyU/m/mlP5Jpe4AQAJ
          error += "ImplicitSamInstance"
          ignoreWarnings = lintErrorsOnly
          checkDependencies = true
          absolutePaths = false

          // Lint is weird in that it will generate a new baseline file and fail the build if a new
          // one was generated, even if empty.
          // If we're updating baselines, always take the baseline so that we populate it if absent.
          project
            .layout
            .projectDirectory
            .file("config/lint/baseline.xml")
            .asFile
            .takeIf { it.exists() || slackProperties.lintUpdateBaselines }
            ?.let { baseline = it }
        }
        packagingOptions {
          resources.excludes +=
            setOf(
              "META-INF/LICENSE.txt",
              "META-INF/LICENSE",
              "META-INF/NOTICE.txt",
              ".readme",
              "META-INF/maven/com.google.guava/guava/pom.properties",
              "META-INF/maven/com.google.guava/guava/pom.xml",
              "META-INF/DEPENDENCIES",
              "**/*.pro",
              "**/*.proto",
              // Metadata for coroutines not relevant to release builds
              "DebugProbesKt.bin",
              // Weird bazel build metadata brought in by Tink
              "build-data.properties",
              "LICENSE_*",
              // We don't know where this comes from but it's 5MB
              // https://slack-pde.slack.com/archives/C8EER3C04/p1621353426001500
              "annotated-jdk/**"
            )
          jniLibs.pickFirsts +=
            setOf(
              // Some libs like Flipper bring their own copy of common native libs (like C++) and we
              // need to de-dupe
              "**/*.so"
            )
        }
        buildTypes {
          getByName("debug") {
            // For upstream android libraries that just have a single release variant, use that.
            matchingFallbacks += "release"
            // Debug should be the default build type. This helps inform studio.
            isDefault = true
          }
        }
        signingConfigs.configureEach {
          enableV3Signing = true
          enableV4Signing = true
        }
        applicationVariants.configureEach {
          mergeAssetsProvider.configure {
            // This task is too expensive to cache while we have embedded emoji fonts
            outputs.cacheIf { false }
          }
        }

        PermissionChecks.configure(
          project = project,
          allowListActionGetter = { slackExtension.androidHandler.appHandler.allowlistAction }
        ) { taskName, file, allowListProvider ->
          tasks.register<CheckManifestPermissionsTask>(taskName) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description =
              "Checks merged manifest permissions against a known allowlist of permissions."
            permissionAllowlistFile.set(file)
            permissionAllowlist.set(allowListProvider)
          }
        }

        pluginManager.withPlugin("com.bugsnag.android.gradle") {
          // See SlackGradleUtil.shouldEnableBugsnagPlugin for more details on this logic
          buildTypes.configureEach {
            // We use withGroovyBuilder here because for the life of me I can't understand where to
            // set this in a strongly typed language.
            withGroovyBuilder {
              getProperty("ext").withGroovyBuilder {
                setProperty(
                  "enableBugsnag",
                  !isDebuggable && globalConfig.shouldEnableBugsnagOnRelease
                )
              }
            }
          }
        }
      }

      slackExtension.androidHandler.configureFeatures(project, slackProperties)
    }

    pluginManager.withPlugin("com.android.library") {
      // Add slack-lint as lint checks source
      slackProperties.versions.bundles.commonLint.ifPresent { dependencies.add("lintChecks", it) }
      prepareAndroidTestConfigurations()
      val isLibraryWithVariants = slackProperties.libraryWithVariants

      configure<LibraryAndroidComponentsExtension> {
        commonComponentsExtension.execute(this)
        if (!isLibraryWithVariants) {
          beforeVariants { variant ->
            when (variant.buildType) {
              "debug" -> {
                // Even in AGP 4 we can't fully remove this yet due to
                // https://issuetracker.google.com/issues/153684320
                variant.enable = false
              }
            }
          }
        }

        // Disable androidTest tasks in libraries unless they opt-in
        beforeVariants { builder ->
          builder.enableAndroidTest =
            slackExtension.androidHandler.featuresHandler.androidTest.getOrElse(false)
        }

        // Contribute these libraries to Fladle if they opt into it
        val androidTestApksAggregator =
          project.rootProject.tasks.named<AndroidTestApksTask>(AndroidTestApksTask.NAME)
        onVariants { variant ->
          val excluded =
            slackExtension.androidHandler.featuresHandler.androidTestExcludeFromFladle.getOrElse(
              false
            )
          if (!excluded) {
            variant.androidTest?.artifacts?.get(SingleArtifact.APK)?.let { apkArtifactsDir ->
              // Wire this up to the aggregator
              androidTestApksAggregator.configure { androidTestApkDirs.from(apkArtifactsDir) }
            }
          }
        }

        // namespace is not a property but we can hook into DSL finalizing to set it at the end
        // if the build script didn't declare one prior
        finalizeDsl { libraryExtension ->
          if (libraryExtension.namespace == null) {
            libraryExtension.namespace =
              "slack" +
                project
                  .path
                  .asSequence()
                  .mapNotNull {
                    when (it) {
                      // Skip dashes and underscores. We could camelcase but it looks weird in a
                      // package name
                      '-',
                      '_' -> null
                      // Use the project path as the real dot namespacing
                      ':' -> '.'
                      else -> it
                    }
                  }
                  .joinToString("")
          }
        }
      }
      configure<LibraryExtension> {
        commonBaseExtensionConfig()
        if (slackProperties.enableCompose) {
          configureCompose(project, isApp = false, composeCompilerVersion = composeCompilerVersion)
        }
        if (isLibraryWithVariants) {
          buildTypes {
            getByName("debug") {
              // For upstream android libraries that just have a single release variant, use that.
              matchingFallbacks += "release"
              // Debug should be the default build type. This helps inform studio.
              isDefault = true
            }
          }
        } else {
          buildTypes {
            getByName("release") {
              // Release should be the default build type. This helps inform studio.
              isDefault = true
            }
          }
          // Default testBuildType is "debug", but AGP doesn't relocate the testBuildType to
          // "release" automatically even if there's only one.
          testBuildType = "release"
        }
        // We don't set targetSdkVersion in libraries since this is controlled by the app.
      }

      slackExtension.androidHandler.configureFeatures(project, slackProperties)
    }
  }

  private fun configureCompose(project: Project, isApp: Boolean, composeCompilerVersion: String) {
    val commonExtensionConfig: CommonExtension<*, *, *, *>.() -> Unit = {
      buildFeatures { compose = true }
      composeOptions { kotlinCompilerExtensionVersion = composeCompilerVersion }
    }
    if (isApp) {
      project.configure<BaseAppModuleExtension> { commonExtensionConfig() }
    } else {
      project.configure<LibraryExtension> { commonExtensionConfig() }
    }
  }

  @Suppress("LongMethod")
  private fun Project.configureKotlinProjects(
    globalConfig: GlobalConfig,
    jdkVersion: Int?,
    jvmTargetVersion: Int,
    slackProperties: SlackProperties
  ) {
    val actualJvmTarget =
      if (jvmTargetVersion == 8) {
        "1.8"
      } else {
        jvmTargetVersion.toString()
      }

    pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
      // Configuration examples https://arturbosch.github.io/detekt/kotlindsl.html
      configure<DetektExtension> {
        toolVersion =
          slackProperties.versions.detekt ?: error("missing 'detekt' version in version catalog")
        rootProject.file("config/detekt/detekt.yml")
        slackProperties.detektConfigs?.let { configs ->
          for (configFile in configs) {
            config.from(rootProject.file(configFile))
          }
        }

        slackProperties.detektBaseline?.let { baselineFile ->
          baseline =
            if (globalConfig.mergeDetektBaselinesTask != null) {
              tasks.withType<DetektCreateBaselineTask>().configureEach {
                globalConfig.mergeDetektBaselinesTask.configure { baselineFiles.from(baseline) }
              }
              file("$buildDir/intermediates/detekt/baseline.xml")
            } else {
              file(rootProject.file(baselineFile))
            }
        }
      }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.base") {
      configure<KotlinProjectExtension> { kotlinDaemonJvmArgs = globalConfig.kotlinDaemonArgs }
      @Suppress("SuspiciousCollectionReassignment")
      tasks.configureEach<KotlinCompile> {
        kotlinOptions {
          if (!slackProperties.allowWarnings && !name.contains("test", ignoreCase = true)) {
            allWarningsAsErrors = true
          }
          jvmTarget = actualJvmTarget
          freeCompilerArgs += kotlinCompilerArgs
          useK2 = slackProperties.useK2

          if (slackProperties.enableCompose && isAndroid) {
            freeCompilerArgs += "-Xskip-prerelease-check"
            // Flag to disable Compose's kotlin version check because they're often behind
            freeCompilerArgs +=
              listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true"
              )
          }

          // Potentially useful for static analysis or annotation processors
          javaParameters = true
        }
      }

      if (jdkVersion != null) {
        configure<KotlinProjectExtension> {
          jvmToolchain { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
        }
      }

      // Set Detekt's jvmTarget
      tasks.configureEach<Detekt> { jvmTarget = actualJvmTarget }

      configureFreeKotlinCompilerArgs()
      if (slackProperties.strictMode && slackProperties.strictValidateKtFilePresence) {
        // Verify that at least one `.kt` file is present in the project's `main` source set. This
        // is important for IC, as otherwise IC will not work!
        // https://youtrack.jetbrains.com/issue/KT-30980
        if (project.file("src/main").walkBottomUp().none { it.extension == "kt" }) {
          throw AssertionError(
            """
            '${project.path}' is a Kotlin project but does not contain any `.kt` files!

            Kotlin projects _must_ have at least one `.kt` file in the `src/main` source set! This
            is necessary in order for incremental compilation to work correctly (see
            https://youtrack.jetbrains.com/issue/KT-30980).

            If you have no files to add (resources-only projects, for instance), you can add a dummy
            compilation marker file like so:

            ```
            /**
             * This class exists solely to force kotlinc to produce incremental compilation
             * information. If we ever add meaningful Kotlin sources to this project, we can then
             * remove this class.
             *
             * Ref: https://youtrack.jetbrains.com/issue/KT-30980
             */
            @Suppress("UnusedPrivateClass")
            private abstract class ${
              CaseFormat.LOWER_HYPHEN.to(
                CaseFormat.UPPER_CAMEL,
                project.name
              )
            }CompilationMarker
            ```
            """.trimIndent()
          )
        }
      }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      // Enable linting on pure JVM projects
      apply(plugin = "com.android.lint")
      // Add slack-lint as lint checks source
      slackProperties.versions.bundles.commonLint.ifPresent { dependencies.add("lintChecks", it) }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
      // Configure kotlin sources in Android projects
      configure<BaseExtension> {
        sourceSets.configureEach {
          val nestedSourceDir = "src/$name/kotlin"
          val dir = File(projectDir, nestedSourceDir)
          if (dir.exists()) {
            // Standard source set
            // Only added if it exists to avoid potentially adding empty source dirs
            java.srcDirs(layout.projectDirectory.dir(nestedSourceDir))
          }
        }
      }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.android.extensions") {
      throw GradleException(
        "Don't use the deprecated 'android.extensions' plugin, switch to " +
          "'plugin.parcelize' instead."
      )
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      configure<KaptExtension> {
        // By default, Kapt replaces unknown types with `NonExistentClass`. This flag asks kapt
        // to infer the type, which is useful for processors that reference to-be-generated
        // classes.
        // https://kotlinlang.org/docs/reference/kapt.html#non-existent-type-correction
        correctErrorTypes = true

        // Maps source errors to Kotlin sources rather than Java stubs
        // Disabled because this triggers a bug in kapt on android 30
        // https://github.com/JetBrains/kotlin/pull/3610
        mapDiagnosticLocations = false
      }

      // See doc on the property for details
      if (!slackProperties.enableKaptInTests) {
        tasks.configureEach {
          if (name.startsWith("kapt") && name.endsWith("TestKotlin", ignoreCase = true)) {
            enabled = false
          }
        }
      }
    }

    pluginManager.withPlugin("com.google.devtools.ksp") {
      configure<KspExtension> {
        // Don't run other plugins like Anvil in KSP's task
        blockOtherCompilerPlugins = true
      }
    }
  }

  /** Common configuration for annotation processors, such as standard options. */
  private fun Project.configureAnnotationProcessors() {
    logger.debug("Configuring any annotation processors on $path")
    val configs =
      APT_OPTION_CONFIGS.mapValues { (_, value) ->
        value.newConfigurer(this@configureAnnotationProcessors)
      }
    configurations.configureEach {
      // Try common case first
      val isApt =
        name in Configurations.Groups.APT ||
          // Try custom configs like testKapt, debugAnnotationProcessor, etc.
          Configurations.Groups.APT.any { name.endsWith(it, ignoreCase = true) }
      if (isApt) {
        val context = ConfigurationContext(project, this@configureEach)
        incoming.afterResolve {
          dependencies.forEach { dependency -> configs[dependency.name]?.configure(context) }
        }
      }
    }
  }

  /**
   * Configures per-dependency free Kotlin compiler args. This is necessary because otherwise
   * kotlinc will emit angry warnings.
   */
  private fun Project.configureFreeKotlinCompilerArgs() {
    logger.debug("Configuring specific Kotlin compiler args on $path")
    val once = AtomicBoolean()
    configurations.configureEach {
      if (isKnownConfiguration(name, Configurations.Groups.RUNTIME)) {
        incoming.afterResolve {
          dependencies.forEach { dependency ->
            KotlinArgConfigs.ALL[dependency.name]?.let { config ->
              if (once.compareAndSet(false, true)) {
                tasks.withType<KotlinCompile>().configureEach {
                  kotlinOptions {
                    @Suppress("SuspiciousCollectionReassignment") // This isn't suspicious
                    freeCompilerArgs += config.args
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  companion object {
    private val APT_OPTION_CONFIGS: Map<String, AptOptionsConfig> =
      AptOptionsConfigs().associateBy { it.targetDependency }

    /** Top-level JVM plugin IDs. Usually only one of these is applied. */
    private val JVM_PLUGINS =
      setOf(
        "application",
        "java",
        "java-library",
        "org.jetbrains.kotlin.jvm",
        "com.android.library",
        "com.android.application"
      )

    private fun isKnownConfiguration(configurationName: String, knownNames: Set<String>): Boolean {
      // Try trimming the flavor by just matching the suffix
      return knownNames.any { platformConfig ->
        configurationName.endsWith(platformConfig, ignoreCase = true)
      }
    }

    /**
     * Best effort fuzzy matching on known configuration names that we want to opt into platforming.
     * We don't blanket apply them to all configurations because
     */
    internal fun isPlatformConfigurationName(name: String): Boolean {
      // Kapt/ksp/compileOnly are special cases since they can be combined with others
      val isKaptOrCompileOnly =
        name.startsWith(Configurations.KAPT, ignoreCase = true) ||
          name.startsWith(Configurations.KSP, ignoreCase = true) ||
          name == Configurations.COMPILE_ONLY
      if (isKaptOrCompileOnly) {
        return true
      }
      return isKnownConfiguration(name, Configurations.Groups.PLATFORM)
    }
  }
}

internal interface KotlinArgConfig {
  val targetDependency: String
  val args: Set<String>
}

@Suppress("unused") // Nested classes here are looked up reflectively
internal object KotlinArgConfigs {

  val ALL: Map<String, KotlinArgConfig> by lazy {
    KotlinArgConfigs::class
      .nestedClasses
      .map { it.objectInstance }
      .filterIsInstance<KotlinArgConfig>()
      .associateBy { it.targetDependency }
  }

  object Coroutines : KotlinArgConfig {
    override val targetDependency: String = "kotlinx-coroutines-core"
    override val args =
      setOf(
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlinx.coroutines.FlowPreview"
      )
  }
}

/** A simple context for the current configuration being processed. */
internal data class ConfigurationContext(val project: Project, val configuration: Configuration) {
  val isKaptConfiguration = configuration.name.endsWith("kapt", ignoreCase = true)
}

/**
 * A common interface for configuration of annotation processors. It's recommended to make
 * implementers of this interface `object` types. The pipeline for configuration of projects will
 * appropriately call [newConfigurer] per-project to create a project-local context.
 */
internal interface AptOptionsConfig {

  /**
   * The targeted dependency of this config. This should be treated as the maven artifact ID of the
   * dependency, such as "dagger-compiler". This should ideally also be a constant.
   */
  val targetDependency: String

  /** @return a new [AptOptionsConfigurer] for this config on the target [project]. */
  fun newConfigurer(project: Project): AptOptionsConfigurer

  interface AptOptionsConfigurer {

    val project: Project

    /**
     * Configure appropriate annotation processor options on the given [project] given the current
     * [configurationContext].
     */
    fun configure(configurationContext: ConfigurationContext)
  }
}

/**
 * A basic [BasicAptOptionsConfig] that makes setup ceremony easier for common cases. This tries to
 * ensure an optimized configuration that avoids object/action allocations and simplify wiring into
 * our different common project types.
 *
 * The general usage that you define a top level object that extends this and override the necessary
 * properties.
 *
 * ```
 * object ButterKnifeAptOptionsConfig : BasicAptOptionsConfig() {
 *   override val targetDependency: String = "butterknife-compiler"
 *   override val globalOptions: Map<String, String> = mapOf("butterknife.minSdk" to "21")
 * }
 * ```
 */
internal abstract class BasicAptOptionsConfig : AptOptionsConfig {
  private val rawAptCompilerOptions by lazy {
    globalOptions.map { (option, value) -> "-A$option=$value" }
  }

  open val name: String = this::class.java.simpleName
  open val globalOptions: Map<String, String> = emptyMap()

  val javaCompileAptAction =
    Action<JavaCompile> { options.compilerArgs.addAll(rawAptCompilerOptions) }

  final override fun newConfigurer(project: Project): AptOptionsConfigurer {
    return newConfigurer(project, BasicAptOptionsConfigurer(project, this))
  }

  /**
   * Optional callback with the created basic configurer. By default this just returns that created
   * instance, but you can optionally override this to customize the behavior. Using class
   * delegation is recommended to simplify reuse.
   */
  open fun newConfigurer(
    project: Project,
    basicConfigurer: AptOptionsConfigurer
  ): AptOptionsConfigurer = basicConfigurer

  private class BasicAptOptionsConfigurer(
    override val project: Project,
    private val baseConfig: BasicAptOptionsConfig
  ) : AptOptionsConfigurer {

    private val baseBuildTypeAction =
      Action<BuildType> {
        project.logger.debug(
          "${baseConfig.name}: Adding javac apt options to android project " +
            "${project.path} at buildType $name"
        )
        baseConfig.globalOptions.forEach { (key, value) ->
          javaCompileOptions.annotationProcessorOptions.arguments[key] = value
        }
      }

    private val javaLibraryAction =
      Action<Any> {
        // Implicitly not using Kotlin because we would have to use Kapt
        project.logger.debug(
          "${baseConfig.name}: Adding javac apt options to android project ${project.path}"
        )
        project.tasks.withType(JavaCompile::class.java, baseConfig.javaCompileAptAction)
      }

    override fun configure(configurationContext: ConfigurationContext) =
      with(configurationContext.project) {
        if (configurationContext.isKaptConfiguration) {
          logger.debug("${baseConfig.name}: Adding kapt arguments to $path")
          configure<KaptExtension> {
            arguments { baseConfig.globalOptions.forEach { (key, value) -> arg(key, value) } }
          }
        } else {
          project.pluginManager.withPlugin("com.android.application") {
            logger.debug(
              "${baseConfig.name}: Adding javac apt options to android application project $path"
            )
            configure<AppExtension> { buildTypes.configureEach(baseBuildTypeAction) }
          }
          project.pluginManager.withPlugin("com.android.library") {
            logger.debug(
              "${baseConfig.name}: Adding javac apt options to android library project $path"
            )
            configure<LibraryExtension> { buildTypes.configureEach(baseBuildTypeAction) }
          }
          project.pluginManager.withPlugin("java", javaLibraryAction)
          project.pluginManager.withPlugin("java-library", javaLibraryAction)
        }
      }
  }
}

/**
 * All [AptOptionsConfig] types. Please follow the standard structure of one object per config and
 * don't add any other types. This ensures that [invoke] works smoothly.
 */
@Suppress("unused") // Nested classes here are looked up reflectively
internal object AptOptionsConfigs {

  operator fun invoke(): List<AptOptionsConfig> =
    AptOptionsConfigs::class
      .nestedClasses
      .map { it.objectInstance }
      .filterIsInstance<AptOptionsConfig>()

  object Dagger : BasicAptOptionsConfig() {
    override val targetDependency: String = "dagger-compiler"
    override val globalOptions: Map<String, String> =
      mapOf(
        "dagger.warnIfInjectionFactoryNotGeneratedUpstream" to "enabled",
        // New error messages. Feedback should go to https://github.com/google/dagger/issues/1769
        "dagger.experimentalDaggerErrorMessages" to "enabled",
        // Fast init mode for improved dagger perf on startup:
        // https://dagger.dev/dev-guide/compiler-options.html
        "dagger.fastInit" to "enabled"
      )
  }

  object Moshi : BasicAptOptionsConfig() {
    override val targetDependency: String = "moshi-kotlin-codegen"
    override val globalOptions: Map<String, String> =
      mapOf("moshi.generated" to "javax.annotation.Generated")
  }
}
