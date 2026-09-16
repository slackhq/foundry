/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
package foundry.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

public abstract class VerifyConventionPluginsTask : DefaultTask() {

  @get:Input public abstract val buildFileContents: MapProperty<String, String>

  @get:Input public abstract val lintExemptBuildFiles: ListProperty<String>

  @TaskAction
  public fun verifyConventions() {
    val failures = mutableListOf<String>()
    val lintExemptions = lintExemptBuildFiles.get().toSet()

    for ((relativePath, contents) in buildFileContents.get().toSortedMap()) {
      if ("id(\"foundry.spotless\")" !in contents) {
        failures += "$relativePath must apply id(\"foundry.spotless\")"
      }

      val hasKotlinConvention =
        KOTLIN_CONVENTIONS.any { convention -> convention in contents } ||
          "alias(libs.plugins.kotlin.multiplatform)" in contents
      if (!hasKotlinConvention) {
        failures += "$relativePath must apply a Foundry Kotlin convention"
      }

      if ("alias(libs.plugins.lint)" in contents) {
        failures += "$relativePath must use id(\"foundry.lint\") instead of the raw lint plugin"
      }
      if (relativePath !in lintExemptions && "id(\"foundry.lint\")" !in contents) {
        failures += "$relativePath must apply id(\"foundry.lint\")"
      }
    }

    check(failures.isEmpty()) {
      failures.joinToString(
        prefix = "Convention plugin coverage failed:\n- ",
        separator = "\n- ",
      )
    }
  }

  private companion object {
    val KOTLIN_CONVENTIONS =
      listOf(
        "id(\"foundry.kotlin-jvm\")",
        "id(\"foundry.kotlin-jvm-gradle\")",
        "id(\"foundry.kotlin-jvm-intellij\")",
      )
  }
}
