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
import com.android.build.gradle.AppExtension
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.configure
import slack.gradle.safeCapitalize

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
    commonExtension: CommonExtension<*, *, *, *>?,
    sdkVersions: (() -> SlackProperties.AndroidSdkProperties)?,
  ) {
    // Projects can opt out of creating the task with this property.
    val enabled = slackProperties.ciUnitTestEnabled
    if (!enabled) {
      project.logger.debug("$LOG Skipping creation of \"$CI_LINT_TASK_NAME\" task")
      return
    }

    // Apply common lints
    slackProperties.versions.bundles.commonLint.ifPresent {
      project.dependencies.add("lintChecks", it)
    }

    val globalTask = project.rootProject.tasks.named(GLOBAL_CI_LINT_TASK_NAME)

    // We only want to create tasks once, but a project might apply multiple plugins.
    val applied = AtomicBoolean(false)

    if (commonExtension != null) {
      project.logger.debug("$LOG Applying UnitTestPlugin to Android project")
      createAndroidCiLintTask(
        project,
        globalTask,
        slackProperties,
        commonExtension,
        sdkVersions!!.invoke()
      )
    } else {
      val javaKotlinLibraryHandler = { plugin: AppliedPlugin ->
        if (applied.compareAndSet(false, true)) {
          // Enable linting on pure JVM projects
          project.pluginManager.apply("com.android.lint")
          project.configure<Lint> { configureLint(project, slackProperties, null, false) }
          project.logger.debug("$LOG Creating CI lint task")
          val ciLint =
            project.tasks.register(COMPILE_CI_LINT_NAME) {
              group = LifecycleBasePlugin.VERIFICATION_GROUP
              dependsOn("lint")
            }
          globalTask.configure { dependsOn(ciLint) }
        }
      }
      project.pluginManager.withPlugin("java-library", javaKotlinLibraryHandler)
      project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm", javaKotlinLibraryHandler)
    }
  }

  private fun createAndroidCiLintTask(
    project: Project,
    globalTask: TaskProvider<*>,
    slackProperties: SlackProperties,
    extension: CommonExtension<*, *, *, *>,
    sdkVersions: SlackProperties.AndroidSdkProperties,
  ) {
    extension.lint {
      configureLint(
        project,
        slackProperties,
        sdkVersions,
        checkDependencies = extension is AppExtension
      )
    }

    val variant = slackProperties.ciLintVariant
    val lintTaskName =
      if (variant == null) {
        "lint"
      } else {
        "lint${variant.safeCapitalize()}"
      }

    project.logger.debug("$LOG Creating CI lint task for variant '$variant'")
    val ciUnitTest =
      project.tasks.register(CI_LINT_TASK_NAME) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        // Even if the task isn't created yet, we can do this by name alone and it will resolve at
        // task configuration time.
        dependsOn(lintTaskName)
      }
    globalTask.configure { dependsOn(ciUnitTest) }
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
