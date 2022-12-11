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
package slack.gradle

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import slack.fakes.NoOpLogger
import slack.gradle.util.newTemporaryFolder

class AndroidSourcesConfigurerTest {

  @JvmField @Rule val tmpFolder = newTemporaryFolder()

  @Test
  fun happyPath() {
    // Latest version is the requested, already present
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 30
    val requested = 30
    File(sdk, "sources/android-$latest").apply { mkdirs() }
    val recorded = mutableListOf<String>()
    val recordingLogger =
      object : NoOpLogger() {
        override fun debug(message: String) {
          recorded.add(message)
        }
      }

    AndroidSourcesConfigurer.patchSdkSources(requested, sdk, recordingLogger, latest)
    assertThat(recorded.first()).contains("Skipping Android sources patching")
  }

  @Test
  fun upgradeExistingToLatest() {
    // Latest is the requested, patched is present
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 30
    val requested = 30
    val sourcesDir = File(sdk, "sources/android-$latest").apply { mkdirs() }
    // Create the marker file
    val markerFile =
      File(sourcesDir, AndroidSourcesConfigurer.MARKER_FILE_NAME).apply { createNewFile() }
    val recorded = mutableListOf<String>()
    val recordingLogger =
      object : NoOpLogger() {
        override fun lifecycle(message: String) {
          println("Lifecycle $message")
          recorded.add(message)
        }
      }

    AndroidSourcesConfigurer.patchSdkSources(requested, sdk, recordingLogger, latest)
    assertThat(markerFile.exists()).isFalse()
    assertThat(recorded.first()).contains("Clearing patched SDK")
  }

  @Test
  fun patchOldSources() {
    // Latest is the prior version, patch it
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 29
    val requested = 30
    File(sdk, "sources/android-$latest").apply { mkdirs() }
    val patchedSourcesDir = File(sdk, "sources/android-$requested")
    val markerFile = File(patchedSourcesDir, AndroidSourcesConfigurer.MARKER_FILE_NAME)
    val recorded = mutableListOf<String>()
    val recordingLogger =
      object : NoOpLogger() {
        override fun lifecycle(message: String) {
          recorded.add(message)
        }
      }

    AndroidSourcesConfigurer.patchSdkSources(requested, sdk, recordingLogger, latest)
    assertThat(patchedSourcesDir.exists()).isTrue()
    assertThat(markerFile.exists()).isTrue()
    assertThat(recorded.first()).contains("Patching Android sources")
  }

  @Test
  fun alreadyPatched() {
    // Latest is the prior version, patch it
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 29
    val requested = 30
    File(sdk, "sources/android-$latest").apply { mkdirs() }
    val patchedSourcesDir = File(sdk, "sources/android-$requested").apply { mkdirs() }
    // Create the marker file
    val markerFile =
      File(patchedSourcesDir, AndroidSourcesConfigurer.MARKER_FILE_NAME).apply { createNewFile() }
    val recorded = mutableListOf<String>()
    val recordingLogger =
      object : NoOpLogger() {
        override fun debug(message: String) {
          recorded.add(message)
        }
      }

    AndroidSourcesConfigurer.patchSdkSources(requested, sdk, recordingLogger, latest)
    assertThat(patchedSourcesDir.exists()).isTrue()
    assertThat(markerFile.exists()).isTrue()
    assertThat(recorded.first()).contains("Skipping Android sources patching")
  }

  @Test
  fun invalidPatchDelta() {
    // We can't patch across more than one version
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 28
    val requested = 30
    try {
      AndroidSourcesConfigurer.patchSdkSources(requested, sdk, NoOpLogger(), latest)
      fail()
    } catch (e: IllegalStateException) {
      assertThat(e).hasMessageThat().contains("compile SDK delta")
    }
  }

  @Test
  fun missingSdkError() {
    // We can't patch without the previous being available
    val sdk = tmpFolder.newFolder("sdk")
    val latest = 29
    val requested = 30
    val patchedSourcesDir = File(sdk, "sources/android-$requested")
    val markerFile = File(patchedSourcesDir, AndroidSourcesConfigurer.MARKER_FILE_NAME)
    val recorded = mutableListOf<String>()
    val recordingLogger =
      object : NoOpLogger() {
        override fun error(message: String) {
          recorded.add(message)
        }
      }

    AndroidSourcesConfigurer.patchSdkSources(requested, sdk, recordingLogger, latest)
    assertThat(patchedSourcesDir.exists()).isFalse()
    assertThat(markerFile.exists()).isFalse()
    assertThat(recorded.first()).contains("Cannot patch android sources jar")
  }
}
