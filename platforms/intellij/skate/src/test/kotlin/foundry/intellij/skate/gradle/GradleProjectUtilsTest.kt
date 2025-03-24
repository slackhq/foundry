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
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.Disposer
import org.junit.Test

class GradleProjectUtilsTest {

  @Test
  fun `findNearestGradleProject returns current directory if it is a Gradle project`() {
    val root = MockVirtualFile(true, "root")
    val gradleDir = MockVirtualFile(true, "gradleProject")
    gradleDir.addChild(MockVirtualFile(false, "build.gradle"))
    root.addChild(gradleDir)

    assertThat(GradleProjectUtils.findNearestGradleProject(root, gradleDir)).isEqualTo(gradleDir)
  }

  @Test
  fun `findNearestGradleProject returns nearest parent Gradle project`() {
    val root = MockVirtualFile(true, "root")
    val gradleDir = MockVirtualFile(true, "gradleProject")
    gradleDir.addChild(MockVirtualFile(false, "build.gradle"))
    val subDir = MockVirtualFile(true, "subDir")
    gradleDir.addChild(subDir)
    root.addChild(gradleDir)

    assertThat(GradleProjectUtils.findNearestGradleProject(root, subDir)).isEqualTo(gradleDir)
  }

  @Test
  fun `findNearestGradleProject returns null if no Gradle project is found`() {
    val root = MockVirtualFile(true, "root")
    val subDir = MockVirtualFile(true, "subDir")
    subDir.addChild(MockVirtualFile(false, "someFile.txt"))
    root.addChild(subDir)

    assertThat(GradleProjectUtils.findNearestGradleProject(root, subDir)).isNull()
  }

  @Test
  fun `findNearestGradleProject returns root if it is a Gradle project`() {
    val root = MockVirtualFile(true, "root")
    root.addChild(MockVirtualFile(false, "build.gradle"))
    val subDir = MockVirtualFile(true, "subDir")
    root.addChild(subDir)

    assertThat(GradleProjectUtils.findNearestGradleProject(root, subDir)).isEqualTo(root)
  }

  @Test
  fun `isGradleProject returns true when directory contains build_gradle`() {
    val exampleDir = MockVirtualFile(true, "exampleDir")
    exampleDir.addChild(MockVirtualFile(false, "build.gradle"))
    exampleDir.addChild(MockVirtualFile(false, "other_file.txt"))

    assertThat(GradleProjectUtils.isGradleProject(exampleDir)).isTrue()
  }

  @Test
  fun `isGradleProject returns true when directory contains build_gradle_kts`() {
    val exampleDir = MockVirtualFile(true, "exampleDir")
    exampleDir.addChild(MockVirtualFile(false, "build.gradle.kts"))
    exampleDir.addChild(MockVirtualFile(false, "some_file.txt"))

    assertThat(GradleProjectUtils.isGradleProject(exampleDir)).isTrue()
  }

  @Test
  fun `isGradleProject returns false for directory with no gradle build files`() {
    val exampleDir = MockVirtualFile(true, "exampleDir")
    exampleDir.addChild(MockVirtualFile(false, "file1.txt"))
    exampleDir.addChild(MockVirtualFile(false, "file2.doc"))

    assertThat(GradleProjectUtils.isGradleProject(exampleDir)).isFalse()
  }

  @Test
  fun `isGradleProject returns false when VirtualFile is not a directory`() {
    val exampleFile = MockVirtualFile(false, "exampleFile")

    assertThat(GradleProjectUtils.isGradleProject(exampleFile)).isFalse()
  }

  @Test
  fun `isGradleProject returns false when directory has no children`() {
    val exampleDir = MockVirtualFile(true, "exampleDir")

    assertThat(GradleProjectUtils.isGradleProject(exampleDir)).isFalse()
  }

  @Test
  fun `getGradleProjectPath returns root path when base path matches directory`() {
    val rootDir = MockVirtualFile(true, "rootDir")
    val project = MockProject(null, Disposer.newDisposable()).apply { baseDir = rootDir }

    val directory = MockVirtualFile(true, "rootDir")

    assertThat(GradleProjectUtils.getGradleProjectPath(project, directory)).isEqualTo(":")
  }

  @Test
  fun `getGradleProjectPath returns correct path for nested directory`() {
    val rootDir = MockVirtualFile(true, "rootDir")
    val project = MockProject(null, Disposer.newDisposable()).apply { baseDir = rootDir }

    val nestedDir = MockVirtualFile(true, "nested")
    val projectDir = MockVirtualFile(true, "project")
    nestedDir.addChild(projectDir)
    rootDir.addChild(nestedDir)

    assertThat(GradleProjectUtils.getGradleProjectPath(project, projectDir))
      .isEqualTo(":nested:project")
  }

  @Test
  fun `getGradleProjectPath returns null for directory outside project base path`() {
    val rootDir = MockVirtualFile(true, "rootDir")
    val project = MockProject(null, Disposer.newDisposable()).apply { baseDir = rootDir }

    val externalDirectory = MockVirtualFile(true, "externalDirectory")

    assertThat(GradleProjectUtils.getGradleProjectPath(project, externalDirectory)).isNull()
  }
}
