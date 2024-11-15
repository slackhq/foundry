/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.gradle.topography

import com.squareup.moshi.JsonClass
import foundry.common.json.JsonTools
import java.nio.file.Path
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

@JsonClass(generateAdapter = true)
public data class ModuleTopography(
  val name: String,
  val gradlePath: String,
  val features: Set<String>,
  val plugins: Set<String>,
) {
  public fun writeJsonTo(property: Provider<out FileSystemLocation>, prettyPrint: Boolean = false) {
    writeJsonTo(property.get().asFile.toPath(), prettyPrint)
  }

  public fun writeJsonTo(path: Path, prettyPrint: Boolean = false) {
    JsonTools.toJson(path, this, prettyPrint)
  }

  public companion object {
    public fun from(provider: Provider<out FileSystemLocation>): ModuleTopography =
      from(provider.get().asFile.toPath())

    public fun from(path: Path): ModuleTopography = JsonTools.fromJson<ModuleTopography>(path)
  }
}

