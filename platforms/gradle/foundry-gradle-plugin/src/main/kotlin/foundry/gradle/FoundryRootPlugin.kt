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

import com.autonomousapps.DependencyAnalysisExtension
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.osacky.doctor.DoctorExtension
import com.squareup.moshi.adapter
import foundry.gradle.agp.VersionNumber
import foundry.gradle.avoidance.ComputeAffectedProjectsTask
import foundry.gradle.avoidance.GenerateAndroidTestProjectPathsTask
import foundry.gradle.avoidance.GenerateDependencyGraphTask
import foundry.gradle.dependencyrake.MissingIdentifiersAggregatorTask
import foundry.gradle.develocity.NoOpBuildScanAdapter
import foundry.gradle.develocity.findAdapter
import foundry.gradle.lint.DetektTasks
import foundry.gradle.lint.LintTasks
import foundry.gradle.stats.ModuleStatsTasks
import foundry.gradle.tasks.AndroidTestApksTask
import foundry.gradle.tasks.CoreBootstrapTask
import foundry.gradle.tasks.GjfDownloadTask
import foundry.gradle.tasks.InstallCommitHooksTask
import foundry.gradle.tasks.KtLintDownloadTask
import foundry.gradle.tasks.KtfmtDownloadTask
import foundry.gradle.tasks.SortDependenciesDownloadTask
import foundry.gradle.tasks.robolectric.UpdateRobolectricJarsTask
import foundry.gradle.unittest.UnitTests
import foundry.gradle.util.StartParameterProperties
import foundry.gradle.util.Thermals
import foundry.gradle.util.ThermalsData
import foundry.gradle.util.createPropertiesProvider
import foundry.gradle.util.gitExecProvider
import foundry.gradle.util.gitVersionProvider
import foundry.gradle.util.setDisallowChanges
import foundry.gradle.util.sneakyNull
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.jvm.toolchain.JvmVendorSpec
import slack.cli.AppleSiliconCompat

/**
 * A common entry point for Foundry project configuration. This should only be applied once and on
 * the root project, with a full view of the entire project tree.
 */
