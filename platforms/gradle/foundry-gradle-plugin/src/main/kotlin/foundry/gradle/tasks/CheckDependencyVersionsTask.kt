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
package foundry.gradle.tasks

import foundry.gradle.capitalizeUS
import foundry.gradle.getVersionsCatalog
import foundry.gradle.register
import foundry.gradle.util.setDisallowChanges
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

/**
 * A task that checks expected versions (from [mappedIdentifiersToVersions]) against runtime
 * versions found in [identifiersToVersions].
 *
 * This is important to check for some dependencies pulling newer versions unexpectedly.
 */
@CacheableTask
public abstract class CheckDependencyVersionsTask : BaseDependencyCheckTask() {

  @get:Input public abstract val mappedIdentifiersToVersions: MapProperty<String, String>

  // Only present for cacheability
  @get:OutputFile public abstract val outputFile: RegularFileProperty

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

    val issuesString = issues.joinToString("\n")
    if (issues.isNotEmpty()) {
      throw GradleException(
        "Mismatched dependency versions! Please update their versions in" +
          " libs.versions.toml to match their resolved versions.\n\n" +
          "${issuesString}\n\nIf you just updated a library, it may pull " +
          "in a newer version of a dependency that we separately specify in libs.versions.toml. " +
          "Keeping the versions in libs.versions.toml in sync with the final resolved versions " +
          "makes it easier to see what version of a library we depend on at a glance."
      )
    }

    outputFile.asFile.get().writeText("Issues:\n$issuesString")
  }

  public companion object {
    public fun register(
      project: Project,
      name: String,
      configuration: Configuration,
    ): TaskProvider<CheckDependencyVersionsTask> {
      return project.tasks.register<CheckDependencyVersionsTask>(
        "check${name.capitalizeUS()}Versions"
      ) {
        configureIdentifiersToVersions(configuration)
        outputFile.setDisallowChanges(
          project.layout.buildDirectory.file(
            "reports/slack/dependencyVersionsIssues/$name/issues.txt"
          )
        )
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
