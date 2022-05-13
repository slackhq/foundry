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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import slack.gradle.getVersionsCatalog
import slack.gradle.safeCapitalize

/**
 * A task that checks expected versions (from [mappedIdentifiersToVersions]) against runtime
 * versions found in [identifiersToVersions].
 *
 * This is important to check for some dependencies pulling newer versions unexpectedly.
 */
@CacheableTask
public abstract class CheckDependencyVersionsTask : BaseDependencyCheckTask() {

  @get:Input public abstract val mappedIdentifiersToVersions: MapProperty<String, String>

  init {
    group = "verification"
  }

  override fun handleDependencies(identifiersToVersions: Map<String, String>) {
    val identifierMap =
      mappedIdentifiersToVersions.get().filterValues {
        // Blank versions mean it's managed by a BOM and we don't need to check it here
        it.isNotBlank()
      }

    val actualVersions = identifiersToVersions.filterKeys { it in identifierMap }

    val issues =
      mutableListOf<String>().apply {
        mappedIdentifiersToVersions.get().forEach { (identifier, declared) ->
          val actual = actualVersions[identifier] ?: return@forEach
          if (actual != declared) {
            this += "$identifier - declared $declared - actual $actual"
          }
        }
      }

    if (issues.isNotEmpty()) {
      throw GradleException(
        "Mismatched dependency versions! Please update their versions in" +
          " libs.versions.toml to match their resolved versions.\n\n" +
          "${issues.joinToString("\n")}\n\nIf you just updated a library, it may pull " +
          "in a newer version of a dependency that we separately specify in libs.versions.toml. " +
          "Keeping the versions in libs.versions.toml in sync with the final resolved versions " +
          "makes it easier to see what version of a library we depend on at a glance."
      )
    }
  }

  public companion object {
    public fun register(
      project: Project,
      name: String,
      configuration: Configuration
    ): TaskProvider<CheckDependencyVersionsTask> {
      return project.tasks.register<CheckDependencyVersionsTask>(
        "check${name.safeCapitalize()}Versions"
      ) {
        configureIdentifiersToVersions(configuration)
        val catalog = project.getVersionsCatalog()
        this.mappedIdentifiersToVersions.putAll(
          project.provider {
            catalog.libraryAliases.associate {
              val dep = catalog.findLibrary(it).get().get()
              dep.module.toString() to dep.versionConstraint.toString()
            }
          }
        )
      }
    }
  }
}
