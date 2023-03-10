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

import com.gradle.enterprise.gradleplugin.testretry.retry as geRetry
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.retry
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.SlackProperties
import slack.gradle.ciUnitTestAndroidVariant
import slack.gradle.isActionsCi
import slack.gradle.isCi
import slack.gradle.util.synchronousEnvProperty

/**
 * This code creates a task named "ciUnitTest" in the project it is applied to, which depends on the
 * specific unit test task that we want to run on CI for the project.
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
internal object UnitTests {
  private val MAX_PARALLEL = max(Runtime.getRuntime().availableProcessors() / 2, 1)
  private const val GLOBAL_CI_UNIT_TEST_TASK_NAME = "globalCiUnitTest"
  private const val CI_UNIT_TEST_TASK_NAME = "ciUnitTest"
  private const val COMPILE_CI_UNIT_TEST_NAME = "compileCiUnitTest"
  private const val LOG = "SlackUnitTests:"

  fun configureRootProject(project: Project): TaskProvider<Task> =
    project.tasks.register(GLOBAL_CI_UNIT_TEST_TASK_NAME) {
      group = LifecycleBasePlugin.VERIFICATION_GROUP
      description = "Global lifecycle task to run all ciUnitTest tasks."
    }

  fun configureSubproject(project: Project, slackProperties: SlackProperties) {
    val globalTask = project.rootProject.tasks.named(GLOBAL_CI_UNIT_TEST_TASK_NAME)

    // Projects can opt out of creating the task with this property.
    val enabled = slackProperties.ciUnitTestEnabled
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

    configureTestTasks(project, slackProperties)
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

  private fun configureTestTasks(project: Project, slackProperties: SlackProperties) {
    val isCi = project.isCi

    // Unit test task configuration
    project.tasks.withType(Test::class.java).configureEach {
      // Run unit tests in parallel if multiple CPUs are available. Use at most half the available
      // CPUs.
      maxParallelForks = MAX_PARALLEL

      // Denote flaky failures as <flakyFailure> instead of <failure> in JUnit test XML files
      reports.junitXml.mergeReruns.set(true)

      jvmArgs(
        // region compile-testing args
        // TODO would be nice if we could apply this _only_ if compile-testing is on the test
        //  classpath
        // Required for Google compile-testing to work.
        // https://github.com/google/compile-testing/issues/222
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        // endregion

        // region Robolectric args
        // Robolectric 4.9+ requires these --add-opens options.
        // https://github.com/robolectric/robolectric/issues/7456
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        // endregion
      )

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
        jvmArgs("-XX:+UseContainerSupport")

        val workspaceDir =
          when {
            project.isActionsCi -> project.synchronousEnvProperty("GITHUB_WORKSPACE")
            else -> project.rootProject.projectDir.absolutePath
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
      if (slackProperties.testRetryPluginType == SlackProperties.TestRetryPluginType.RETRY_PLUGIN) {
        project.pluginManager.withPlugin("org.gradle.test-retry") {
          project.tasks.withType(Test::class.java).configureEach {
            retry {
              failOnPassedAfterRetry.set(slackProperties.testRetryFailOnPassedAfterRetry)
              maxFailures.set(slackProperties.testRetryMaxFailures)
              maxRetries.set(slackProperties.testRetryMaxRetries)
            }
          }
        }
      } else {
        // TODO eventually expose if GE was enabled in settings via our own settings plugin?
        project.tasks.withType(Test::class.java).configureEach {
          geRetry {
            failOnPassedAfterRetry.set(slackProperties.testRetryFailOnPassedAfterRetry)
            maxFailures.set(slackProperties.testRetryMaxFailures)
            maxRetries.set(slackProperties.testRetryMaxRetries)
          }
        }
      }
    }
  }
}
