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
package slack.gradle.tasks.detektbaseline

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin
import slack.gradle.tasks.detektbaseline.MergeDetektBaselinesTask.Companion.TASK_NAME

/**
 * Collects all generated baselines and merges them into one global one. Temporary until detekt
 * supports this as a first party tool: https://github.com/arturbosch/detekt/issues/1589.
 *
 * This is only configured to run when the [TASK_NAME] task name is passed in, as detekt currently
 * reuses the `baseline` argument for both the input file to regular detekt tasks and output file of
 * its create tasks. Since we don't want the create tasks to overwrite each other into the same
 * output task, we dynamically configure this as needed. When [TASK_NAME] is specified, all detekt
 * baselines are pointed to an intermediate output file in that project's build directory and the
 * misc "detektBaseline" tasks are wired to have their outputs to be inputs to this task's
 * [baselineFiles].
 *
 * Usage:
 *
 * ./gradlew detektBaseline detektBaselineMerge
 */
@CacheableTask
internal abstract class MergeDetektBaselinesTask : DefaultTask() {

  init {
    description = " Collects all generated detekt baselines and merges them into one global one."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
  }

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  @get:SkipWhenEmpty
  abstract val baselineFiles: ConfigurableFileCollection

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun merge() {
    val merged =
      baselineFiles
        .filter { it.exists() }
        .map { Baseline.load(it.toPath()) }
        .reduce { acc, baseline ->
          acc.copy(
            currentIssues = acc.currentIssues + baseline.currentIssues,
            manuallySuppressedIssues =
              acc.manuallySuppressedIssues + baseline.manuallySuppressedIssues
          )
        }
    val sorted =
      merged.copy(
        currentIssues = merged.currentIssues.toSortedSet(),
        manuallySuppressedIssues = merged.manuallySuppressedIssues.toSortedSet()
      )

    sorted.writeTo(outputFile.asFile.get().toPath())
  }

  companion object {
    const val TASK_NAME = "detektBaselineMerge"
  }
}
