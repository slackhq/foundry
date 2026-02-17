/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
package foundry.gradle.lint

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestPlugin
import foundry.gradle.FoundryProperties
import foundry.gradle.androidExtension
import foundry.gradle.androidExtensionNullable
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.artifacts.Resolver
import foundry.gradle.avoidance.SkippyArtifacts
import foundry.gradle.capitalizeUS
import foundry.gradle.getByType
import foundry.gradle.multiplatformExtension
import foundry.gradle.register
import foundry.gradle.tasks.SimpleFileProducerTask
import foundry.gradle.tasks.SimpleFilesConsumerTask
import foundry.gradle.tasks.publish
import java.lang.reflect.Field
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

/**
 * Common configuration for Android lint in projects.
 *
 * Much of this file adapts KMP art from AndroidX:
 * https://github.com/androidx/androidx/blob/cb78132cc8288f85e0ff8f4c32ed58a61b05d7d8/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt
 */
internal object LintTasks {
  private const val GLOBAL_CI_LINT_TASK_NAME = "globalCiLint"
  private const val CI_LINT_TASK_NAME = "ciLint"
  private const val LOG = "FoundryLints:"

  private fun Project.log(message: String) {
    logger.debug("$LOG $message")
  }

  fun configureRootProject(project: Project) {
    val resolver = Resolver.interProjectResolver(project, FoundryArtifact.SkippyLint)
    SimpleFilesConsumerTask.registerOrConfigure(
      project,
      GLOBAL_CI_LINT_TASK_NAME,
      description = "Global lifecycle task to run all ciUnitTest tasks.",
      inputFiles = resolver.artifactView(),
    )
  }

  fun configureSubProject(
    project: Project,
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) {
    project.log("Configuring lint tasks for project ${project.path}")
    // Projects can opt out of creating the task with this property.
    val enabled = foundryProperties.ciLintEnabled
    if (!enabled) {
      project.log("Skipping creation of \"$CI_LINT_TASK_NAME\" task")
      return
    }
    val projectAllAction =
      Action<Plugin<*>> {
        when (this) {
          is AppPlugin,
          is LibraryPlugin ->
            project.configureAndroidProjectForLint(
              foundryProperties,
              affectedProjects,
              onProjectSkipped,
            )

          is TestPlugin -> {
            if (foundryProperties.enableLintInAndroidTestProjects) {
              project.configureAndroidProjectForLint(
                foundryProperties,
                affectedProjects,
                onProjectSkipped,
              )
            }
          }
          // Only configure non-multiplatform Java projects via JavaPlugin. Multiplatform
          // projects targeting Java (e.g. `jvm { withJava() }`) are configured via
          // KotlinBasePlugin.
          is JavaPlugin ->
            if (project.multiplatformExtension == null) {
              project.configureNonAndroidProjectForLint(
                foundryProperties,
                affectedProjects,
                onProjectSkipped,
              )
            }
          // Only configure non-Android multiplatform projects via KotlinBasePlugin.
          // Multiplatform projects targeting Android (e.g. `id("com.android.library")`) are
          // configured via AppPlugin or LibraryPlugin.
          is KotlinBasePlugin ->
            if (
              project.multiplatformExtension != null &&
                !project.plugins.hasPlugin(AppPlugin::class.java) &&
                !project.plugins.hasPlugin(LibraryPlugin::class.java)
            ) {
              project.configureNonAndroidProjectForLint(
                foundryProperties,
                affectedProjects,
                onProjectSkipped,
              )
            }
        }
      }
    project.plugins.configureEach(projectAllAction)
  }

