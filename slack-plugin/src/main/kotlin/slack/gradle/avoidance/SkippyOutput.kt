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
package slack.gradle.avoidance

import okio.FileSystem
import okio.Path
import slack.gradle.avoidance.SkippyOutput.Companion.AFFECTED_ANDROID_TEST_PROJECTS_FILE_NAME
import slack.gradle.avoidance.SkippyOutput.Companion.AFFECTED_PROJECTS_FILE_NAME
import slack.gradle.avoidance.SkippyOutput.Companion.FOCUS_SETTINGS_FILE_NAME
import slack.gradle.util.prepareForGradleOutput

public interface SkippyOutput {
  /** The tool-specific directory. */
  public val subDir: Path

  /** The output list of affected projects. */
  public val affectedProjectsFile: Path

  /** The output list of affected androidTest projects. */
  public val affectedAndroidTestProjectsFile: Path

  /** An output .focus file that could be used with the Focus plugin. */
  public val outputFocusFile: Path

  public companion object {
    internal const val AFFECTED_PROJECTS_FILE_NAME: String = "affected_projects.txt"
    internal const val AFFECTED_ANDROID_TEST_PROJECTS_FILE_NAME: String =
      "affected_android_test_projects.txt"
    internal const val FOCUS_SETTINGS_FILE_NAME: String = "focus.settings.gradle"
  }
}

public class SimpleSkippyOutput(public override val subDir: Path) : SkippyOutput {
  public override val affectedProjectsFile: Path = subDir.resolve(AFFECTED_PROJECTS_FILE_NAME)
  public override val affectedAndroidTestProjectsFile: Path =
    subDir.resolve(AFFECTED_ANDROID_TEST_PROJECTS_FILE_NAME)
  public override val outputFocusFile: Path = subDir.resolve(FOCUS_SETTINGS_FILE_NAME)
}

public class WritableSkippyOutput(tool: String, outputDir: Path, fs: FileSystem) : SkippyOutput {
  internal val delegate = SimpleSkippyOutput(outputDir.resolve(tool))

  // Eagerly init the subdir and clear it if exists
  public override val subDir: Path =
    delegate.subDir.apply {
      if (fs.exists(this)) {
        fs.deleteRecursively(this)
      }
    }

  public override val affectedProjectsFile: Path by lazy {
    delegate.affectedProjectsFile.prepareForGradleOutput(fs)
  }

  public override val affectedAndroidTestProjectsFile: Path by lazy {
    delegate.affectedAndroidTestProjectsFile.prepareForGradleOutput(fs)
  }

  public override val outputFocusFile: Path by lazy {
    delegate.outputFocusFile.prepareForGradleOutput(fs)
  }
}
