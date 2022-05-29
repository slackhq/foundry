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

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare
import java.util.Locale
import java.util.Optional
import kotlin.math.max
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.retry
import org.gradle.kotlin.dsl.withType
import slack.gradle.util.synchronousEnvProperty
import slack.stats.ModuleStatsTasks

/**
 * Simple base plugin over [StandardProjectConfigurations]. Eventually functionality from this will
 * be split into more granular plugins.
 *
 * The goal of separating this from [SlackRootPlugin] is project isolation.
 */
internal class SlackBasePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val slackProperties = SlackProperties(target)

    if (!target.isRootProject) {
      StandardProjectConfigurations().applyTo(target)
      target.configureTests(slackProperties)

      // Configure Gradle's test-retry plugin for insights on build scans on CI only
      // Thinking here is that we don't want them to retry when iterating since failure
      // there is somewhat expected.
      if (slackProperties.autoApplyTestRetry && target.isCi) {
        target.apply(plugin = "org.gradle.test-retry")
      }

      if (slackProperties.autoApplySpotless) {
        target.apply(plugin = "com.diffplug.spotless")
      }

      if (slackProperties.autoApplyCacheFix) {
        target.pluginManager.withPlugin("com.android.base") {
          target.apply(plugin = "org.gradle.android.cache-fix")
        }
      }

      if (slackProperties.autoApplyDetekt) {
        // Would be nice to have a single Kotlin hook
        // https://youtrack.jetbrains.com/issue/KT-48008
        target.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
          target.apply(plugin = "io.gitlab.arturbosch.detekt")
        }
        target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
          target.apply(plugin = "io.gitlab.arturbosch.detekt")
        }
      }

      if (slackProperties.autoApplyNullaway) {
        // Always apply the NullAway plugin with errorprone
        target.pluginManager.withPlugin("net.ltgt.errorprone") {
          target.apply(plugin = "net.ltgt.nullaway")
        }
      }

      // Add the experimental EitherNet APIs where it's used
      // TODO make a more general solution for these
      target.configurations.configureEach {
        incoming.afterResolve {
          dependencies.forEach { dependency ->
            if (dependency.name == "eithernet") {
              target.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
                .configureEach {
                  kotlinOptions {
                    @Suppress("SuspiciousCollectionReassignment")
                    freeCompilerArgs += "-opt-in=com.slack.eithernet.ExperimentalEitherNetApi"
                  }
                }
            }
          }
        }
      }

      ModuleStatsTasks.configureSubproject(target)
    }

    // Everything in here applies to all projects
    target.configureSpotless(slackProperties)
    target.configureClasspath(slackProperties)
    val scanApi = ScanApi(target)
    if (scanApi.isAvailable) {
      scanApi.addTestParallelization(target)
      scanApi.addTestSystemProperties(target)
    }
  }

  /** Configures Spotless for formatting. Note we do this per-project for improved performance. */
  private fun Project.configureSpotless(slackProperties: SlackProperties) {
    val isRootProject = this.isRootProject
    pluginManager.withPlugin("com.diffplug.spotless") {
      if (isRootProject) {
        // Pre-declare in root project for better performance and also to work around
        // https://github.com/diffplug/spotless/issues/1213
        configure<SpotlessExtension> { predeclareDeps() }
        configure<SpotlessExtensionPredeclare> {
          format("misc") {
            target("*.md", ".gitignore")
            trimTrailingWhitespace()
            endWithNewline()
          }
          val ktlintVersion = slackProperties.versions.ktlint
          val ktlintUserData = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
          kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion).userData(ktlintUserData)
            trimTrailingWhitespace()
            endWithNewline()
          }
          kotlinGradle {
            target("src/**/*.kts")
            ktlint(ktlintVersion).userData(ktlintUserData)
            trimTrailingWhitespace()
            endWithNewline()
          }
          java {
            target("src/**/*.java")
            googleJavaFormat(slackProperties.versions.gjf).reflowLongStrings()
            trimTrailingWhitespace()
            endWithNewline()
          }
          json {
            target("src/**/*.json", "*.json")
            target("*.json")
            gson().indentWithSpaces(2).version(slackProperties.versions.gson)
          }
        }
      }
    }
  }

  @Suppress("LongMethod", "ComplexMethod")
  private fun Project.configureClasspath(slackProperties: SlackProperties) {
    val hamcrestDep = slackProperties.versions.catalog.findLibrary("testing-hamcrest")
    val checkerDep = slackProperties.versions.catalog.findLibrary("checkerFrameworkQual")
    val isTestProject = "test" in name || "test" in path
    configurations.configureEach {
      configureConfigurationResolutionStrategies(this, isTestProject, hamcrestDep, checkerDep)
    }

    val enableMavenLocal = slackProperties.enableMavenLocal
    val enableSnapshots = slackProperties.enableSnapshots
    // Check if we're running a `dependencyUpdates` task is running by looking for its `-Drevision=`
    // property, which this
    // breaks otherwise.
    val dependencyUpdatesRevision = providers.systemProperty("revision").isPresent
    if (!enableMavenLocal && !enableSnapshots && !dependencyUpdatesRevision) {
      configurations.configureEach { resolutionStrategy { failOnNonReproducibleResolution() } }
    }
  }

  private fun configureConfigurationResolutionStrategies(
    configuration: Configuration,
    isTestProject: Boolean,
    hamcrestDepOptional: Optional<Provider<MinimalExternalModuleDependency>>,
    checkerDepOptional: Optional<Provider<MinimalExternalModuleDependency>>
  ) {
    val configurationName = configuration.name
    val lowercaseName = configurationName.toLowerCase(Locale.US)
    // Hamcrest switched to a single jar starting in 2.1, so exclude the old ones but replace the
    // core one with the
    // new one (as cover for transitive users like junit).
    if (hamcrestDepOptional.isPresent && isTestProject || "test" in lowercaseName) {
      val hamcrestDepProvider = hamcrestDepOptional.get()
      if (hamcrestDepProvider.isPresent) {
        val hamcrestDep = hamcrestDepProvider.get().toString()
        configuration.resolutionStrategy {
          dependencySubstitution {
            substitute(module("org.hamcrest:hamcrest-core")).apply {
              using(module(hamcrestDep))
              because("hamcrest 2.1 removed the core/integration/library artifacts")
            }
            substitute(module("org.hamcrest:hamcrest-integration")).apply {
              using(module(hamcrestDep))
              because("hamcrest 2.1 removed the core/integration/library artifacts")
            }
            substitute(module("org.hamcrest:hamcrest-library")).apply {
              using(module(hamcrestDep))
              because("hamcrest 2.1 removed the core/integration/library artifacts")
            }
          }
        }
      }
    }

    configuration.resolutionStrategy {
      dependencySubstitution {
        // Checker Framework dependencies are all over the place. We clean up some old
        // ones and force to a consolidated version.
        checkerDepOptional.ifPresent { checkerDep ->
          substitute(module("org.checkerframework:checker-compat-qual")).apply {
            using(module(checkerDep.get().toString()))
            because("checker-compat-qual no longer exists and was replaced with just checker-qual")
          }
        }
      }
      checkerDepOptional.ifPresent { checkerDep ->
        val checkerDepVersion = checkerDep.get().versionConstraint.toString()
        eachDependency {
          if (requested.group == "org.checkerframework") {
            useVersion(checkerDepVersion)
            because(
              "Checker Framework dependencies are all over the place, so we force their version to a " +
                "single latest one"
            )
          }
        }
      }
    }
  }

  @Suppress("LongMethod")
  private fun Project.configureTests(slackProperties: SlackProperties) {
    val maxParallel = max(Runtime.getRuntime().availableProcessors() / 2, 1)
    // Create "ciUnitTest" tasks in all subprojects
    apply(plugin = "slack.unit-test")

    // Unit test task configuration
    tasks.withType<Test>().configureEach {
      // Run unit tests in parallel if multiple CPUs are available. Use at most half the available
      // CPUs.
      maxParallelForks = maxParallel

      // Denote flaky failures as <flakyFailure> instead of <failure> in JUnit test XML files
      reports.junitXml.mergeReruns.set(true)

      if (slackProperties.testVerboseLogging) {
        // Add additional logging on Jenkins to help debug hanging or OOM-ing unit tests.
        testLogging {
          showStandardStreams = true
          showStackTraces = true

          // Set options for log level LIFECYCLE
          events("started", "passed", "failed", "skipped")
          setExceptionFormat("short")

          // Setting this to 0 (the default is 2) will display the test executor that each test is
          // running on.
          displayGranularity = 0
        }
      }

      if (isCi) {
        //
        // Trying to improve memory management on CI
        // https://github.com/tinyspeck/slack-android-ng/issues/22005
        //

        // Improve JVM memory behavior in tests to avoid OOMs
        // https://www.royvanrijn.com/blog/2018/05/java-and-docker-memory-limits/
        if (JavaVersion.current().isJava10Compatible) {
          jvmArgs("-XX:+UseContainerSupport")
        } else {
          jvmArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap")
        }

        val workspaceDir =
          when {
            isJenkins -> synchronousEnvProperty("WORKSPACE")
            isActionsCi -> synchronousEnvProperty("GITHUB_WORKSPACE")
            else -> rootProject.projectDir.absolutePath
          }

        // helps when tests leak memory
        @Suppress("MagicNumber") setForkEvery(1000L)

        // Cap JVM args per test
        minHeapSize = "128m"
        maxHeapSize = "1g"
        jvmArgs(
          "-XX:+HeapDumpOnOutOfMemoryError",
          "-XX:+UseGCOverheadLimit",
          "-XX:GCHeapFreeLimit=10",
          "-XX:GCTimeLimit=20",
          "-XX:HeapDumpPath=$workspaceDir/fs_oom_err_pid<pid>.hprof",
          "-XX:OnError=cat $workspaceDir/fs_oom.log",
          "-XX:OnOutOfMemoryError=cat $workspaceDir/fs_oom_err_pid<pid>.hprof",
          "-Xss1m" // Stack size
        )
      }
    }

    if (isCi) {
      pluginManager.withPlugin("org.gradle.test-retry") {
        tasks.withType<Test>().configureEach {
          @Suppress("MagicNumber")
          retry {
            failOnPassedAfterRetry.set(false)
            maxFailures.set(20)
            maxRetries.set(1)
          }
        }
      }
    }
  }
}
