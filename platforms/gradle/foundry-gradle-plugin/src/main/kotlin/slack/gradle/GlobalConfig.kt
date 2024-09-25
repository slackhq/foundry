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
package slack.gradle

import org.gradle.api.Project
import org.gradle.jvm.toolchain.JvmVendorSpec

/** Registry of global configuration info. */
public class GlobalConfig
private constructor(
  internal val kotlinDaemonArgs: List<String>,
  internal val errorProneCheckNamesAsErrors: List<String>,
  internal val affectedProjects: Set<String>?,
  internal val jvmVendor: JvmVendorSpec?,
) {

  internal companion object {
    operator fun invoke(project: Project): GlobalConfig {
      check(project == project.rootProject) { "Project is not root project!" }
      val globalSlackProperties = SlackProperties(project)
      return GlobalConfig(
        kotlinDaemonArgs = globalSlackProperties.kotlinDaemonArgs.split(" "),
        errorProneCheckNamesAsErrors =
          globalSlackProperties.errorProneCheckNamesAsErrors?.split(":").orEmpty(),
        affectedProjects =
          globalSlackProperties.affectedProjects?.let { file ->
            project.logger.lifecycle("[Skippy] Affected projects found in '$file'")
            // Check file existence. This way we can allow specifying the property even if it
            // doesn't exist, which can be more convenient in CI pipelines.
            if (file.exists()) {
              file.readLines().toSet().also { loadedProjects ->
                project.logger.lifecycle("[Skippy] Loaded ${loadedProjects.size} affected projects")
              }
            } else {
              project.logger.lifecycle("[Skippy] Could not load affected projects from '$file'")
              null
            }
          },
        jvmVendor =
          globalSlackProperties.jvmVendor.map(JvmVendorSpec::matching).orNull.also {
            project.logger.debug("[SGP] JVM vendor: $it")
          },
      )
    }
  }
}