internal class FoundryRootPlugin @Inject constructor(private val buildFeatures: BuildFeatures) :
  Plugin<Project> {

  override fun apply(project: Project) {
    require(project == project.rootProject) {
      "Slack plugin should only be applied on the root project!"
    }

    AppleSiliconCompat.validate {
      """
        Rosetta detected!
        You are running on an Apple Silicon device but running an x86 JDK. This means your OS is
        running your process in a translated mode (i.e. slower) via Rosetta.

        Please download a native arm64 JDK and remove your existing x86 JDK.

        See: https://github.com/tinyspeck/slack-android-ng/wiki/JDK-Installation-&-JAVA_HOME
      """
        .trimIndent()
    }

    val startParameters = project.gradle.startParameter.projectProperties
    val startParameterProperties =
      project.providers.of(StartParameterProperties::class.java) {
        parameters.properties.setDisallowChanges(startParameters)
      }
    val localProperties =
      project.createPropertiesProvider("local.properties").map {
        it.mapKeys { it.key.toString() }.mapValues { it.value.toString() }
      }
    val startParameterProperty: (String) -> Provider<String> = { key ->
      startParameterProperties.map { sneakyNull(it[key]) }
    }
    val globalLocalProperty: (String) -> Provider<String> = { key ->
      localProperties.map { sneakyNull(it[key]) }
    }
    val foundryProperties =
      FoundryProperties(
        project,
        startParameterProperty = startParameterProperty,
        globalLocalProperty = globalLocalProperty,
      )
    val thermalsLogJsonFile =
      project.layout.buildDirectory.file("outputs/logs/last-build-thermals.json")
    val logThermals = foundryProperties.logThermals
    val enableSkippy = foundryProperties.affectedProjects?.exists() == true
    if (enableSkippy) {
      project.logger.lifecycle(
        "Enabling Skippy using projects in ${foundryProperties.affectedProjects}"
      )
    } else if (foundryProperties.affectedProjects != null) {
      project.logger.lifecycle(
        "Skippy is disabled because file '${foundryProperties.affectedProjects}' does not exist."
      )
    } else {
      project.logger.debug("Skippy is disabled")
    }

    FoundryTools.register(
      project = project,
      logThermals = logThermals,
      enableSkippyDiagnostics = enableSkippy,
      logVerbosely = foundryProperties.verboseLogging,
      thermalsLogJsonFileProvider = thermalsLogJsonFile,
      isConfigurationCacheRequested = buildFeatures.configurationCache.requested,
      startParameterProperties = startParameterProperties,
      globalLocalProperties = localProperties,
    )
    configureRootProject(project, foundryProperties, thermalsLogJsonFile)
  }

  // These checks is a false positive because we have inner lambdas
  @Suppress("ReturnCount", "LongMethod", "ComplexMethod")
  private fun configureRootProject(
    project: Project,
    foundryProperties: FoundryProperties,
    thermalsLogJsonFileProvider: Provider<RegularFile>,
  ) {

    // Check enforced JDK version
    if (foundryProperties.strictJdk) {
      val runtimeVersion =
        project.providers.systemProperty("java.specification.version").get().toInt()
      val jdk = foundryProperties.versions.jdk.get()
      check(jdk == runtimeVersion) {
        """
          Current Java version ($runtimeVersion) does not match the enforced version ($jdk).
          Run ./slackw bootstrap to upgrade and be sure to set your JAVA_HOME to the JDK path it
          prints out.

          If you're seeing this error from Studio, ensure Studio is using JDK $jdk in
          Preferences > Build, Execution, Deployment > Build tools > Gradle > Gradle JVM
        """
          .trimIndent()
      }
    }

    if (!project.isCi) {
      foundryProperties.compileSdkVersion?.substringAfter("-")?.toInt()?.let { compileSdk ->
        val latestCompileSdkWithSources = foundryProperties.latestCompileSdkWithSources(compileSdk)
        AndroidSourcesConfigurer.patchSdkSources(compileSdk, project, latestCompileSdkWithSources)
      }
      project.configureGit(foundryProperties)
    }
    project.configureSlackRootBuildscript(
      foundryProperties.versions.jdk.asProvider(project.providers),
      foundryProperties.jvmVendor.map(JvmVendorSpec::matching).orNull,
    )
    LintTasks.configureRootProject(project)
    DetektTasks.configureRootProject(project, foundryProperties)
    project.configureMisc(foundryProperties)
    UnitTests.configureRootProject(project)
    ModuleStatsTasks.configureRoot(project, foundryProperties)
    val generateDependencyGraphTask =
      GenerateDependencyGraphTask.register(project, foundryProperties)
    val generateAndroidTestProjectsTask = GenerateAndroidTestProjectPathsTask.register(project)
    ComputeAffectedProjectsTask.register(
      rootProject = project,
      foundryProperties = foundryProperties,
      dependencyGraphProvider = generateDependencyGraphTask.flatMap { it.outputFile },
      androidTestProjectPathsProvider = generateAndroidTestProjectsTask.flatMap { it.outputFile },
    )
    // Register robolectric jar downloads if requested
    foundryProperties.versions.robolectric?.let {
      UpdateRobolectricJarsTask.register(project, foundryProperties)
    }

    val scanApi = findAdapter(project)
    if (scanApi !is NoOpBuildScanAdapter) {
      buildFeatures.reportTo(scanApi)
      // It's SUPER important to capture this log File instance separately before passing into the
      // background call below, as this is serialized as an input to that lambda. We also cannot use
      // slackTools() in there anymore as it's already been closed (and will be recreated) in the
      // lambda if we call it there and then be orphaned.
      val thermalsLogJsonFile = thermalsLogJsonFileProvider.get().asFile
      with(scanApi) {
        buildFinished {
          background {
            var thermals: Thermals? = null
            if (thermalsLogJsonFile.exists()) {
              val text = thermalsLogJsonFile.readText()
              if (text.isNotEmpty()) {
                try {
                  thermals =
                    foundry.gradle.util.JsonTools.MOSHI.adapter<Thermals>()
                      .fromJson(thermalsLogJsonFile.readText())
                } catch (e: Exception) {
                  Logging.getLogger("SGP").error("Failed to parse thermals log", e)
                }
              }
            }
            thermals?.run {
              if (this is ThermalsData && wasThrottled) {
                println("🔥 \u001b[33mBuild was thermally throttled!\u001B[0m")
                tag("THROTTLED")
                link("Thermal Performance", chartUrl(urlCharLimit = 100_000))
                value("Throttle - Chart URL", chartUrl(urlCharLimit = 100_000))
                value("Throttle - Lowest", lowest.toString())
                value("Throttle - Average", average.toString())
                value("Throttle - Time throttled", percentThrottled.toString())
                value("Throttle - All", allSpeedLimits.toString())
              }
            }
          }
        }
      }
    }

    // Add ktlint download task
    foundryProperties.versions.ktlint?.let { ktlintVersion ->
      project.tasks.register<KtLintDownloadTask>("updateKtLint") {
        version.setDisallowChanges(ktlintVersion)
        outputFile.setDisallowChanges(project.layout.projectDirectory.file("config/bin/ktlint"))
      }
    }

    // Add GJF download task
    foundryProperties.versions.gjf?.let { gjfVersion ->
      project.tasks.register<GjfDownloadTask>("updateGjf") {
        version.setDisallowChanges(gjfVersion)
        outputFile.setDisallowChanges(project.layout.projectDirectory.file("config/bin/gjf"))
      }
    }

    // Add ktfmt download task
    foundryProperties.versions.ktfmt?.let { ktfmtVersion ->
      project.tasks.register<KtfmtDownloadTask>("updateKtfmt") {
        version.setDisallowChanges(ktfmtVersion)
        outputFile.setDisallowChanges(project.layout.projectDirectory.file("config/bin/ktfmt"))
      }
    }

    // Add sortDependencies download task
    foundryProperties.versions.sortDependencies?.let { sortDependenciesVersion ->
      project.tasks.register<SortDependenciesDownloadTask>("updateSortDependencies") {
        version.setDisallowChanges(sortDependenciesVersion)
        outputFile.setDisallowChanges(
          project.layout.projectDirectory.file("config/bin/sort-dependencies")
        )
      }
    }

    // Dependency analysis plugin for build health
    // Usage: ./gradlew clean buildHealth
    project.pluginManager.withPlugin("com.autonomousapps.dependency-analysis") {
      // Register the missing identifiers aggregator
      if (foundryProperties.enableAnalysisPlugin) {
        MissingIdentifiersAggregatorTask.register(project)
      }
      project.configure<DependencyAnalysisExtension> {
        abi {
          exclusions {
            ignoreGeneratedCode()
            ignoreInternalPackages()
          }
        }
        structure {
          ignoreKtx(true)
          bundle("androidx-camera") {
            primary("androidx.camera:camera-camera2")
            includeGroup("androidx.camera")
          }
          bundle("androidx-paging") {
            primary("androidx.paging:paging-runtime")
            includeGroup("androidx.paging")
          }
          bundle("androidx-lifecycle") {
            primary("androidx.lifecycle:lifecycle-runtime")
            includeGroup("androidx.lifecycle")
            includeGroup("androidx.arch.core")
          }
          bundle("bugsnag") { includeGroup("com.bugsnag") }
          bundle("clikt") {
            primary("com.github.ajalt.clikt:clikt")
            includeGroup("com.github.ajalt.clikt")
          }
          bundle("compose-animation") {
            primary("androidx.compose.animation:animation")
            includeGroup("androidx.compose.animation")
          }
          bundle("compose-foundation") {
            primary("androidx.compose.foundation:foundation")
            includeGroup("androidx.compose.foundation")
          }
          bundle("compose-runtime") {
            primary("androidx.compose.runtime:runtime")
            includeGroup("androidx.compose.runtime")
          }
          bundle("dagger") {
            includeGroup("com.google.dagger")
            includeDependency("javax.inject:javax.inject")
          }
          bundle("exoplayer") { includeGroup("com.google.android.exoplayer") }
          bundle("kotlin-stdlib") { includeGroup("org.jetbrains.kotlin") }
          bundle("leakcanary") {
            primary("com.squareup.leakcanary:leakcanary-android")
            includeGroup("com.squareup.leakcanary")
          }
          bundle("lint-tools") { includeGroup("com.android.tools.lint") }
          bundle("okhttp") {
            primary("com.squareup.okhttp3:okhttp")
            includeGroup("com.squareup.okhttp3")
          }
          bundle("paging") { includeGroup("androidx.paging") }
          bundle("robolectric") { includeGroup("org.robolectric") }
          bundle("rxjava") { includeGroup("io.reactivex.rxjava3") }
        }
      }
    }

    project.pluginManager.withPlugin("com.github.ben-manes.versions") {
      project.tasks.withType(DependencyUpdatesTask::class.java).configureEach {
        // Disallow updating to unstable candidates from stable versions, but do allow suggesting
        // newer unstable
        // candidates if we're already on an unstable version. Note that we won't suggest a newer
        // unstable version if
        // it has a different base version (see doc + example below).
        rejectVersionIf {
          when {
            candidate.moduleIdentifier.toString() == "com.google.guava:guava" -> {
              // Guava has special rules because it adds -jre or -android as a suffix. These are
              // misunderstood by the VersionNumber API as suffixes because it will use their
              // natural order. We just use -jre every time so we reject all -android versions.
              return@rejectVersionIf "-android" in candidate.version
            }
            candidate.group.startsWith("androidx.test") -> {
              // We do allow non-stable test dependencies because they're
              // - Not shipped in prod, we can immediately mitigate if something is wrong
              // - About as reliable in alphas releases as they are in stable.
              //   - Alphas tend to have critical bugfixes introduced by the previous stable 🤦‍
              return@rejectVersionIf false
            }
            candidate.moduleIdentifier.toString() == "com.slack.android:analytics" -> {
              // These use git shas as version suffixes, which aren't reliable for semver checks
              return@rejectVersionIf true
            }
            candidate.moduleIdentifier.toString() == "com.slack.data:client-thrifty" -> {
              // These use an exotic type of semver
              return@rejectVersionIf true
            }
            candidate.group == "com.slack.android.chime" -> {
              // Chime uses unconventional version names, which aren't reliable for semver checks
              return@rejectVersionIf true
            }
            !foundryProperties.versionsPluginAllowUnstable -> {
              val currentIsStable = isStable(currentVersion)
              val candidateIsStable = isStable(candidate.version)
              if (!currentIsStable) {
                if (candidateIsStable) {
                  // Always prefer stable candidates newer than a current unstable version
                  return@rejectVersionIf false
                } else {
                  val candidateVersion = VersionNumber.parse(candidate.version)
                  val currentVersion = VersionNumber.parse(currentVersion)

                  @Suppress("ReplaceCallWithBinaryOperator") // Bug in groovy interop
                  val bothAreUnstable =
                    !candidateVersion.equals(VersionNumber.UNKNOWN) &&
                      !currentVersion.equals(VersionNumber.UNKNOWN)
                  if (bothAreUnstable) {
                    // Both are unstable. Only accept a newer unstable version if it's the same
                    // maj.min.patch. This is so we don't accidentally skip a more stable version in
                    // between.
                    // Example:
                    //   - Current: 1.1.0-alpha01
                    //   - Candidate: 1.2.0-alpha01
                    //   - Other available: 1.1.0-alpha02, 1.1.1
                    // In this case we want 1.1.1 and to reject the newer 1.2.0-alpha01
                    val shouldReject = candidateVersion.baseVersion > currentVersion.baseVersion
                    if (shouldReject) {
                      project.logger.debug(
                        "Rejecting unstable $candidate because its base version " +
                          "is greater than $currentVersion."
                      )
                    }
                    return@rejectVersionIf shouldReject
                  }
                }
              }
              return@rejectVersionIf !candidateIsStable && currentIsStable
            }
            else -> return@rejectVersionIf false
          }
        }
      }
    }

    AndroidTestApksTask.register(project)
  }

  private fun isStable(version: String): Boolean {
    val stableKeyword =
      listOf("RELEASE", "FINAL", "GA").any { version.uppercase(Locale.US).contains(it) }
    return stableKeyword || STABLE_REGEX.matches(version)
  }

  private fun Project.configureGit(foundryProperties: FoundryProperties) {
    // Only run locally
    if (!isCi) {
      foundryProperties.gitHooksFile?.let { hooksPath ->
        // Configure hooks
        providers.gitExecProvider("git", "config", "core.hooksPath", hooksPath.canonicalPath).get()
      }

      val revsFile = foundryProperties.gitIgnoreRevsFile ?: return
      // "git version 2.24.1"
      val gitVersion = providers.gitVersionProvider().get()
      val versionNumber = parseGitVersion(gitVersion)
      @Suppress(
        "ReplaceCallWithBinaryOperator"
      ) // Groovy classes don't seem to export equals() correctly
      when {
        versionNumber.equals(VersionNumber.UNKNOWN) -> {
          logger.lifecycle(
            "Could not infer git env from '$gitVersion'. This can happen if it's the pre-installed " +
              "git version from Apple, please consider using a custom git installation from Homebrew or otherwise."
          )
        }
        versionNumber < MIN_GIT_VERSION_FOR_IGNORE_REVS -> {
          logger.lifecycle(
            "Current git version ($versionNumber) is too low to use " +
              "blame.ignoreRevsFile (2.23+). Please consider updating!"
          )
        }
        else -> {
          logger.debug("Configuring blame.ignoreRevsFile")
          providers
            .gitExecProvider("git", "config", "blame.ignoreRevsFile", file(revsFile).canonicalPath)
            .get()
        }
      }
    }
  }

  private companion object {
    /**
     * Minimum supported version of git to use blame.ignoreRevsFile.
     *
     * See
     * https://www.moxio.com/blog/43/ignoring-bulk-change-commits-with-git-blame#git-2.23-to-the-rescue.
     */
    val MIN_GIT_VERSION_FOR_IGNORE_REVS = VersionNumber.parse("2.23")

    private val STABLE_REGEX = "^[0-9,.v-]+(-android)?(-r)?$".toRegex()
  }
}

