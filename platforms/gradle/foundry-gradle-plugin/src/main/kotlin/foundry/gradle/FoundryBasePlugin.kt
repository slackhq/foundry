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

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessExtensionPredeclare
import com.diffplug.spotless.LineEnding
import foundry.gradle.avoidance.ProjectDependenciesDumpTask
import foundry.gradle.develocity.NoOpBuildScanAdapter
import foundry.gradle.develocity.findAdapter
import foundry.gradle.stats.ModuleStatsTasks
import foundry.gradle.topography.ModuleTopographyTasks
import java.util.Locale
import java.util.Optional
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Provider

/**
 * Simple base plugin over [StandardProjectConfigurations]. Eventually functionality from this will
 * be split into more granular plugins.
 *
 * The goal of separating this from [FoundryRootPlugin] is project isolation.
 */
internal class FoundryBasePlugin @Inject constructor(private val buildFeatures: BuildFeatures) :
  Plugin<Project> {
  override fun apply(target: Project) {
    val foundryToolsProvider = target.foundryToolsProvider()
    val globalConfig = foundryToolsProvider.get().globalConfig
    val globalFoundryProperties = globalConfig.globalFoundryProperties
    val foundryProperties = FoundryProperties.getOrCreate(target, foundryToolsProvider)

    if (foundryProperties.relocateBuildDir && !target.isSyncing) {
      // <root-dir>/../<root-dir-name>-out/<project's relative path>
      val rootDir = target.rootDir
      val rootDirName = rootDir.name
      target.layout.buildDirectory.set(
        rootDir.resolve("../$rootDirName-out/${target.projectDir.toRelativeString(rootDir)}")
      )
    }

    if (!target.isRootProject) {
      val versionCatalog =
        target.getVersionsCatalogOrNull() ?: error("SGP requires use of version catalogs!")
      val foundryExtension =
        target.extensions.create(
          "foundry",
          FoundryExtension::class.java,
          globalFoundryProperties,
          foundryProperties,
          target,
          versionCatalog,
        )
      StandardProjectConfigurations(foundryProperties, versionCatalog, foundryToolsProvider.get())
        .applyTo(target, foundryExtension, foundryProperties)

      // Configure Gradle's test-retry plugin for insights on build scans on CI only
      // Thinking here is that we don't want them to retry when iterating since failure
      // there is somewhat expected.
      if (
        foundryProperties.autoApplyTestRetry &&
          target.isCi &&
          foundryProperties.testRetryPluginType ==
            FoundryProperties.TestRetryPluginType.RETRY_PLUGIN
      ) {
        target.pluginManager.apply("org.gradle.test-retry")
      }

      if (foundryProperties.autoApplyCacheFix) {
        target.pluginManager.withPlugin("com.android.base") {
          target.pluginManager.apply("org.gradle.android.cache-fix")
        }
      }

      if (foundryProperties.autoApplyNullaway) {
        // Always apply the NullAway plugin with errorprone
        target.pluginManager.withPlugin("net.ltgt.errorprone") {
          target.pluginManager.apply("net.ltgt.nullaway")
        }
      }

      val topographyTask =
        ModuleTopographyTasks.configureSubproject(
          target,
          foundryExtension,
          foundryProperties,
          globalConfig.affectedProjects,
        )
      ModuleStatsTasks.configureSubproject(target, foundryProperties, topographyTask)
    } else {
      // Root-only
      ModuleTopographyTasks.configureRootProject(target)
    }

    // Everything in here applies to all projects

    // Project deps generation
    ProjectDependenciesDumpTask.register(target)
    target.configureClasspath(foundryProperties)

    if (!this.buildFeatures.isolatedProjects.requested.getOrElse(false)) {
      // TODO https://github.com/diffplug/spotless/issues/1979
      target.configureSpotless(foundryProperties)
      // TODO not clear how to access the build scan API from a non-root project
      val scanApi = findAdapter(target)
      if (scanApi !is NoOpBuildScanAdapter) {
        scanApi.addTestParallelization(target)
      }
    }
  }

  /** Configures Spotless for formatting. Note we do this per-project for improved performance. */
  private fun Project.configureSpotless(foundryProperties: FoundryProperties) {
    val isRootProject = this.isRootProject
    if (foundryProperties.autoApplySpotless) {
      pluginManager.apply("com.diffplug.spotless")
    } else {
      return
    }
    pluginManager.withPlugin("com.diffplug.spotless") {
      val spotlessFormatters: SpotlessExtension.() -> Unit = {
        format("misc") {
          target("*.md", ".gitignore")
          trimTrailingWhitespace()
          endWithNewline()
        }

        val ktlintVersion = foundryProperties.versions.ktlint
        if (ktlintVersion != null) {
          val ktlintUserData = mapOf("indent_size" to "2", "continuation_indent_size" to "2")
          kotlin { ktlint(ktlintVersion).editorConfigOverride(ktlintUserData) }
          kotlinGradle { ktlint(ktlintVersion).editorConfigOverride(ktlintUserData) }
        }

        val ktfmtVersion = foundryProperties.versions.ktfmt
        if (ktfmtVersion != null) {
          kotlin { ktfmt(ktfmtVersion).googleStyle() }
          kotlinGradle { ktfmt(ktfmtVersion).googleStyle() }
        }

        if (ktlintVersion != null || ktfmtVersion != null) {
          check(!(ktlintVersion != null && ktfmtVersion != null)) {
            "Cannot have both ktlint and ktfmt enabled, please pick one and remove the other from the version catalog!"
          }
          kotlin {
            target("src/**/*.kt")
            trimTrailingWhitespace()
            endWithNewline()
          }
          kotlinGradle {
            target("src/**/*.kts")
            trimTrailingWhitespace()
            endWithNewline()
          }
        }

        foundryProperties.versions.gjf?.let { gjfVersion ->
          java {
            target("src/**/*.java")
            googleJavaFormat(gjfVersion).reflowLongStrings()
            trimTrailingWhitespace()
            endWithNewline()
          }
        }
        foundryProperties.versions.gson?.let { gsonVersion ->
          json {
            target("src/**/*.json", "*.json")
            target("*.json")
            gson().indentWithSpaces(2).version(gsonVersion)
          }
        }
      }
      // Pre-declare in root project for better performance and also to work around
      // https://github.com/diffplug/spotless/issues/1213
      configure<SpotlessExtension> {
        spotlessFormatters()
        if (isRootProject) {
          predeclareDeps()
        }
        // Use platform native endings and don't try to inspect gitattrs
        // https://github.com/diffplug/spotless/issues/1527
        // https://github.com/diffplug/spotless/issues/1644
        lineEndings = LineEnding.PLATFORM_NATIVE
      }
      if (isRootProject) {
        configure<SpotlessExtensionPredeclare> { spotlessFormatters() }
      }
    }
  }

  @Suppress("LongMethod", "ComplexMethod")
  private fun Project.configureClasspath(foundryProperties: FoundryProperties) {
    val catalog = getVersionsCatalog()
    val hamcrestDep = catalog.findLibrary("testing-hamcrest")
    val checkerDep = catalog.findLibrary("checkerFrameworkQual")
    val isTestProject = "test" in name || "test" in path
    configurations.configureEach {
      configureConfigurationResolutionStrategies(this, isTestProject, hamcrestDep, checkerDep)
    }

    val enableMavenLocal = foundryProperties.enableMavenLocal
    val enableSnapshots = foundryProperties.enableSnapshots
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
    checkerDepOptional: Optional<Provider<MinimalExternalModuleDependency>>,
  ) {
    val configurationName = configuration.name
    val lowercaseName = configurationName.lowercase(Locale.US)
    // Hamcrest switched to a single jar starting in 2.1, so exclude the old ones but replace the
    // core one with the
    // new one (as cover for transitive users like junit).
    if (hamcrestDepOptional.isPresent && (isTestProject || "test" in lowercaseName)) {
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
}
