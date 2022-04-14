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
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.ciUnitTestAndroidVariant

private const val TASK_NAME = "ciUnitTest"
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
 */
internal class UnitTestPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    // Projects can opt out of creating the task with this property.
    val enabled = SlackProperties(project).ciUnitTestEnabled
    if (!enabled) {
      project.logger.debug("$LOG Skipping creation of \"$TASK_NAME\" task")
      return
    }

    // We only want to create tasks once, but a project might apply multiple plugins.
    val applied = AtomicBoolean(false)

    project.pluginManager.withPlugin("com.android.application") {
      if (applied.compareAndSet(false, true)) {
        project.logger.debug("$LOG Applying UnitTestPlugin to android application")
        createAndroidCiUnitTestTask(project)
      }
    }
    project.pluginManager.withPlugin("com.android.library") {
      if (applied.compareAndSet(false, true)) {
        project.logger.debug("$LOG Applying UnitTestPlugin to android library")
        createAndroidCiUnitTestTask(project)
      }
    }
    val javaKotlinLibraryHandler = { plugin: AppliedPlugin ->
      if (applied.compareAndSet(false, true)) {
        project.logger.debug("$LOG Applying UnitTestPlugin to ${plugin.name}")
        project.logger.debug("$LOG Creating $TASK_NAME that depends on test")
        project.tasks.register(TASK_NAME) {
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          dependsOn("test")
        }
      }
    }
    project.pluginManager.withPlugin("java-library", javaKotlinLibraryHandler)
    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm", javaKotlinLibraryHandler)
  }

  private fun createAndroidCiUnitTestTask(project: Project) {
    val variantUnitTestTaskName = "test${project.ciUnitTestAndroidVariant()}UnitTest"
    project.logger.debug("$LOG Creating $TASK_NAME that depends on $variantUnitTestTaskName")
    project.tasks.register(TASK_NAME) {
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      // Even if the task isn't created yet, we can do this by name alone and it will resolve at
      // task configuration time.
      dependsOn(variantUnitTestTaskName)
    }
  }
}
