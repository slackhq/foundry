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
package slack.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.maven.MavenModule

public abstract class BaseDependencyCheckTask : DefaultTask() {
  @get:Internal public lateinit var configuration: Configuration

  internal abstract fun handleDependencies(dependencies: Map<String, String>)

  init {
    @Suppress("LeakingThis")
    notCompatibleWithConfigurationCache(
      "Pending Gradle 7.5 and https://github.com/gradle/gradle/pull/18729"
    )
  }

  @TaskAction
  internal fun check() {
    val componentIds =
      configuration
        .incoming
        .resolutionResult
        .allDependencies
        .map { it.from.id }
        .filterIsInstance<ModuleComponentIdentifier>()

    val components = fetchComponents(componentIds)
    check(components.isNotEmpty()) { "No runtime versions were found" }

    handleDependencies(components)
  }

  private fun fetchComponents(componentIds: List<ModuleComponentIdentifier>): Map<String, String> {
    return project
      .dependencies
      .createArtifactResolutionQuery()
      .forComponents(componentIds)
      .withArtifacts(MavenModule::class.java)
      .execute()
      .resolvedComponents
      .associate { component ->
        val componentId = component.id as ModuleComponentIdentifier
        val identifier = "${componentId.group}:${componentId.module}"
        identifier to componentId.version
      }
  }
}
