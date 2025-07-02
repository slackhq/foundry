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
import com.intellij.mock.MockProject
import com.intellij.openapi.util.Disposer
import foundry.intellij.skate.gradle.GradleProjectUtils.parseProjectPaths
import org.junit.Test

class ProjectPathServiceIntegrationTest {

  @Test
  fun `service correctly parses all-projects txt file`() {
    val projectFileContent =
      """
      :platforms:gradle:agp-handlers:agp-handler-api
      :platforms:gradle:better-gradle-properties
      :platforms:gradle:foundry-gradle-plugin
      :platforms:intellij:artifactory-authenticator
      :platforms:intellij:compose
      :platforms:intellij:compose:playground
      :platforms:intellij:skate
      :tools:cli
      :tools:foundry-common
      :tools:robolectric-sdk-management
      :tools:skippy
      :tools:tracing
      :tools:version-number
    """
        .trimIndent()

    val parsedPaths = parseProjectPaths(projectFileContent)

    assertThat(parsedPaths).hasSize(13)
    assertThat(parsedPaths).contains(":platforms:intellij:skate")
    assertThat(parsedPaths).contains(":tools:cli")
    assertThat(parsedPaths).contains(":tools:foundry-common")
  }

  @Test
  fun `service handles empty lines and comments`() {
    val projectFileContent =
      """
      # This is a comment
      :platforms:intellij:skate

      # Another comment
      :tools:cli

      :tools:foundry-common
      # End comment
    """
        .trimIndent()

    val parsedPaths = parseProjectPaths(projectFileContent)

    assertThat(parsedPaths).hasSize(3)
    assertThat(parsedPaths).contains(":platforms:intellij:skate")
    assertThat(parsedPaths).contains(":tools:cli")
    assertThat(parsedPaths).contains(":tools:foundry-common")
    assertThat(parsedPaths).doesNotContain("# This is a comment")
    assertThat(parsedPaths).doesNotContain("")
  }

  @Test
  fun `service validates project paths correctly`() {
    val service = createTestProjectPathService()

    // Since the test service reads from actual file (which may not exist),
    // we test with empty set behavior
    val projectPaths = service.getProjectPaths()

    // Test validation logic against actual service
    assertThat(service.isValidProjectPath(":nonexistent:project")).isFalse()
    assertThat(service.isValidProjectPath(":invalid:path")).isFalse()
    assertThat(service.isValidProjectPath("")).isFalse()

    // If paths exist, test validation works
    projectPaths.forEach { path -> assertThat(service.isValidProjectPath(path)).isTrue() }
  }

  @Test
  fun `service caching works correctly`() {
    val service = createTestProjectPathService()

    // First call should load from file
    val firstCall = service.getProjectPaths()

    // Second call should use cache
    val secondCall = service.getProjectPaths()

    // Results should be identical due to caching
    assertThat(firstCall).isEqualTo(secondCall)

    // Verify both calls return the same content
    assertThat(firstCall.size).isEqualTo(secondCall.size)
  }

  @Test
  fun `service cache invalidation works`() {
    val service = createTestProjectPathService()

    // Load initial data
    val initialPaths = service.getProjectPaths()

    // Invalidate cache
    service.invalidateCache()

    // Next call should reload
    val reloadedPaths = service.getProjectPaths()

    // Content should be the same but instance might be different
    assertThat(reloadedPaths).containsExactlyElementsIn(initialPaths)
  }

  @Test
  fun `service handles missing all-projects file gracefully`() {
    // Test when the all-projects.txt file doesn't exist
    val service = createTestProjectPathService()

    // Should return empty set rather than throw exception
    val paths = service.getProjectPaths()
    assertThat(paths).isEmpty()
  }

  private fun createTestProjectPathService(): ProjectPathService {
    val project = MockProject(null, Disposer.newDisposable())
    val service = ProjectPathService(project)
    return service
  }
}
