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
package slack.unittest

import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.ciUnitTestAndroidVariant
import slack.gradle.isRootProject

private const val GLOBAL_CI_UNIT_TEST_TASK_NAME = "globalCiUnitTest"
private const val CI_UNIT_TEST_TASK_NAME = "ciUnitTest"
private const val COMPILE_CI_UNIT_TEST_NAME = "compileCiUnitTest"
private const val LOG = "SlackUnitTestPlugin:"

/**
 * This plugin creates a task named "ciUnitTest" in the project it is applied to, which depends on
 * the specific unit test task that we want to run on CI for the project.
 *
 * Instead of running "./gradlew test" on CI (which would run tests on every build variant on every
 * project), we prefer to run unit tests on only one build variant to save time.
 *
 * For an Android application or library, "ciUnitTest" will depend on "testInternalDebugUnitTest"
 * (if the "internal"/"external" product flavors exist), or "testDebugUnitTest". For a Java or
 * Kotlin library, it will depend on "test".
 *
 * For convenience, this also creates a task named `compileCiUnitTest` for just _building_ the
 * tests.
 *
 * Finally, this plugin creates a convenience task named "globalCiUnitTest" in the root project,
 * which depends on all the "ciUnitTest" tasks in the subprojects. This is the task that should be
 * run on CI.
 */
internal class UnitTestPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val globalTask =
      if (project.isRootProject) {
        project.rootProject.tasks.register(GLOBAL_CI_UNIT_TEST_TASK_NAME) {
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          description = "Global lifecycle task to run all ciUnitTest tasks."
        }
      } else {
        project.rootProject.tasks.named(GLOBAL_CI_UNIT_TEST_TASK_NAME)
      }

    // Projects can opt out of creating the task with this property.
    val enabled = SlackProperties(project).ciUnitTestEnabled
    if (!enabled) {
      project.logger.debug("$LOG Skipping creation of \"$CI_UNIT_TEST_TASK_NAME\" task")
      return
    }

    // We only want to create tasks once, but a project might apply multiple plugins.
    val applied = AtomicBoolean(false)

    project.pluginManager.withPlugin("com.android.base") {
      if (applied.compareAndSet(false, true)) {
        project.logger.debug("$LOG Applying UnitTestPlugin to Android project")
        createAndroidCiUnitTestTask(project, globalTask)
      }
    }
    val javaKotlinLibraryHandler = { plugin: AppliedPlugin ->
      if (applied.compareAndSet(false, true)) {
        project.logger.debug("$LOG Applying UnitTestPlugin to ${plugin.name}")
        project.logger.debug("$LOG Creating CI unit test tasks")
        val ciUnitTest =
          project.tasks.register(CI_UNIT_TEST_TASK_NAME) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            dependsOn("test")
          }
        globalTask.configure { dependsOn(ciUnitTest) }
        project.tasks.register(COMPILE_CI_UNIT_TEST_NAME) {
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          dependsOn("testClasses")
        }
      }
    }
    project.pluginManager.withPlugin("java-library", javaKotlinLibraryHandler)
    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm", javaKotlinLibraryHandler)
  }

  private fun createAndroidCiUnitTestTask(project: Project, globalTask: TaskProvider<*>) {
    val variant = project.ciUnitTestAndroidVariant()
    val variantUnitTestTaskName = "test${variant}UnitTest"
    val variantCompileUnitTestTaskName = "compile${variant}UnitTestSources"
    project.logger.debug("$LOG Creating CI unit test tasks for variant '$variant'")
    val ciUnitTest =
      project.tasks.register(CI_UNIT_TEST_TASK_NAME) {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        // Even if the task isn't created yet, we can do this by name alone and it will resolve at
        // task configuration time.
        dependsOn(variantUnitTestTaskName)
      }
    globalTask.configure { dependsOn(ciUnitTest) }
    project.tasks.register(COMPILE_CI_UNIT_TEST_NAME) {
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      // Even if the task isn't created yet, we can do this by name alone and it will resolve at
      // task configuration time.
      dependsOn(variantCompileUnitTestTaskName)
    }
  }
}
