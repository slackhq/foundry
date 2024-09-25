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
import foundry.gradle.SlackAndroidAppExtension
import foundry.gradle.SlackAndroidLibraryExtension
import foundry.gradle.SlackExtension
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
 * slack {
 *   android {
 *     library {
 *       // ...
 *     }
 *   }
 * }
 * ```
 */
public fun Project.slack(body: SlackExtension.() -> Unit) {
  extensions.findByType<SlackExtension>()?.let(body) ?: error("Slack extension not found.")
}

/**
 * Common entry point for configuring slack-android-specific bits of projects.
 *
 * ```
 * slackAndroid {
 *   library {
 *     // ...
 *   }
 * }
 * ```
 */
public fun Project.slackAndroid(action: Action<AndroidHandler>) {
  slack { android(action) }
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
public fun Project.slackAndroidLibrary(action: Action<SlackAndroidLibraryExtension>) {
  slack { android { library(action) } }
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
public fun Project.slackAndroidApp(action: Action<SlackAndroidAppExtension>) {
  slack { android { app(action) } }
}
