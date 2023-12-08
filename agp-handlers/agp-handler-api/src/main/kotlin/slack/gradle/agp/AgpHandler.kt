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
package slack.gradle.agp

import com.android.build.api.AndroidPluginVersion
import java.io.File
import org.gradle.api.provider.ProviderFactory

private val NUMBER_REGEX = Regex("d")

/** An interface for handling different AGP versions via (mostly) version-agnostic APIs. */
public interface AgpHandler {
  /** The current AGP version. */
  public val agpVersion: AndroidPluginVersion

  /** Returns the Android SDK directory. This API changed in AGP 8.3.0-alpha05. */
  public fun getAndroidSdkDirectory(projectRootDir: File, providers: ProviderFactory): File

  /**
   * A basic factory interface for creating [AgpHandler] instances. These should be implemented and
   * contributed as a service loader via something like `@AutoService`.
   *
   * **IMPORTANT:** This interface should _not_ use any AGP APIs outside the implementation of
   * [currentVersion] and [create].
   */
  public interface Factory {
    public val minVersion: AndroidPluginVersion
    /** Attempts to get the current AGP version or throws and exception if it cannot. */
    public val currentVersion: AndroidPluginVersion

    public fun create(): AgpHandler
  }
}

/** Returns a new [AndroidPluginVersion] with any preview information stripped. */
public val AndroidPluginVersion.baseVersion: AndroidPluginVersion
  get() = AndroidPluginVersion(major, minor, micro)

/** Returns a computed [AndroidPluginVersion] for the given [input] version string. */
public fun computeAndroidPluginVersion(input: String): AndroidPluginVersion {
  val split = input.split('-')
  require(split.isNotEmpty()) { "Could not parse AGP version from '$input'" }
  val baseVersionNumberStrings = split[0].split('.')
  val (major, minor, micro) =
    Array(3) { index ->
      if (baseVersionNumberStrings.size >= index + 1) {
        baseVersionNumberStrings[index].toInt()
      } else {
        0
      }
    }
  val baseVersion = AndroidPluginVersion(major, minor, micro)
  return if (split.size == 2) {
    // There's a preview here
    val (previewType, number) = split[1].partition { !it.isDigit() }
    when (previewType) {
      "alpha" -> baseVersion.alpha(number.toInt())
      "beta" -> baseVersion.beta(number.toInt())
      "rc" -> baseVersion.rc(number.toInt())
      "dev" -> baseVersion.dev()
      else -> error("Unrecognized preview type '$previewType' with version '$number'")
    }
  } else {
    baseVersion
  }
}
