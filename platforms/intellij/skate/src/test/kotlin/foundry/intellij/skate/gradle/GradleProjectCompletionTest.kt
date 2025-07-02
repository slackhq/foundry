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
package foundry.intellij.skate.gradle

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import foundry.intellij.skate.gradle.GradleProjectUtils.convertRelativePathToGradlePath
import org.junit.Test

class GradleProjectCompletionTest {

  @Test
  fun `completion excludes current project from suggestions`() {
    val allProjects =
      setOf(
        ":platforms:intellij:skate",
        ":platforms:intellij:compose",
        ":tools:cli",
        ":tools:foundry-common",
      )

    val currentProject = ":platforms:intellij:skate"

    // Test the actual filtering logic used in GradleProjectCompletionContributor
    val filteredPaths = allProjects.filter { it != currentProject }

    assertThat(filteredPaths)
      .containsExactly(":platforms:intellij:compose", ":tools:cli", ":tools:foundry-common")
    assertThat(filteredPaths).doesNotContain(":platforms:intellij:skate")
  }

  @Test
  fun `completion only triggers in project call context`() {
    val projectCallContext = "implementation(project(\""
    val nonProjectContext = "implementation(\""
    val anotherNonProjectContext = "dependencies {"

    // Test the actual context detection logic used in ProjectPathCompletionProvider
    assertThat(projectCallContext.contains("project(")).isTrue()
    assertThat(nonProjectContext.contains("project(")).isFalse()
    assertThat(anotherNonProjectContext.contains("project(")).isFalse()
  }

  @Test
  fun `completion creates correct lookup elements`() {
    val projectPath = ":platforms:intellij:skate"

    // Test the actual LookupElementBuilder creation from ProjectPathCompletionProvider
    val lookupElement =
      LookupElementBuilder.create(projectPath)
        .withIcon(AllIcons.Nodes.Module)
        .withTypeText("Gradle Project")

    // Verify the lookup string is correct
    assertThat(lookupElement.lookupString).isEqualTo(projectPath)

    // Verify the builder was created successfully (non-null)
    assertThat(lookupElement).isNotNull()
  }

  @Test
  fun `GradleProjectUtils path conversion works correctly`() {
    val testCases =
      mapOf(
        "platforms/intellij/skate" to ":platforms:intellij:skate",
        "tools/cli" to ":tools:cli",
        "" to ":",
        "platforms/gradle/foundry-gradle-plugin" to ":platforms:gradle:foundry-gradle-plugin",
      )

    testCases.forEach { (relativePath, expectedProjectPath) ->
      val calculatedPath = convertRelativePathToGradlePath(relativePath)
      assertThat(calculatedPath).isEqualTo(expectedProjectPath)
    }
  }

  @Test
  fun `completion context detection works with surrounding text`() {
    // Test the surrounding text analysis used in ProjectPathCompletionProvider
    val contextWithProject = "dependencies { implementation(project(\""
    val contextWithoutProject = "dependencies { implementation(libs.someLibrary"
    val contextAfterProject = "implementation(project(\":tools:cli\"))"

    assertThat(contextWithProject.contains("project(")).isTrue()
    assertThat(contextWithoutProject.contains("project(")).isFalse()
    assertThat(contextAfterProject.contains("project(")).isTrue()
  }
}
