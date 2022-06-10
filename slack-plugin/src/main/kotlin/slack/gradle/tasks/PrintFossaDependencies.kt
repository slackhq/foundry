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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import slack.gradle.safeCapitalize

/**
 * A task that writes runtime dependency info found in [identifiersToVersions].
 *
 * This is used by the Fossa tool to parse and look up our dependencies. The output file is in the
 * form of a newline-delimited list of `<module identifier>:<version>`.
 *
 * Example:
 *
 * ```
 * mvn+com.google.gson:gson:2.8.6
 * mvn+com.google.guava:guava:29-jre
 * ```
 *
 * More details:
 * https://slack-pde.slack.com/archives/C012A55CZNH/p1607469397011200?thread_ts=1607384582.004300&cid=C012A55CZNH
 */
@CacheableTask
public abstract class PrintFossaDependencies : BaseDependencyCheckTask() {

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  init {
    group = "slack"
  }

  override fun handleDependencies(identifiersToVersions: Map<String, String>) {
    val file = outputFile.asFile.get()
    file.bufferedWriter().use { writer ->
      identifiersToVersions.entries
        .map { (moduleIdentifier, version) -> "mvn+$moduleIdentifier:$version" }
        .sorted() // Important for deterministic ouputs
        .joinTo(writer, separator = "\n")
    }

    logger.lifecycle("Fossa deps written to $file")
  }

  public companion object {
    public fun register(
      project: Project,
      name: String,
      configuration: Configuration
    ): TaskProvider<PrintFossaDependencies> {
      return project.tasks.register<PrintFossaDependencies>(
        "print${name.safeCapitalize()}FossaDependencies"
      ) {
        outputFile.set(project.layout.buildDirectory.file("reports/slack/fossa/$name.txt"))
        configureIdentifiersToVersions(configuration)
      }
    }
  }
}
