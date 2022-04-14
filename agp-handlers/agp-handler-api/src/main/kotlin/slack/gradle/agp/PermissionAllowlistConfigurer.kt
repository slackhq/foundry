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

import java.io.File

public interface PermissionAllowlistConfigurer : VariantConfiguration {
  /**
   * Sets a file containing a newline-delimited allowlist of permissions. If set, merged manifest
   * permissions for this variant will have their permissions checked against the allowlist defined
   * in [file].
   */
  public fun setAllowlistFile(file: File)
}