  /** Android Lint configuration entry point for Android projects. */
  private fun Project.configureAndroidProjectForLint(
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) =
    androidExtension.finalizeDsl { extension ->
      val typedExtension = extension as CommonExtension
      foundryProperties.lintVersionOverride?.let {
        val lintVersion = foundryProperties.versions.lookupVersion(it)
        typedExtension.experimentalProperties["android.experimental.lint.version"] = lintVersion
      }

      log("Applying ciLint to Android project")

      log(
        "Configuring android lint tasks. isApp=${extension is ApplicationAndroidComponentsExtension}"
      )

      log("Creating ciLint task")
      val ciLintTask = createCiLintTask(project)

      val ciLintVariants = foundryProperties.ciLintVariants
      if (ciLintVariants != null) {
        ciLintTask.configure {
          // Even if the task isn't created yet, we can do this by name alone and it will resolve at
          // task configuration time.
          ciLintVariants.splitToSequence(',').forEach { variant ->
            logger.debug("Using variant $variant for ciLint task")
            val lintTaskName = "lint${variant.capitalizeUS()}"
            dependsOn(lintTaskName)
          }
        }
      } else {
        androidExtension.onVariants { variant ->
          val lintTaskName = "lint${variant.name.capitalizeUS()}"
          log("Adding $lintTaskName to ciLint task")
          ciLintTask.configure {
            // Even if the task isn't created yet, we can do this by name alone and it will resolve
            // at task configuration time.
            dependsOn(lintTaskName)
          }
        }
      }

      configureLint(
        typedExtension.lint,
        ciLintTask,
        foundryProperties,
        affectedProjects,
        onProjectSkipped,
        foundryProperties.requireAndroidSdkProperties(),
      )
    }

  /** Android Lint configuration entry point for non-Android projects. */
  private fun Project.configureNonAndroidProjectForLint(
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) = afterEvaluate {
    // For Android projects, the Android Gradle Plugin is responsible for applying the lint plugin;
    // however, we need to apply it ourselves for non-Android projects.
    pluginManager.apply("com.android.lint")

    // Create task aliases matching those creates by AGP for Android projects, since those are what
    // developers expect to invoke. Redirect them to the "real" lint task.
    val lintTask = tasks.named("lint")
    tasks.register<DefaultTask>("lintDebug") {
      dependsOn(lintTask)
      enabled = false
    }
    tasks.register<DefaultTask>("lintRelease") {
      dependsOn(lintTask)
      enabled = false
    }

    log("Creating ciLint task")
    val ciLint = createCiLintTask(project) { dependsOn(lintTask) }

    // For Android projects, we can run lint configuration last using `DslLifecycle.finalizeDsl`;
    // however, we need to run it using `Project.afterEvaluate` for non-Android projects.
    configureLint(
      extensions.getByType(),
      ciLint,
      foundryProperties,
      affectedProjects,
      onProjectSkipped,
    )
  }

