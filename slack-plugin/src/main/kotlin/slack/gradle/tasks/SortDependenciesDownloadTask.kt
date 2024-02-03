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
package slack.gradle.tasks

import org.gradle.api.tasks.UntrackedTask

/**
 * Downloads the Sort Dependencies binary from maven central.
 *
 * Usage:
 * ```
 *     ./gradlew updateSortDependencies
 * ```
 */
@UntrackedTask(because = "These are one-off, on-demand download tasks")
internal abstract class SortDependenciesDownloadTask :
  BaseDownloadTask(
    targetName = "Sort Dependencies",
    addExecPrefix = true,
    urlTemplate = { version ->
      "https://repo1.maven.org/maven2/com/squareup/sort-gradle-dependencies-app/$version/sort-gradle-dependencies-app-$version-all.jar"
    },
  ) {
  init {
    description = "Downloads the Sort Dependencies binary from maven central."
  }
}
