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

import foundry.gradle.FoundryVersions
import foundry.gradle.register
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task that validates whether the Java version specified in a `.java_version` matches the
 * expected version defined in a version catalog. If the versions match, an output file is generated
 * with the status "valid". If they do not match, the task fails with a descriptive error message.
 *
 * This is useful for projects that define the JDK in both places, as some tools like Renovate and
 * github actions work well with a `.java_version` file.
 */
@CacheableTask
public abstract class ValidateJavaVersionMatches : DefaultTask(), FoundryValidationTask {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val javaVersionFile: RegularFileProperty

  @get:Input public abstract val javaVersionFileRelativePath: Property<String>
  @get:Input public abstract val catalogName: Property<String>
  @get:Input public abstract val catalogJdkVersion: Property<Int>

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @TaskAction
  internal fun validate() {
    val javaVersion = javaVersionFile.asFile.get().readText().trim().toInt()
    val catalogVersion = catalogJdkVersion.get()

    check(javaVersion == catalogVersion) {
      "Java version ($javaVersion) in file '${javaVersionFileRelativePath.get()}' does not match the JDK version in ${catalogName.get()}.versions.toml ($catalogVersion). Please ensure these are aligned"
    }

    outputFile.asFile.get().writeText("valid")
  }

  internal companion object {
    fun register(
      project: Project,
      javaVersionFilePath: String,
      catalogJdk: Int,
      foundryVersions: FoundryVersions,
    ) {
      project.tasks.register<ValidateJavaVersionMatches>("validateJavaVersions") {
        javaVersionFile.set(project.layout.projectDirectory.file(javaVersionFilePath))
        javaVersionFileRelativePath.set(javaVersionFilePath)
        catalogJdkVersion.set(catalogJdk)
        catalogName.set(foundryVersions.catalogName)
        outputFile.set(
          project.layout.buildDirectory.file("foundry/validate_java_version/output.txt")
        )
      }
    }
  }
}
