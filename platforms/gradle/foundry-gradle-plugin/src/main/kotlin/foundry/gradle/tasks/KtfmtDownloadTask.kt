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
package foundry.gradle.tasks

import org.gradle.api.tasks.UntrackedTask

/**
 * Downloads the ktfmt binary from maven central.
 *
 * Usage:
 * ```
 *     ./gradlew updateKtfmt
 * ```
 */
@UntrackedTask(because = "These are one-off, on-demand download tasks")
internal abstract class KtfmtDownloadTask :
  BaseDownloadTask(
    targetName = "ktfmt",
    addExecPrefix = true,
    urlTemplate = { version ->
      "https://repo1.maven.org/maven2/com/facebook/ktfmt/$version/ktfmt-$version-jar-with-dependencies.jar"
    },
  )
