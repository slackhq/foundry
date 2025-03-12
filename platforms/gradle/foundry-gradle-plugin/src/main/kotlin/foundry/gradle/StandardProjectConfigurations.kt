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

package foundry.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasAndroidTestBuilder
import com.android.build.api.variant.HasUnitTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.api.variant.TestVariant
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.tasks.JavaPreCompileTask
import com.autonomousapps.DependencyAnalysisSubExtension
import com.bugsnag.android.gradle.BugsnagPluginExtension
import foundry.gradle.Configurations.isPlatformConfigurationName
import foundry.gradle.android.AndroidArchitecture
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.dependencies.FoundryDependencies
import foundry.gradle.dependencyrake.RakeDependencies
import foundry.gradle.kgp.KgpTasks
import foundry.gradle.lint.LintTasks
import foundry.gradle.permissionchecks.PermissionChecks
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.tasks.AndroidTestApksTask
import foundry.gradle.tasks.CheckManifestPermissionsTask
import foundry.gradle.tasks.SimpleFileProducerTask
import foundry.gradle.tasks.publishWith
import foundry.gradle.tasks.robolectric.UpdateRobolectricJarsTask
import foundry.gradle.testing.EmulatorWtfTests
import foundry.gradle.testing.RoborazziTests
import foundry.gradle.testing.UnitTests
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaCompiler
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.base.plugins.LifecycleBasePlugin
import wtf.emulator.EwExtension

private const val LOG = "Foundry:"
private const val FIVE_MINUTES_MS = 300_000L

private fun Logger.logWithTag(message: String) {
  debug("$LOG $message")
}

/**
 * Standard [Project] configurations. This class will be iterated on over time as we grow out our
 * bootstrapping options for Gradle subprojects.
 *
 * Principles:
 * - Avoid duplicating work and allocations. This runs at configuration time and should be as low
 *   overhead as possible.
 * - Do not resolve dependencies at configuration-time. Use appropriate callback APIs!
 * - Support Kotlin, Android, and Java projects.
 * - One-off configuration should be left to individual projects to declare.
 * - Use debug logging.
 */