  private fun Project.configureLint(
    lint: Lint,
    ciLint: TaskProvider<SimpleFileProducerTask>,
    foundryProperties: FoundryProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
    androidSdkVersions: FoundryProperties.AndroidSdkProperties? = null,
  ) {
    val isMultiplatform = multiplatformExtension != null

    foundryProperties.versions.bundles.commonLint.ifPresent { dependencies.add("lintChecks", it) }

    if (affectedProjects != null && path !in affectedProjects) {
      val taskPath = "${path}:$CI_LINT_TASK_NAME"
      val log = "Skipping $taskPath because it is not affected."
      onProjectSkipped(GLOBAL_CI_LINT_TASK_NAME, taskPath)
      if (foundryProperties.debug) {
        log(log)
      } else {
        log(log)
      }
      SkippyArtifacts.publishSkippedTask(project, CI_LINT_TASK_NAME)
    } else {
      val publisher = Publisher.interProjectPublisher(project, FoundryArtifact.SkippyLint)
      publisher.publish(ciLint)
    }

    // Lint is configured entirely in finalizeDsl so that individual projects cannot easily
    // disable individual checks in the DSL for any reason.
    lint.apply {
      ignoreWarnings = foundryProperties.lintErrorsOnly

      // Run lint on tests. Uses top-level lint.xml to specify checks.
      ignoreTestSources = foundryProperties.lintIgnoreTestSources
      checkTestSources = foundryProperties.lintCheckTestSources

      textReport = true
      xmlReport = false
      htmlReport = true
      sarifReport = true

      // Format text output for convenience.
      explainIssues = true
      noLines = false
      quiet = true

      // Turn off the "lintVital<buildVariant>" tasks that are included by default in release
      // builds.
      // We run lint separately from release builds on CI, which makes these tasks redundant.
      checkReleaseBuilds = false

      // We run lint on each library, so we don't want transitive checking of each dependency
      checkDependencies = false

      if (!foundryProperties.isTestLibrary) {
        fatal += "VisibleForTests"
      }

      if (isMultiplatform) {
        // Disable classfile-based checks because lint cannot find the class files for
        // multiplatform projects and `SourceSet.java.classesDirectory` is not configurable.
        // This is not ideal, but it's better than having no lint checks at all.
        disable += "LintError"
      }

      // Disable dependency checks that suggest to change them. We want libraries to be
      // intentional with their dependency version bumps.
      disable += "KtxExtensionAvailable"
      disable += "GradleDependency"

      // These store qualified gradle caches in their paths and always change in baselines
      disable += "ObsoleteLintCustomCheck"

      // Explicitly disable StopShip check (see b/244617216)
      disable += "StopShip"

      fatal += "Assert"
      fatal += "NewApi"
      fatal += "ObsoleteSdkInt"

      // Too many Kotlin features require synthetic accessors - we want to rely on R8 to
      // remove these accessors
      disable += "SyntheticAccessor"

      // Lint doesn't like abstract classes extending Initializer
      // see https://issuetracker.google.com/issues/265962219
      disable += "EnsureInitializerMetadata"

      if (
        androidExtensionNullable is ApplicationAndroidComponentsExtension &&
          !foundryProperties.isTestLibrary
      ) {
        checkDependencies = true
      }

      androidSdkVersions?.let { sdkVersions ->
        if (sdkVersions.minSdk >= 28) {
          // Lint doesn't understand AppComponentFactory
          // https://issuetracker.google.com/issues/243267012
          disable += "Instantiatable"
        }
      }

      lintConfig = rootProject.layout.projectDirectory.file("config/lint/lint.xml").asFile

      baseline =
        foundryProperties.lintBaselineFileName?.let {
          project.layout.projectDirectory.file(it).asFile
        }
    }
  }

  /**
   * Lint uses [ConfigurableFileCollection.disallowChanges] during initialization, which prevents
   * modifying the file collection separately (there is no time to configure it before AGP has
   * initialized and disallowed changes). This uses reflection to temporarily allow changes, and
   * apply [block].
   */
  private fun ConfigurableFileCollection.withChangesAllowed(
    block: ConfigurableFileCollection.() -> Unit
  ) {
    // The `disallowChanges` field is defined on `ConfigurableFileCollection` prior to Gradle 8.6
    // and on the inner ValueState in later versions.
    val (target, field) =
      findDeclaredFieldOnClass("disallowChanges")?.let { field -> Pair(this, field) }
        ?: findDeclaredFieldOnClass("valueState")?.let { valueState ->
          valueState.isAccessible = true
          val target = valueState.get(this)
          target.findDeclaredFieldOnClass("disallowChanges")?.let { field ->
            // For Gradle 8.6 and later,
            Pair(target, field)
          }
        }
        ?: throw NoSuchFieldException()

    // Make the field temporarily accessible while we run the `block`.
    field.isAccessible = true
    field.set(target, false)
    block()
    field.set(target, true)
  }

  private fun Any.findDeclaredFieldOnClass(name: String): Field? {
    return try {
      this::class.java.getDeclaredField(name)
    } catch (_: NoSuchFieldException) {
      null
    }
  }

  private fun createCiLintTask(
    project: Project,
    action: Action<SimpleFileProducerTask> = Action {},
  ): TaskProvider<SimpleFileProducerTask> {
    project.logger.debug("Creating ciLint task: ${project.path}:$CI_LINT_TASK_NAME")
    val task =
      SimpleFileProducerTask.registerOrConfigure(
        project,
        CI_LINT_TASK_NAME,
        group = LifecycleBasePlugin.VERIFICATION_GROUP,
        description = "Lifecycle task to run all lint tasks on this project.",
        action = action,
      )
    return task
  }
}
