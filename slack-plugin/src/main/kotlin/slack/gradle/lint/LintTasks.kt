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

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.capitalizeUS
import slack.gradle.configure
import slack.gradle.getByType

internal object LintTasks {
  private const val GLOBAL_CI_LINT_TASK_NAME = "globalCiLint"
  private const val CI_LINT_TASK_NAME = "ciLint"
  private const val COMPILE_CI_LINT_NAME = "compileCiLint"
  private const val LOG = "SlackLints:"

  fun configureRootProject(project: Project): TaskProvider<Task> =
    project.tasks.register(GLOBAL_CI_LINT_TASK_NAME) {
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      description = "Global lifecycle task to run all dependent lint tasks."
    }

  fun configureSubProject(
    project: Project,
    slackProperties: SlackProperties,
    affectedProjects: Set<String>?,
    commonExtension: CommonExtension<*, *, *, *>?,
    sdkVersions: (() -> SlackProperties.AndroidSdkProperties)?,
  ) {
    project.logger.debug("$LOG Configuring lint tasks for project ${project.path}...")
    // Projects can opt out of creating the task with this property.
    val enabled = slackProperties.ciLintEnabled
    if (!enabled) {
      project.logger.debug("$LOG Skipping creation of \"$CI_LINT_TASK_NAME\" task")
      return
    }

    fun applyCommonLints() {
      slackProperties.versions.bundles.commonLint.ifPresent {
        project.dependencies.add("lintChecks", it)
      }
    }

    val globalTask =
      if (affectedProjects == null || project.path in affectedProjects) {
        project.rootProject.tasks.named(GLOBAL_CI_LINT_TASK_NAME)
      } else {
        val log = "$LOG Skipping ${project.path}:$CI_LINT_TASK_NAME because it is not affected."
        if (slackProperties.debug) {
          project.logger.lifecycle(log)
        } else {
          project.logger.debug(log)
        }
        null
      }

    // We only want to create tasks once, but a project might apply multiple plugins.
    val applied = AtomicBoolean(false)

    if (commonExtension != null) {
      project.logger.debug("$LOG Applying ciLint to Android project")
      applyCommonLints()
      createAndroidCiLintTask(
        project,
        globalTask,
        slackProperties,
        commonExtension,
        sdkVersions!!.invoke()
      )
    } else {
      val javaKotlinLibraryHandler = { _: AppliedPlugin ->
        if (applied.compareAndSet(false, true)) {
          // Enable linting on pure JVM projects
          project.pluginManager.apply("com.android.lint")
          project.configure<Lint> { configureLint(project, slackProperties, null, false) }
          applyCommonLints()
          project.logger.debug("$LOG Creating ciLint task")
          val ciLint =
            project.tasks.register(COMPILE_CI_LINT_NAME) {
              group = LifecycleBasePlugin.VERIFICATION_GROUP
              dependsOn("lint")
            }
          globalTask?.configure { dependsOn(ciLint) }
        }
      }
      project.pluginManager.withPlugin("java-library", javaKotlinLibraryHandler)
      project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm", javaKotlinLibraryHandler)
    }
  }

  private fun createAndroidCiLintTask(
    project: Project,
    globalTask: TaskProvider<*>?,
    slackProperties: SlackProperties,
    extension: CommonExtension<*, *, *, *>,
    sdkVersions: SlackProperties.AndroidSdkProperties,
  ) {
    project.logger.debug("$LOG Configuring android lint tasks. isApp=${extension is AppExtension}")
    extension.lint {
      configureLint(
        project,
        slackProperties,
        sdkVersions,
        checkDependencies = extension is AppExtension
      )
    }

    project.logger.debug("$LOG Creating ciLint task")
    val ciLintTask =
      project.tasks.register(CI_LINT_TASK_NAME) { group = LifecycleBasePlugin.VERIFICATION_GROUP }
    globalTask?.configure { dependsOn(ciLintTask) }

    slackProperties.ciLintVariants?.let { variants ->
      ciLintTask.configure {
        // Even if the task isn't created yet, we can do this by name alone and it will resolve at
        // task configuration time.
        variants.splitToSequence(',').forEach { variant ->
          logger.debug("$LOG Using variant $variant for ciLint task")
          val lintTaskName = "lint${variant.capitalizeUS()}"
          dependsOn(lintTaskName)
        }
      }
      return // nothing else to do!
    }

    val componentsExtension =
      when (extension) {
        is AppExtension -> {
          project.extensions.getByType<ApplicationAndroidComponentsExtension>()
        }
        is LibraryExtension -> {
          project.extensions.getByType<LibraryAndroidComponentsExtension>()
        }
        is TestExtension -> {
          project.extensions.getByType<TestAndroidComponentsExtension>()
        }
        else -> error("No AndroidComponentsExtension found for project ${project.path}")
      }
    componentsExtension.onVariants { variant ->
      val lintTaskName = "lint${variant.name.capitalizeUS()}"
      project.logger.debug("$LOG Adding $lintTaskName to ciLint task")
      ciLintTask.configure {
        // Even if the task isn't created yet, we can do this by name alone and it will resolve at
        // task configuration time.
        dependsOn(lintTaskName)
      }
    }
  }

  private fun Lint.configureLint(
    project: Project,
    slackProperties: SlackProperties,
    androidSdkVersions: SlackProperties.AndroidSdkProperties?,
    checkDependencies: Boolean,
  ) {
    lintConfig = project.rootProject.layout.projectDirectory.file("config/lint/lint.xml").asFile
    sarifReport = true
    // This check is _never_ up to date and makes network requests!
    disable += "NewerVersionAvailable"
    // These store qualified gradle caches in their paths and always change in baselines
    disable += "ObsoleteLintCustomCheck"
    // https://groups.google.com/g/lint-dev/c/Bj0-I1RIPyU/m/mlP5Jpe4AQAJ
    enable += "ImplicitSamInstance"
    error += "ImplicitSamInstance"

    androidSdkVersions?.let { sdkVersions ->
      if (sdkVersions.minSdk >= 28) {
        // Lint doesn't understand AppComponentFactory
        // https://issuetracker.google.com/issues/243267012
        disable += "Instantiatable"
      }
    }

    ignoreWarnings = slackProperties.lintErrorsOnly
    absolutePaths = false
    this.checkDependencies = checkDependencies

    val lintBaselineFile = slackProperties.lintBaselineFileName

    // Lint is weird in that it will generate a new baseline file and fail the build if a new
    // one was generated, even if empty.
    // If we're updating baselines, always take the baseline so that we populate it if absent.
    project.layout.projectDirectory
      .file(lintBaselineFile)
      .asFile
      .takeIf { it.exists() || slackProperties.lintUpdateBaselines }
      ?.let { baseline = it }
  }
}
