/*
 * Copyright (C) 2025 Slack Technologies, LLC
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

import foundry.gradle.FoundryShared
import foundry.gradle.FoundryVersions
import foundry.gradle.capitalizeUS
import foundry.gradle.register
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task that validates whether version specified in a given [versionFile] (for example:
 * `.java_version`) matches the expected version defined in a version catalog ([catalogVersion]). If
 * the versions match, an [outputFile] is generated with the status "valid". If they do not match,
 * the task fails with a descriptive error message.
 *
 * This is useful for projects that define the version in multiple places, as some tools like
 * Renovate and GitHub actions work well with a `.java_version` file and some caching mechanisms
 * want a single manifest file.
 */
@CacheableTask
public abstract class ValidateVersionsMatch @Inject constructor(problems: Problems) :
  DefaultTask(), FoundryValidationTask {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val versionFile: RegularFileProperty

  @get:Input public abstract val versionFileRelativePath: Property<String>
  @get:Input public abstract val catalogName: Property<String>
  @get:Input public abstract val catalogVersion: Property<String>

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  private val problemReporter = problems.reporter

  init {
    group = FoundryShared.FOUNDRY_TASK_GROUP
  }

  @TaskAction
  internal fun validate() {
    val versionFilePath = versionFile.asFile.get().toPath()
    val fileVersion = versionFilePath.readText().trim()
    val requiredVersion = catalogVersion.get()

    val valid = fileVersion == requiredVersion
    if (!valid) {
      val problemId =
        ProblemId.create(
          "validate-versions-match",
          "Versions mismatch",
          FoundryShared.PROBLEM_GROUP,
        )
      problemReporter.throwing(
        GradleException(
          "Version ($fileVersion) in file '${versionFileRelativePath.get()}' does not match the version in ${catalogName.get()}.versions.toml ($requiredVersion)."
        ),
        problemId,
      ) {
        fileLocation(versionFilePath.absolutePathString())
        solution("Ensure these versions are aligned.")
        severity(Severity.ERROR)
      }
    }

    outputFile.asFile.get().writeText(valid.toString())
  }

  internal companion object {
    fun register(
      project: Project,
      type: String,
      versionFilePath: String,
      catalogVersion: String,
      foundryVersions: FoundryVersions,
    ) {
      project.tasks.register<ValidateVersionsMatch>("validate${type.capitalizeUS()}Matches") {
        versionFile.set(project.layout.projectDirectory.file(versionFilePath))
        versionFileRelativePath.set(versionFilePath)
        this.catalogVersion.set(catalogVersion)
        catalogName.set(foundryVersions.catalogName)
        outputFile.set(
          project.layout.buildDirectory.file("foundry/version-matches/$type/valid.txt")
        )
      }
    }
  }
}
