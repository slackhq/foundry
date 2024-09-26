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
package foundry.gradle

import com.gradle.develocity.agent.gradle.adapters.BuildScanAdapter
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.tasks.testing.Test

internal fun BuildFeatures.reportTo(scanApi: BuildScanAdapter) {
  scanApi.value(
    "bf-configuration-cache-requested",
    configurationCache.requested.getOrElse(false).toString(),
  )
  scanApi.value(
    "bf-configuration-cache-active",
    configurationCache.active.getOrElse(false).toString(),
  )
  scanApi.value(
    "bf-isolated-projects-requested",
    isolatedProjects.requested.getOrElse(false).toString(),
  )
  scanApi.value("bf-isolated-projects-active", isolatedProjects.active.getOrElse(false).toString())
}

internal fun BuildScanAdapter.addTestParallelization(project: Project) {
  project.tasks.withType(Test::class.java).configureEach {
    doFirst { value("$identityPath#maxParallelForks", maxParallelForks.toString()) }
  }
}