private fun Project.configureSlackRootBuildscript(
  jdkProvider: Provider<Int>,
  jvmVendor: JvmVendorSpec?,
) {
  // Only register bootstrap if explicitly requested for now
  if (CoreBootstrapTask.isBootstrapEnabled(this)) {
    CoreBootstrapTask.register(this, jdkProvider, jvmVendor)
  }
  InstallCommitHooksTask.register(this)
}

private fun Project.configureMisc(foundryProperties: FoundryProperties) {
  tasks
    .withType(Delete::class.java)
    .named { it == "clean" }
    .configureEach {
      group = "build"
      delete(rootProject.layout.buildDirectory)
    }

  // Configure gradle doctor
  pluginManager.withPlugin("com.osacky.doctor") {
    @Suppress("MagicNumber")
    configure<DoctorExtension> {
      // We always use G1 because it's faster
      warnWhenNotUsingParallelGC.setDisallowChanges(false)

      /** Throw an exception when multiple Gradle Daemons are running. */
      disallowMultipleDaemons.setDisallowChanges(false)

      // TODO we disable these for now because local development envs are a mess, and these will
      // require more organized
      //  setup. When we do enable them though, they should just be set to `!isCi`

      /** Show a message if the download speed is less than this many megabytes / sec. */
      downloadSpeedWarningThreshold.setDisallowChanges(.5f)
      /**
       * The level at which to warn when a build spends more than this percent garbage collecting.
       */
      GCWarningThreshold.setDisallowChanges(0.10f)
      /**
       * Print a warning to the console if we spend more than this amount of time with Dagger
       * annotation processors.
       */
      daggerThreshold.setDisallowChanges(5000)
      /**
       * By default, Gradle caches test results. This can be dangerous if tests rely on timestamps,
       * dates, or other files which are not declared as inputs.
       *
       * We don't disable caching because we don't see much instability here and disabling them
       * severely impacts CI time.
       */
      enableTestCaching.setDisallowChanges(true)
      /**
       * By default, Gradle treats empty directories as inputs to compilation tasks. This can cause
       * cache misses.
       */
      failOnEmptyDirectories.setDisallowChanges(true)
      /**
       * Do not allow building all apps simultaneously. This is likely not what the user intended.
       */
      allowBuildingAllAndroidAppsSimultaneously.setDisallowChanges(false)

      javaHome {
        /** Ensure that we are using JAVA_HOME to build with this Gradle. */
        ensureJavaHomeMatches.setDisallowChanges(true)

        /** Ensure we have JAVA_HOME set. */
        ensureJavaHomeIsSet.setDisallowChanges(true)

        /** For now, we just give a heavy-handed warning with a link to our wiki! */
        failOnError.setDisallowChanges(provider { foundryProperties.strictJdk })

        /** Link our wiki page in its messages to get developers up and running. */
        extraMessage.setDisallowChanges(
          "https://github.com/tinyspeck/slack-android-ng/wiki/JDK-Installation-&-JAVA_HOME"
        )
      }
    }
  }
}
