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
package slack.gradle.lint

import com.android.build.api.dsl.Lint
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestPlugin
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.android.build.gradle.internal.lint.VariantInputs
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.withClosure
import slack.gradle.SlackProperties
import slack.gradle.androidExtension
import slack.gradle.androidExtensionNullable
import slack.gradle.capitalizeUS
import slack.gradle.getByType
import slack.gradle.multiplatformExtension

/**
 * Common configuration for Android lint in projects.
 *
 * Much of this file adapts KMP art from AndroidX:
 * https://github.com/androidx/androidx/blob/cb78132cc8288f85e0ff8f4c32ed58a61b05d7d8/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt
 */
internal object LintTasks {
  private const val GLOBAL_CI_LINT_TASK_NAME = "globalCiLint"
  private const val CI_LINT_TASK_NAME = "ciLint"
  private const val COMPILE_CI_LINT_NAME = "compileCiLint"
  private const val LOG = "SlackLints:"

  private fun Project.log(message: String) {
    logger.debug("$LOG $message")
  }

  fun configureRootProject(project: Project): TaskProvider<Task> =
    project.tasks.register(GLOBAL_CI_LINT_TASK_NAME) {
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      description = "Global lifecycle task to run all dependent lint tasks."
    }

  fun configureSubProject(
    project: Project,
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) {
    project.log("Configuring lint tasks for project ${project.path}")
    // Projects can opt out of creating the task with this property.
    val enabled = slackProperties.ciLintEnabled
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
              slackProperties,
              affectedProjects,
              onProjectSkipped
            )
          is TestPlugin -> {
            if (slackProperties.enableLintInAndroidTestProjects) {
              project.configureAndroidProjectForLint(
                slackProperties,
                affectedProjects,
                onProjectSkipped
              )
            }
          }
          // Only configure non-multiplatform Java projects via JavaPlugin. Multiplatform
          // projects targeting Java (e.g. `jvm { withJava() }`) are configured via
          // KotlinBasePlugin.
          is JavaPlugin ->
            if (project.multiplatformExtension == null) {
              project.configureNonAndroidProjectForLint(
                slackProperties,
                affectedProjects,
                onProjectSkipped
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
                slackProperties,
                affectedProjects,
                onProjectSkipped
              )
            }
        }
      }
    project.plugins.all(projectAllAction)
  }

  /** Android Lint configuration entry point for Android projects. */
  private fun Project.configureAndroidProjectForLint(
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) =
    androidExtension.finalizeDsl { extension ->
      log("Applying ciLint to Android project")

      log(
        "Configuring android lint tasks. isApp=${extension is ApplicationAndroidComponentsExtension}"
      )

      log("Creating ciLint task")
      val ciLintTask =
        tasks.register(CI_LINT_TASK_NAME) { group = LifecycleBasePlugin.VERIFICATION_GROUP }

      val ciLintVariants = slackProperties.ciLintVariants
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
        extension.lint,
        ciLintTask,
        slackProperties,
        affectedProjects,
        onProjectSkipped,
        slackProperties.requireAndroidSdkProperties()
      )
    }

  /** Android Lint configuration entry point for non-Android projects. */
  private fun Project.configureNonAndroidProjectForLint(
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
  ) = afterEvaluate {
    // The lint plugin expects certain configurations and source sets which are only added by
    // the Java and Android plugins. If this is a multiplatform project targeting JVM, we'll
    // need to manually create these configurations and source sets based on their multiplatform
    // JVM equivalents.
    addSourceSetsForMultiplatformAfterEvaluate()

    // For Android projects, the Android Gradle Plugin is responsible for applying the lint plugin;
    // however, we need to apply it ourselves for non-Android projects.
    pluginManager.apply("com.android.lint")

    // Create task aliases matching those creates by AGP for Android projects, since those are what
    // developers expect to invoke. Redirect them to the "real" lint task.
    val lintTask = tasks.named("lint")
    tasks.register("lintDebug") {
      dependsOn(lintTask)
      enabled = false
    }
    tasks.register("lintRelease") {
      dependsOn(lintTask)
      enabled = false
    }

    log("Creating ciLint task")
    val ciLint =
      tasks.register(COMPILE_CI_LINT_NAME) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        dependsOn(lintTask)
      }

    // For Android projects, we can run lint configuration last using `DslLifecycle.finalizeDsl`;
    // however, we need to run it using `Project.afterEvaluate` for non-Android projects.
    configureLint(
      extensions.getByType(),
      ciLint,
      slackProperties,
      affectedProjects,
      onProjectSkipped
    )
  }

  private fun Project.configureLint(
    lint: Lint,
    ciLintTask: TaskProvider<*>,
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    onProjectSkipped: (String, String) -> Unit,
    androidSdkVersions: SlackProperties.AndroidSdkProperties? = null,
  ) {
    val isMultiplatform = multiplatformExtension != null

    slackProperties.versions.bundles.commonLint.ifPresent { dependencies.add("lintChecks", it) }

    val globalTask =
      if (affectedProjects == null || path in affectedProjects) {
        rootProject.tasks.named(GLOBAL_CI_LINT_TASK_NAME)
      } else {
        val taskPath = "${path}:$CI_LINT_TASK_NAME"
        val log = "Skipping $taskPath because it is not affected."
        onProjectSkipped(GLOBAL_CI_LINT_TASK_NAME, taskPath)
        if (slackProperties.debug) {
          log(log)
        } else {
          log(log)
        }
        null
      }

    globalTask?.configure { dependsOn(ciLintTask) }

    afterEvaluate { addSourceSetsForAndroidMultiplatformAfterEvaluate() }

    // Lint is configured entirely in finalizeDsl so that individual projects cannot easily
    // disable individual checks in the DSL for any reason.
    lint.apply {
      ignoreWarnings = slackProperties.lintErrorsOnly

      // Run lint on tests. Uses top-level lint.xml to specify checks.
      ignoreTestSources = slackProperties.lintIgnoreTestSources
      checkTestSources = slackProperties.lintCheckTestSources

      textReport = true
      xmlReport = true
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

      fatal += "VisibleForTests"

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

      if (androidExtensionNullable is ApplicationAndroidComponentsExtension) {
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

      baseline = project.layout.projectDirectory.file(slackProperties.lintBaselineFileName).asFile
    }
  }

  /**
   * If the project is using multiplatform, adds configurations and source sets expected by the lint
   * plugin, which allows it to configure itself when running against a non-Android multiplatform
   *
   * The version of lint that we're using does not directly support Kotlin multiplatform, but we can
   * synthesize the necessary configurations and source sets from existing `jvm` configurations and
   * `kotlinSourceSets`, respectively.
   *
   * This method *must* run after evaluation.
   */
  private fun Project.addSourceSetsForMultiplatformAfterEvaluate() {
    val kmpTargets = multiplatformExtension?.targets ?: return

    // Synthesize target configurations based on multiplatform configurations.
    val kmpApiElements = kmpTargets.map { it.apiElementsConfigurationName }
    val kmpRuntimeElements = kmpTargets.map { it.runtimeElementsConfigurationName }
    listOf(kmpRuntimeElements to "runtimeElements", kmpApiElements to "apiElements").forEach {
      (kmpConfigNames, targetConfigName) ->
      configurations.maybeCreate(targetConfigName).apply {
        kmpConfigNames
          .mapNotNull { configName -> configurations.findByName(configName) }
          .forEach { config -> extendsFrom(config) }
      }
    }

    // Synthesize source sets based on multiplatform source sets.
    val javaExtension = project.extensions.getByType<JavaPluginExtension>()
    listOf("main" to "main", "test" to "test").forEach { (kmpCompilationName, targetSourceSetName)
      ->
      javaExtension.sourceSets.maybeCreate(targetSourceSetName).apply {
        kmpTargets
          .mapNotNull { target -> target.compilations.findByName(kmpCompilationName) }
          .flatMap { compilation -> compilation.kotlinSourceSets }
          .flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
          .forEach { srcDirs -> java.srcDirs += srcDirs }
      }
    }
  }

  /**
   * If the project is using multiplatform targeted to Android, adds source sets directly to lint
   * tasks, which allows it to run against Android multiplatform projects.
   *
   * Lint is not aware of MPP, and MPP doesn't configure Lint. There is no built-in API to adjust
   * the default Lint task's sources, so we use this hack to manually add sources for MPP source
   * sets. In the future, with the new Kotlin Project Model
   * (https://youtrack.jetbrains.com/issue/KT-42572) and an AGP / MPP integration plugin, this will
   * no longer be needed. See also b/195329463.
   */
  private fun Project.addSourceSetsForAndroidMultiplatformAfterEvaluate() {
    val multiplatformExtension = multiplatformExtension ?: return
    multiplatformExtension.targets.findByName("android") ?: return

    val androidMain =
      multiplatformExtension.sourceSets.findByName("androidMain")
        ?: throw GradleException("Failed to find source set with name 'androidMain'")

    // Get all the source sets androidMain transitively / directly depends on.
    val dependencySourceSets = androidMain.withClosure(KotlinSourceSet::dependsOn)

    /** Helper function to add the missing sourcesets to this [VariantInputs] */
    fun VariantInputs.addSourceSets() {
      // Each variant has a source provider for the variant (such as debug) and the 'main'
      // variant. The actual files that Lint will run on is both of these providers
      // combined - so we can just add the dependencies to the first we see.
      val sourceProvider = sourceProviders.get().firstOrNull() ?: return
      dependencySourceSets.forEach { sourceSet ->
        sourceProvider.javaDirectories.withChangesAllowed {
          from(sourceSet.kotlin.sourceDirectories)
        }
      }
    }

    // Add the new sources to the lint analysis tasks.
    tasks.withType(AndroidLintAnalysisTask::class.java).configureEach {
      variantInputs.addSourceSets()
    }

    // Also configure the model writing task, so that we don't run into mismatches between
    // analyzed sources in one module and a downstream module
    tasks.withType(LintModelWriterTask::class.java).configureEach { variantInputs.addSourceSets() }
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
    val disallowChanges = this::class.java.getDeclaredField("disallowChanges")
    disallowChanges.isAccessible = true
    disallowChanges.set(this, false)
    block()
    disallowChanges.set(this, true)
  }
}