@Suppress("TooManyFunctions")
internal class StandardProjectConfigurations(
  private val globalProperties: FoundryProperties,
  private val versionCatalog: VersionCatalog,
  private val foundryTools: FoundryTools,
) {
  fun applyTo(
    project: Project,
    foundryExtension: FoundryExtension,
    foundryProperties: FoundryProperties,
  ) {
    if (foundryProperties.eagerlyConfigureArtifactPublishing) {
      setUpSubprojectArtifactPublishing(project)
    }
    project.applyCommonConfigurations(foundryProperties)
    project.applyJvmConfigurations(foundryProperties, foundryExtension)
    KgpTasks.configure(project, foundryTools, foundryProperties)
  }

  /**
   * Always enables publishing of all SgpArtifacts, even if we never end up publishing artifacts
   * This sucks but I don't see any other way to do this due to how tightly locked down Gradle's
   * inter-project access APIs are in project isolation.
   *
   * Ideally, we would only add project dependencies when they are definitely able to contribute
   * artifacts to that configuration, but that's not possible when:
   * 1. Root projects can't reach into subprojects to ask about their configuration
   * 2. Subprojects can't reach into root projects to add dependencies conditionally
   * 3. There doesn't seem to be a way to depend on a certain project's configuration if that
   *    configuration doesn't exist. This sorta makes sense, but for the purpose of inter-project
   *    artifacts I wish it was possible to depend on a configuration that may not exist and just
   *    treat it as an empty config that publishes no artifacts.
   *
   * It _seems_ like #3 is possible via [ArtifactView.ViewConfiguration.lenient], so this function
   * is behind a flag just as a failsafe.
   */
  private fun setUpSubprojectArtifactPublishing(project: Project) {
    for (artifact in FoundryArtifact::class.sealedSubclasses) {
      Publisher.interProjectPublisher(project, artifact.objectInstance!!)
    }
  }

  private fun Project.applyCommonConfigurations(foundryProperties: FoundryProperties) {
    if (globalProperties.autoApplySortDependencies) {
      if (project.buildFile.exists()) {
        val sortDependenciesIgnoreSet =
          globalProperties.sortDependenciesIgnore?.splitToSequence(',')?.toSet().orEmpty()
        if (project.path !in sortDependenciesIgnoreSet) {
          pluginManager.apply("com.squareup.sort-dependencies")
        }
      }
    }
    LintTasks.configureSubProject(
      project,
      foundryProperties,
      foundryTools.globalConfig.affectedProjects,
      foundryTools::logAvoidedTask,
    )
  }

  @Suppress("unused")
  private fun Project.javaCompilerFor(version: Int): Provider<JavaCompiler> {
    return extensions.getByType<JavaToolchainService>().compilerFor {
      languageVersion.setDisallowChanges(JavaLanguageVersion.of(version))
      foundryTools.globalConfig.jvmVendor?.let(vendor::set)
    }
  }

  private fun Project.applyJvmConfigurations(
    foundryProperties: FoundryProperties,
    foundryExtension: FoundryExtension,
  ) {
    val platformProjectPath = foundryProperties.platformProjectPath
    if (platformProjectPath == null) {
      if (foundryProperties.strictMode) {
        logger.warn(
          "foundry.location.foundry-platform is not set. Consider creating one to ensure consistent dependency versions across projects!"
        )
      }
    } else if (!foundryProperties.noPlatform && path != platformProjectPath) {
      applyPlatforms(foundryProperties.versions.boms, platformProjectPath)
    }

    checkAndroidXDependencies(foundryProperties)
    AnnotationProcessing.configureFor(project)

    pluginManager.onFirst(JVM_PLUGINS) { pluginId ->
      foundryProperties.versions.bundles.commonAnnotations.ifPresent {
        dependencies.add("implementation", it)
      }

      UnitTests.configureSubproject(
        project,
        pluginId,
        foundryProperties,
        foundryTools.globalConfig.affectedProjects,
        foundryTools::logAvoidedTask,
      )

      if (pluginId != "com.android.test") {
        // Configure dependencyAnalysis
        // TODO move up once DAGP supports com.android.test projects
        //  https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/issues/797
        if (foundryProperties.enableAnalysisPlugin && project.path != platformProjectPath) {
          val buildFile = project.buildFile
          // This can run on some intermediate middle directories, like `carbonite` in
          // `carbonite:carbonite`
          if (buildFile.exists()) {
            // Configure rake
            plugins.withId("com.autonomousapps.dependency-analysis") {
              val isNoApi = foundryProperties.rakeNoApi
              val catalogNames =
                extensions.findByType<VersionCatalogsExtension>()?.catalogNames ?: return@withId

              val catalogs =
                catalogNames.map { catalogName -> project.getVersionsCatalog(catalogName) }

              val rakeDependencies =
                tasks.register<RakeDependencies>("rakeDependencies") {
                  // TODO https://github.com/gradle/gradle/issues/25014
                  buildFileProperty.set(project.buildFile)
                  noApi.setDisallowChanges(isNoApi)
                  identifierMap.setDisallowChanges(
                    project.provider {
                      buildMap {
                        for (catalog in catalogs) {
                          putAll(
                            catalog.identifierMap().mapValues { (_, v) -> "${catalog.name}.$v" }
                          )
                        }
                      }
                    }
                  )
                  missingIdentifiersFile.set(
                    project.layout.buildDirectory.file("rake/missing_identifiers.txt")
                  )
                }
              configure<DependencyAnalysisSubExtension> {
                registerPostProcessingTask(rakeDependencies)
              }
              val publisher =
                Publisher.interProjectPublisher(project, FoundryArtifact.DagpMissingIdentifiers)
              publisher.publish(rakeDependencies.flatMap { it.missingIdentifiersFile })
            }
          }
        }
      }
    }

    configureAndroidProjects(foundryExtension, foundryProperties)
    configureJavaProject(foundryProperties)
    foundryExtension.applyTo(this)
  }

  /**
   * Applies platform()/bom dependencies for projects, right now only on known
   * [Configurations.Groups.PLATFORM].
   */
  private fun Project.applyPlatforms(
    boms: Set<Provider<MinimalExternalModuleDependency>>,
    platformProject: String,
  ) {
    configurations.configureEach {
      if (Configurations.isTest(name) && Configurations.isApi(name)) {
        // Don't add dependencies to testApi configurations as these are never used
        // https://youtrack.jetbrains.com/issue/KT-61653
        project.logger.debug("Ignoring boms on ${project.path}:$name")
        return@configureEach
      }
      if (isPlatformConfigurationName(name)) {
        project.logger.debug("Adding boms to ${project.path}:$name")
        project.dependencies.apply {
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
  private fun Project.checkAndroidXDependencies(foundryProperties: FoundryProperties) {
    if (!foundryProperties.skipAndroidxCheck) {
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
  private fun Project.configureJavaProject(foundryProperties: FoundryProperties) {
    val releaseVersion = foundryProperties.jvmTarget.map(JavaVersion::toVersion)
    plugins.withType(JavaBasePlugin::class.java).configureEach {
      project.configure<JavaPluginExtension> {
        sourceCompatibility = releaseVersion.get()
        targetCompatibility = releaseVersion.get()
      }
      foundryProperties.versions.jdk.ifPresent {
        if (it >= 9) {
          tasks.configureEach<JavaCompile> {
            if (!isAndroid) {
              logger.logWithTag("Configuring release option for $path")
              options.release.setDisallowChanges(releaseVersion.map { it.majorVersion.toInt() })
            }
          }
        }
      }
    }

    val javaToolchains by lazy { project.serviceOf<JavaToolchainService>() }

    tasks.withType(JavaCompile::class.java).configureEach {
      // Keep parameter names, this is useful for annotation processors and static analysis tools
      options.compilerArgs.addAll(listOf("-parameters"))

      // Android is our lowest JVM target, so if we're an android project we'll always use that
      // source target.
      // TODO is this late enough to be safe?
      // TODO if we set it in android, does the config from this get safely ignored?
      // TODO re-enable in android at all after AGP 7.1
      if (!isAndroid) {
        val target =
          if (isAndroid) releaseVersion
          else
            foundryProperties.versions.jdk.map(JavaVersion::toVersion).asProvider(project.providers)
        logger.logWithTag("Configuring toolchain for $path")
        // Can't use disallowChanges here because Gradle sets it again later for some reason
        javaCompiler.set(
          javaToolchains.compilerFor {
            languageVersion.setDisallowChanges(
              target.map { JavaLanguageVersion.of(it.majorVersion) }
            )
            foundryTools.globalConfig.jvmVendor?.let(vendor::set)
          }
        )
      }
    }

    configureErrorProne(foundryProperties)
  }

  /**
   * Adds common configuration for error prone on Java projects. Note that this still uses
   * application of the error prone plugin as an opt-in marker for now, and is not applied to every
   * project.
   */
  private fun Project.configureErrorProne(foundryProperties: FoundryProperties) {
    val autoPatchEnabled = foundryProperties.errorProneAutoPatch
    pluginManager.withPlugin("net.ltgt.nullaway") {
      val nullawayBaseline = foundryProperties.nullawayBaseline

      val nullawayDep =
        foundryProperties.versions.getOptionalValue("errorProne-nullaway").orElseThrow {
          IllegalStateException("Could not find errorProne-nullaway in the catalog")
        }
      dependencies.apply { add("errorprone", nullawayDep) }

      tasks.withType(JavaCompile::class.java).configureEach {
        val nullAwaySeverity =
          if (name.contains("test", ignoreCase = true)) {
            CheckSeverity.OFF
          } else {
            CheckSeverity.ERROR
          }
        options.errorprone.nullaway {
          severity.setDisallowChanges(nullAwaySeverity)
          // TODO make this configurable
          annotatedPackages.add("slack")
          checkOptionalEmptiness.setDisallowChanges(true)
          if (autoPatchEnabled && nullawayBaseline) {
            suggestSuppressions.setDisallowChanges(true)
            autoFixSuppressionComment.setDisallowChanges(
              "Nullability issue auto-patched by NullAway."
            )
            // TODO make this configurable
            castToNonNullMethod.setDisallowChanges("slack.commons.JavaPreconditions.castToNotNull")
          }
        }
      }
    }
    pluginManager.withPlugin("net.ltgt.errorprone") {
      dependencies.add("errorprone", FoundryDependencies.ErrorProne.core)

      val isAndroidProject = isAndroid

      tasks.withType(JavaCompile::class.java).configureEach {
        options.errorprone {
          disableWarningsInGeneratedCode.setDisallowChanges(true)
          // The EP flag alone isn't enough
          // https://github.com/google/error-prone/issues/2092
          excludedPaths.setDisallowChanges(".*/build/generated/.*")
          disable("HidingField")
          error(*foundryTools().globalConfig.errorProneCheckNamesAsErrors.toTypedArray())

          if (isAndroidProject) {
            options.compilerArgs.add("-XDandroidCompatible=true")
          }

          // Enable autopatching via "-PepAutoPatch=true". This patches in-place and requires a
          // recompilation after.
          // This could be useful to enable on CI + a git porcelain check to see if there's any
          // patchable error prone fixes.
          if (autoPatchEnabled) {
            // Always log this verbosely
            logger.lifecycle("Enabling error-prone auto-patching on ${project.path}:$name")
            errorproneArgs.addAll(
              "-XepPatchChecks:${ERROR_PRONE_CHECKS.joinToString(",")}",
              "-XepPatchLocation:IN_PLACE",
            )
          }
        }
      }
    }
  }

  @Suppress("LongMethod")
  private fun Project.configureAndroidProjects(
    foundryExtension: FoundryExtension,
    foundryProperties: FoundryProperties,
  ) {
    val javaVersion = foundryProperties.jvmTarget.map(JavaVersion::toVersion)
    // Contribute these libraries to Fladle if they opt into it
    val androidTestApksPublisher =
      Publisher.interProjectPublisher(project, FoundryArtifact.AndroidTestApkDirs)
    val projectPath = project.path
    val isAffectedProject =
      foundryTools.globalConfig.affectedProjects?.contains(projectPath) != false
    val skippyAndroidTestProjectPublisher =
      Publisher.interProjectPublisher(project, FoundryArtifact.SkippyAndroidTestProject)

    val commonComponentsExtension =
      Action<AndroidComponentsExtension<*, *, *>> {
        val variantsToDisable =
          foundryProperties.disabledVariants?.splitToSequence(",")?.associate {
            val (flavorName, buildType) = it.split("+")
            flavorName to buildType
          } ?: emptyMap()
        if (variantsToDisable.isNotEmpty()) {
          logger.debug("$LOG Disabling variants: $variantsToDisable")
          val isApp = this is ApplicationAndroidComponentsExtension
          for ((flavorName, buildType) in variantsToDisable) {
            val selector =
              selector().withBuildType(buildType).withFlavor("environment" to flavorName)
            beforeVariants(selector) { builder ->
              builder.enable = false
              // AGP has confusing declaration mismatches about this deprecation so we cast it
              if (builder is HasUnitTestBuilder) {
                (builder as HasUnitTestBuilder).enableUnitTest = false
              }
              if (builder is HasAndroidTestBuilder) {
                builder.androidTest.enable = false
              }
            }
          }
          if (isApp) {
            beforeVariants { builder ->
              // AGP has confusing declaration mismatches about this deprecation so we cast it
              (builder as HasUnitTestBuilder).enableUnitTest = false
            }
          }
        }

        finalizeDsl {
          val androidTestEnabled =
            this is TestAndroidComponentsExtension ||
              foundryExtension.androidHandler.featuresHandler.androidTest.getOrElse(false)
          if (androidTestEnabled && foundryProperties.enableEmulatorWtfForAndroidTest) {
            project.pluginManager.apply("wtf.emulator.gradle")
            project.configure<EwExtension> {
              // whether to enable Android orchestrator, if your app has orchestrator
              // configured this will get picked up automatically, however you can
              // force-change the value here if you want to
              useOrchestrator.set(foundryProperties.useOrchestrator)

              // whether to clear package data before running each test (orchestrator only)
              // if your app has this configured via testInstrumentationRunnerArguments then
              // it will get picked up automatically
              clearPackageData.set(foundryProperties.useOrchestrator)
            }
            if (foundryProperties.enableEmulatorWtfPerTestVideo) {
              when (this) {
                is LibraryAndroidComponentsExtension,
                is ApplicationAndroidComponentsExtension -> {
                  dependencies.add("androidTestImplementation", "wtf.emulator:test-runtime-android")
                }
                is TestAndroidComponentsExtension -> {
                  dependencies.add("implementation", "wtf.emulator:test-runtime-android")
                }
              }
            }
          }
        }

        // Configure androidTest
        onVariants { variant ->
          val isLibraryVariant = variant is LibraryVariant
          val excluded =
            isLibraryVariant &&
              foundryExtension.androidHandler.featuresHandler.androidTestExcludeFromAggregation
                .getOrElse(false)
          val isAndroidTestEnabled =
            (variant is HasAndroidTest && variant.androidTest != null) || variant is TestVariant
          if (isAndroidTestEnabled) {
            if (foundryProperties.useOrchestrator.getOrElse(false)) {
              dependencies.add("androidTestUtil", "androidx.test:orchestrator")
            }
            if (!excluded) {
              val packaging =
                (variant as? LibraryVariant)?.androidTest?.packaging
                  ?: (variant as? TestVariant)?.packaging
              packaging?.apply {
                dex.useLegacyPackaging.set(
                  foundryProperties.compressAndroidTestApksWithLegacyPackaging
                )
                jniLibs.useLegacyPackaging.set(
                  foundryProperties.compressAndroidTestApksWithLegacyPackaging
                )
                jniLibs.useLegacyPackagingFromBundle.set(
                  foundryProperties.compressAndroidTestApksWithLegacyPackaging
                )
                foundryProperties.targetAndroidTestApksArch.orNull?.let { targetArch ->
                  jniLibs.excludes.addAll(
                    // Exclude out non-targeted architectures
                    AndroidArchitecture.entries
                      .filterNot { it == targetArch }
                      .map { "**/${it.jniLibsPath}/*.so" }
                  )
                }
              }

              if (isAffectedProject) {
                // Aggregate test apks. In Fladle we aggregate test APKs, in emulator.wtf we
                // aggregate
                // to their root project dep
                if (foundryProperties.enableEmulatorWtfForAndroidTest) {
                  // Aggregate to emulator.wtf's configuration instead
                  // TODO this doesn't work yet, toe-hold for the future
                  // @Suppress("GradleProjectIsolation")
                  // project.rootProject.dependencies.add(
                  //   "emulatorwtf",
                  //   project.rootProject.project(project.path),
                  // )
                } else {
                  // Note this intentionally just uses the same task each time as they always
                  // produce
                  // the same output
                  SimpleFileProducerTask.registerOrConfigure(
                      project,
                      name = "androidTestProjectMetadata",
                      description =
                        "Produces a metadata artifact indicating this project path produces an androidTest APK.",
                      input = projectPath,
                      group = "skippy",
                    )
                    .publishWith(skippyAndroidTestProjectPublisher)
                  if (isLibraryVariant) {
                    val libraryVariant = variant as LibraryVariant
                    libraryVariant.androidTest?.apply {
                      // Wire this up to the aggregator. No need for an intermediate task here.
                      androidTestApksPublisher.publishDirs(artifacts.get(SingleArtifact.APK))
                    }
                  }
                }
              }
            }

            if (excluded || !isAffectedProject) {
              val reason = if (excluded) "excluded" else "not affected"
              val taskPath = "${projectPath}:androidTest"
              val log = "$LOG Skipping $taskPath because it is $reason."
              foundryTools.logAvoidedTask(AndroidTestApksTask.NAME, taskPath)
              if (foundryProperties.debug) {
                project.logger.lifecycle(log)
              } else {
                project.logger.debug(log)
              }
            }
          }
        }
      }

    val sdkVersions = lazy { foundryProperties.requireAndroidSdkProperties() }
    val shouldApplyCacheFixPlugin = foundryProperties.enableAndroidCacheFix
    val commonBaseExtensionConfig: BaseExtension.(applyTestOptions: Boolean) -> Unit =
      { applyTestOptions ->
        if (shouldApplyCacheFixPlugin) {
          pluginManager.apply("org.gradle.android.cache-fix")
        }

        compileSdkVersion(sdkVersions.value.compileSdk)
        foundryProperties.ndkVersion?.let { ndkVersion = it }
        foundryProperties.buildToolsVersionOverride?.let { buildToolsVersion = it }
        val useOrchestrator = foundryProperties.useOrchestrator.getOrElse(false)
        defaultConfig {
          minSdk = sdkVersions.value.minSdk
          vectorDrawables.useSupportLibrary = true

          if (applyTestOptions) {
            testInstrumentationRunner = foundryProperties.testInstrumentationRunner

            if (useOrchestrator) {
              // The following argument makes the Android Test Orchestrator run its
              // "pm clear" command after each test invocation. This command ensures
              // that the app's state is completely cleared between tests.
              testInstrumentationRunnerArguments.putAll(mapOf("clearPackageData" to "true"))
            }
          }
        }

        compileOptions {
          sourceCompatibility = javaVersion.get()
          targetCompatibility = javaVersion.get()
          isCoreLibraryDesugaringEnabled = true
        }

        dependencies.add(
          Configurations.CORE_LIBRARY_DESUGARING,
          versionCatalog.findLibrary("google-coreLibraryDesugaring").get(),
        )

        if (applyTestOptions) {
          testOptions {
            animationsDisabled = true

            if (useOrchestrator) {
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
            if (foundryProperties.alwaysEnableResourcesInTests) {
              unitTests.isIncludeAndroidResources = true
            }

            // Configure individual Tests tasks.
            if (globalProperties.versions.robolectric != null) {
              unitTests.all { test ->
                //
                // Note that we can't configure this to _just_ be enabled for robolectric projects
                // based on dependencies unfortunately, as the task graph is already wired by the
                // time dependencies start getting resolved.
                //
                foundryProperties.versions.robolectric?.let {
                  logger.debug("Configuring $name test task to depend on Robolectric jar downloads")
                  // Depending on the root project task by name alone is ok for Project Isolation
                  test.dependsOn(":${UpdateRobolectricJarsTask.NAME}")
                }

                // Necessary for some OkHttp-using tests to work on JDK 11 in Robolectric
                // https://github.com/robolectric/robolectric/issues/5115
                test.systemProperty("javax.net.ssl.trustStoreType", "JKS")
              }
            }
          }
        }
      }

    val objenesis2Version = foundryProperties.versions.objenesis
    val prepareAndroidTestConfigurations = { configToMatch: String ->
      configurations.configureEach {
        if (name.contains(configToMatch, ignoreCase = true)) {
          // Cover for https://github.com/Kotlin/kotlinx.coroutines/issues/2023
          exclude(mapOf("group" to "org.jetbrains.kotlinx", "module" to "kotlinx-coroutines-debug"))
          objenesis2Version?.let {
            // Cover for https://github.com/mockito/mockito/pull/2024, as objenesis 3.x is not
            // compatible with Android SDK <26
            resolutionStrategy.force("org.objenesis:objenesis:$it")
          }
        }
      }
    }

    pluginManager.withPlugin("com.android.base") {
      tasks.withType(JavaPreCompileTask::class.java).configureEach {
        doFirst {
          // JavaPreCompileTask incorrectly reads annotation processors from the ksp classpath
          // and then warns about them ending up in the JavaCompile tasks even though they're
          // not on the classpath. This works around that by clearing out that field before it
          // tries to merge them in with annotationProcessorArtifacts.
          // https://issuetracker.google.com/issues/331806519
          CACHED_KSP_ARTIFACTS_FIELD.set(this, null)
        }
      }
    }

    pluginManager.withPlugin("com.android.application") {
      prepareAndroidTestConfigurations("androidTest")
      configure<ApplicationAndroidComponentsExtension> {
        commonComponentsExtension.execute(this)
        // Disable androidTest tasks unless they opt-in
        beforeVariants { builder ->
          // Disable unit tests on release variants, since it's unused
          if (builder.buildType == "release") {
            // AGP has confusing declaration mismatches about this deprecation so we cast it
            (builder as HasUnitTestBuilder).enableUnitTest = false
          }

          // Must be in the beforeVariants block to defer read until after evaluation
          val androidTestEnabled =
            foundryExtension.androidHandler.featuresHandler.androidTest.getOrElse(false)
          val variantEnabled =
            androidTestEnabled &&
              foundryExtension.androidHandler.featuresHandler.androidTestAllowedVariants.orNull
                ?.contains(builder.name) != false
          logger.debug("$LOG AndroidTest for ${builder.name} enabled? $variantEnabled")
          builder.androidTest.enable = variantEnabled
        }

        onVariants(selector().withBuildType("release")) { variant ->
          // Metadata for coroutines not relevant to release builds
          variant.packaging.resources.excludes.add("DebugProbesKt.bin")
        }
      }
      configure<BaseAppModuleExtension> {
        foundryExtension.setAndroidExtension(this)
        commonBaseExtensionConfig(true)
        defaultConfig {
          // TODO this won't work with SDK previews but will fix in a followup
          targetSdk = sdkVersions.value.targetSdk
        }
        packaging {
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
              // Weird bazel build metadata brought in by Tink
              "build-data.properties",
              "LICENSE_*",
              // We don't know where this comes from but it's 5MB
              // https://slack-pde.slack.com/archives/C8EER3C04/p1621353426001500
              "annotated-jdk/**",
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

        PermissionChecks.configure(
          project = project,
          allowListActionGetter = { foundryExtension.androidHandler.appHandler.allowlistAction },
        ) { taskName, file, allowListProvider ->
          tasks.register<CheckManifestPermissionsTask>(taskName) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description =
              "Checks merged manifest permissions against a known allowlist of permissions."
            permissionAllowlistFile.setDisallowChanges(file)
            permissionAllowlist.setDisallowChanges(allowListProvider)
          }
        }

        pluginManager.withPlugin("com.bugsnag.android.gradle") {
          val branchMatchesPatternProvider =
            foundryProperties.bugsnagEnabledBranchPattern.zip(gitBranchProvider()) { pattern, branch
              ->
              if (pattern == null || branch == null) {
                return@zip false
              }
              pattern.toRegex().matches(branch)
            }

          val enabledProvider =
            foundryProperties.bugsnagEnabled.orElse(branchMatchesPatternProvider).orElse(false).zip(
              provider { isCi }
            ) { enabled, isRunningOnCi ->
              // Only enable if we're also on CI
              enabled && isRunningOnCi
            }

          configure<BugsnagPluginExtension> {
            variantFilter {
              // disables plugin for all debug variants
              // This is technically the default behavior, buuuuut let's be super sure eh?
              if (name.contains("debug", ignoreCase = true)) {
                setEnabled(false)
              }
            }

            // 5 minute timeout because let's be real, if it's taking this long something is wrong
            requestTimeoutMs.setDisallowChanges(FIVE_MINUTES_MS)

            // Enable uploads if the enable prop is enabled or the branch matches a provided pattern
            // Note we _don't_ use the BugsnagPluginExtension.enabled property itself because we do
            // want bugsnag to do most of its regular process, just skipping uploads unless enabled.
            uploadJvmMappings.setDisallowChanges(enabledProvider)
            reportBuilds.setDisallowChanges(enabledProvider)

            // We don't use these
            uploadNdkMappings.setDisallowChanges(false)
            uploadNdkUnityLibraryMappings.setDisallowChanges(false)
            uploadReactNativeMappings.setDisallowChanges(false)
          }
        }
      }

      foundryExtension.androidHandler.applyTo(project)
    }

    pluginManager.withPlugin("com.android.library") {
      prepareAndroidTestConfigurations("androidTest")
      val isLibraryWithVariants = foundryProperties.libraryWithVariants

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
          val androidTestEnabled =
            foundryExtension.androidHandler.featuresHandler.androidTest.getOrElse(false)
          val variantEnabled =
            androidTestEnabled &&
              foundryExtension.androidHandler.featuresHandler.androidTestAllowedVariants.orNull
                ?.contains(builder.name) != false
          builder.androidTest.enable = variantEnabled
          if (variantEnabled) {
            // Ensure there's a manifest file present and has its debuggable flag set correctly
            if (
              foundryProperties.strictMode && foundryProperties.strictValidateAndroidTestManifest
            ) {
              val manifest = project.file("src/androidTest/AndroidManifest.xml")
              check(manifest.exists()) {
                "AndroidManifest.xml is missing from src/androidTest. Ensure it exists and also is set to debuggable!"
              }
              check(manifest.readText().contains("android:debuggable=\"true\"")) {
                "AndroidManifest.xml in src/androidTest is missing the debuggable flag! Ensure it is set to 'android:debuggable=\"true\"'"
              }
            }
          }
        }

        // namespace is not a property but we can hook into DSL finalizing to set it at the end
        // if the build script didn't declare one prior
        finalizeDsl { libraryExtension ->
          if (libraryExtension.namespace == null) {
            libraryExtension.namespace =
              foundryProperties.defaultNamespacePrefix +
                projectPath
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
        foundryExtension.setAndroidExtension(this)
        commonBaseExtensionConfig(true)
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

      foundryExtension.androidHandler.applyTo(project)
    }

    pluginManager.withPlugin("com.android.test") {
      prepareAndroidTestConfigurations("implementation")

      configure<TestAndroidComponentsExtension> {
        commonComponentsExtension.execute(this)

        // namespace is not a property but we can hook into DSL finalizing to set it at the end
        // if the build script didn't declare one prior
        finalizeDsl { testExtension ->
          if (testExtension.namespace == null) {
            testExtension.namespace =
              foundryProperties.defaultNamespacePrefix +
                projectPath
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
      configure<TestExtension> {
        foundryExtension.setAndroidExtension(this)
        commonBaseExtensionConfig(true)
        defaultConfig { targetSdk = sdkVersions.value.targetSdk }
        buildTypes {
          getByName("debug") {
            // For upstream android libraries that just have a single release variant, use that.
            matchingFallbacks += "release"
          }
        }
      }

      foundryExtension.androidHandler.applyTo(project)
    }

    foundryProperties.versions.roborazzi.ifPresent {
      pluginManager.withPlugin("io.github.takahirom.roborazzi") {
        RoborazziTests.configureSubproject(
          project,
          foundryProperties,
          foundryTools.globalConfig.affectedProjects,
          foundryTools::logAvoidedTask,
        )
      }
    }

    foundryProperties.versions.emulatorWtf.ifPresent {
      pluginManager.withPlugin("wtf.emulator.gradle") {
        EmulatorWtfTests.configureSubproject(
          project,
          foundryProperties,
          foundryTools.globalConfig.affectedProjects,
          foundryTools::logAvoidedTask,
        )
      }
    }
  }

  companion object {
    private val CACHED_KSP_ARTIFACTS_FIELD =
      JavaPreCompileTask::class.java.getDeclaredField("kspProcessorArtifacts").apply {
        isAccessible = true
      }

    /** Top-level JVM plugin IDs. Usually only one of these is applied. */
    private val JVM_PLUGINS =
      setOf(
        "application",
        "java",
        "java-library",
        "org.jetbrains.kotlin.jvm",
        "com.android.library",
        "com.android.application",
        "com.android.test",
      )
  }
}

/** A simple context for the current configuration being processed. */
internal data class ConfigurationContext(val project: Project, val configuration: Configuration) {
  val isKaptConfiguration = configuration.name.endsWith("kapt", ignoreCase = true)
}
