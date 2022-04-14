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

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Project

/**
 * Easily add tags to build scans iff the API is available. No-op if the build scan API is not
 * available.
 *
 * Copied from gradle-doctor:
 * https://github.com/runningcode/gradle-doctor/blob/04ee63c8fcb3621340214f765b12b96d3f7fd439/doctor-plugin/src/main/java/com/osacky/doctor/internal/ScanApi.kt
 */
internal class ScanApi(project: Project) {
  // Only the root project will have the build scan extension
  private var extension: Any? = project.rootProject.extensions.findByName("buildScan")

  val isAvailable = extension != null

  fun requireExtension(): BuildScanExtension {
    return extension as BuildScanExtension
  }

  val server: String?
    get() {
      return if (!isAvailable) {
        null
      } else {
        requireExtension().server
      }
    }

  fun tag(tag: String) {
    if (isAvailable) {
      requireExtension().tag(tag)
    }
  }

  fun value(name: String, value: String) {
    if (isAvailable) {
      requireExtension().value(name, value)
    }
  }

  fun link(name: String, url: String) {
    if (isAvailable) {
      requireExtension().link(name, url)
    }
  }

  fun background(body: ScanApi.() -> Unit) {
    if (isAvailable) {
      requireExtension().background { body(this@ScanApi) }
    }
  }
}
