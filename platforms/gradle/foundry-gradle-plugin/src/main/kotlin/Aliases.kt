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
import foundry.gradle.AndroidHandler
import foundry.gradle.FoundryExtension
import foundry.gradle.FoundryAndroidAppExtension
import foundry.gradle.FoundryAndroidLibraryExtension
import foundry.gradle.findByType
import org.gradle.api.Action
import org.gradle.api.Project

/*
 * This file exists because of a strange behavior in Gradle. If you want to access buildSrc code from the root project's
 * buildscript block, it cannot directly access elements that contain a package name. This is really weird, and
 * hopefully a bug.
 *
 * TODO(zsweers) link the bug!
 */

/**
 * Common entry point for configuring slack-specific bits of projects.
 *
 * ```
 * foundry {
 *   android {
 *     library {
 *       // ...
 *     }
 *   }
 * }
 * ```
 */
public fun Project.foundry(action: Action<FoundryExtension>) {
  extensions.findByType<FoundryExtension>()?.let(action::execute)
    ?: error("Foundry extension not found.")
}

@Deprecated("Use foundry", ReplaceWith("foundry(action)"), level = DeprecationLevel.WARNING)
public fun Project.slack(action: Action<FoundryExtension>) {
  extensions.findByType<FoundryExtension>()?.let(action::execute)
    ?: error("Foundry extension not found.")
}

@Deprecated(
  "Use foundryAndroid",
  ReplaceWith("foundry { android(action) }"),
  level = DeprecationLevel.WARNING,
)
public fun Project.slackAndroid(action: Action<AndroidHandler>) {
  foundry { android(action) }
}

@Deprecated(
  "Use foundryAndroidLibrary",
  ReplaceWith("foundry { android { library(action) } }"),
  level = DeprecationLevel.WARNING,
)
public fun Project.slackAndroidLibrary(action: Action<FoundryAndroidLibraryExtension>) {
  foundry { android { library(action) } }
}

@Deprecated(
  "Use foundry",
  ReplaceWith("foundry { android { app(action) } }"),
  level = DeprecationLevel.WARNING,
)
public fun Project.slackAndroidApp(action: Action<FoundryAndroidAppExtension>) {
  foundry { android { app(action) } }
}
