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
import foundry.gradle.SlackAndroidAppExtension
import foundry.gradle.SlackAndroidLibraryExtension
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
public fun Project.foundry(body: FoundryExtension.() -> Unit) {
  extensions.findByType<FoundryExtension>()?.let(body) ?: error("Foundry extension not found.")
}

@Deprecated("Use foundry", ReplaceWith("foundry(body)"), level = DeprecationLevel.ERROR)
public fun Project.slack(body: FoundryExtension.() -> Unit) {
  extensions.findByType<FoundryExtension>()?.let(body) ?: error("Foundry extension not found.")
}

/**
 * Common entry point for configuring slack-android-specific bits of projects.
 *
 * ```
 * foundryAndroid {
 *   library {
 *     // ...
 *   }
 * }
 * ```
 */
public fun Project.foundryAndroid(action: Action<AndroidHandler>) {
  foundry { android(action) }
}

@Deprecated(
  "Use foundryAndroid",
  ReplaceWith("foundryAndroid(body)"),
  level = DeprecationLevel.ERROR,
)
public fun Project.slackAndroid(action: Action<AndroidHandler>) {
  foundryAndroid(action)
}

/**
 * Common entry point for configuring slack-android-library-specific bits of projects.
 *
 * ```
 * androidLibrary {
 *   // ...
 * }
 * ```
 */
public fun Project.foundryAndroidLibrary(action: Action<SlackAndroidLibraryExtension>) {
  foundry { android { library(action) } }
}

@Deprecated(
  "Use foundryAndroidLibrary",
  ReplaceWith("foundryAndroidLibrary(body)"),
  level = DeprecationLevel.ERROR,
)
public fun Project.slackAndroidLibrary(action: Action<SlackAndroidLibraryExtension>) {
  foundryAndroidLibrary(action)
}

/**
 * Common entry point for configuring slack-android-library-specific bits of projects.
 *
 * ```
 * androidApp {
 *   // ...
 * }
 * ```
 */
public fun Project.foundryAndroidApp(action: Action<SlackAndroidAppExtension>) {
  foundry { android { app(action) } }
}

@Deprecated(
  "Use foundryAndroidApp",
  ReplaceWith("foundryAndroidApp(body)"),
  level = DeprecationLevel.ERROR,
)
public fun Project.slackAndroidApp(action: Action<SlackAndroidAppExtension>) {
  foundryAndroidApp(action)
}
