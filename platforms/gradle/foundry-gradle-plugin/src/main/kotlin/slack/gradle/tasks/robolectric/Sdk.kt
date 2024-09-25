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
package slack.gradle.tasks.robolectric

/**
 * Represents a unique build of the Android SDK.
 *
 * Adapted from Robolectric's implementation.
 */
internal sealed class Sdk : Comparable<Sdk> {

  /**
   * Returns the [Android API level](https://source.android.com/setup/start/build-numbers) for this
   * SDK.
   *
   * It must match the version reported by `android.os.Build.VERSION.SDK_INT` provided within.
   */
  abstract val apiLevel: Int

  /**
   * Returns the [Android Version](https://source.android.com/setup/start/build-numbers) for this
   * SDK.
   *
   * It should match the version reported by `android.os.Build.VERSION.RELEASE` provided within.
   *
   * If this is an expensive operation, the implementation should cache the return value.
   */
  abstract val androidVersion: String?

  /**
   * Returns the Android codename for this SDK.
   *
   * It should match the version reported by `android.os.Build.VERSION.CODENAME` provided within.
   *
   * If this is an expensive operation, the implementation should cache the return value.
   */
  abstract val androidCodeName: String?

  /** The actual dependency jar to fetch. */
  abstract fun dependencyJar(): DependencyJar

  /** The actual dependency coordinates to fetch. */
  abstract val dependencyCoordinates: String

  /** Instances of `Sdk` are ordered by the API level they implement. */
  override fun compareTo(other: Sdk): Int {
    return apiLevel - other.apiLevel
  }
}

/**
 * Represents an Android SDK stored at Maven Central.
 *
 * Adapted from Robolectric's implementation.
 */
internal data class DefaultSdk(
  override val apiLevel: Int,
  override val androidVersion: String,
  private val robolectricVersion: String,
  override val androidCodeName: String,
  private val requiredJavaVersion: Int,
  private val iVersion: Int,
) : Sdk() {
  companion object {
    private const val GROUP_ID = "org.robolectric"
    private const val ARTIFACT_ID = "android-all-instrumented"
  }

  private val version = "$androidVersion-robolectric-$robolectricVersion-i$iVersion"

  override fun dependencyJar(): DependencyJar {
    return DependencyJar(GROUP_ID, ARTIFACT_ID, version, null)
  }

  override val dependencyCoordinates = "$GROUP_ID:$ARTIFACT_ID:$version"
}
