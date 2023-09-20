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

/**
 * A basic factory interface for creating [AgpHandler] instances. These should be implemented and
 * contributed as a service loader via something like `@AutoService`.
 *
 * **IMPORTANT:** This interface should _not_ use any AGP APIs outside of the implementation of
 * [currentVersion] and [create].
 */
public interface AgpHandlerFactory {
  public val minVersion: AndroidPluginVersion
  /** Attempts to get the current AGP version or throws and exception if it cannot. */
  public val currentVersion: AndroidPluginVersion

  public fun create(): AgpHandler
}
