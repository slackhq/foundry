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

import java.io.File
import kotlin.system.measureTimeMillis
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * For Android SDK 30, the real sources are not published until its release. This is annoying when
 * we want to build against them though (new APIs, etc). To cover for this, we copy over the
 * existing android-29 sources into the android-30 directory as temporary cover with a
 * `slack_patched_marker` file.
 *
 * Once real sources are published, this will just delete the copied jar and let AGP download it
 * automatically.
 */
internal object AndroidSourcesConfigurer {

  internal const val MARKER_FILE_NAME = "slack_patched_marker"

  fun patchSdkSources(requestedSdkVersion: Int, rootProject: Project, latest: Int) {
    val sdkDir = rootProject.slackTools().agpHandler.getAndroidSdkDirectory(rootProject.projectDir)
    patchSdkSources(requestedSdkVersion, sdkDir, rootProject.logger, latest)
  }

  @Suppress("LongMethod")
  fun patchSdkSources(requestedSdkVersion: Int, sdkDir: File, logger: Logger, latest: Int) {
    if (requestedSdkVersion == latest) {
      // Check for our patched marker. If it exists, delete the directory and let AGP download it
      // again.
      val marker = File(sdkDir, "sources/android-$latest/$MARKER_FILE_NAME")
      if (marker.exists()) {
        val sourcesDir = marker.parentFile
        logger.lifecycle(
          "Clearing patched SDK $latest sources dir at $sourcesDir so AGP will " +
            "download the final, real sources. Restart Android Studio + re-sync once after this!"
        )
        sourcesDir.deleteRecursively()
      } else {
        // Nothing to do!
        logger.debug("Skipping Android sources patching")
        return
      }
    } else {
      check((requestedSdkVersion - latest) == 1) {
        "Expected a maximum compile SDK delta of just 1. Cannot patch sources between " +
          "$requestedSdkVersion and latest $latest"
      }
      val requestedSources = File(sdkDir, "sources/android-$requestedSdkVersion")
      if (requestedSources.exists()) {
        // Nothing to do!
        logger.debug("Skipping Android sources patching")
        return
      }
      val latestSources = File(sdkDir, "sources/android-$latest")
      if (!latestSources.exists()) {
        // TODO we could try to download the sdk 29 sources via sdkmanager for them?
        logger.error(
          "Cannot patch android sources jar for requested SDK version " +
            "$requestedSdkVersion. This could be because your SDK has not downloaded it yet." +
            " Android SDK sources will not work in this project until you do download them though." +
            " You can do so manually via the SDK manager in Android studio."
        )
      } else {
        logger.lifecycle(
          "Patching Android sources from $latest as requested SDK version $requestedSdkVersion"
        )
        measureTimeMillis {
            requestedSources.mkdirs()
            latestSources.copyRecursively(requestedSources, overwrite = true)

            // Now create our marker
            File(requestedSources, MARKER_FILE_NAME).apply {
              createNewFile()
              // Write some explanatory text. This isn't used anywhere, just here for curious
              // readers.
              writeText(
                """
            Hello curious reader! This file exists solely as a marker for the
            AndroidSourcesConfigurer util to know if these are patched sdk sources. This sources
            directory is a copy of API $latest sources.

            Did you know if you drive from Houston to LA, El Paso is halfway?
              """
                  .trimIndent()
              )
            }
          }
          .also {
            logger.lifecycle(
              "Successfully patched Android sources for SDK $requestedSdkVersion " +
                "in ${it}ms. Restart Android Studio + re-sync once after this!"
            )
          }
      }
    }
  }
}
