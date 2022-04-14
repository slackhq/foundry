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

/**
 * Base interface for interfaces that configure on a per-variant basis and need to expose variant
 * info.
 */
public interface VariantConfiguration {
  /** Returns the Build Type. */
  public val buildType: String?

  /** Returns the list of flavors, or an empty list. */
  public val flavors: List<Pair<String, String>>

  /** Returns the unique variant name. */
  public val name: String
}
