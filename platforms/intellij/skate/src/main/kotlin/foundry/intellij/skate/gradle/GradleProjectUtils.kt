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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths
import foundry.gradle.convertProjectPathToAccessor

/** Utility functions for working with Gradle projects. */
object GradleProjectUtils {

  /**
   * Checks if the given directory is a Gradle project by looking for build.gradle or
   * build.gradle.kts files.
   */
  fun isGradleProject(directory: VirtualFile): Boolean {
    if (!directory.isDirectory) return false
    return directory.children.any { it.name == "build.gradle" || it.name == "build.gradle.kts" }
  }

  /**
   * Finds the nearest parent Gradle project directory for the given file or directory. Returns the
   * file itself if it's a Gradle project directory, or null if no Gradle project is found.
   */
  fun findNearestGradleProject(root: VirtualFile, file: VirtualFile): VirtualFile? {
    var current: VirtualFile? = file
    while (current != null) {
      if (isGradleProject(current)) {
        return current
      } else if (current == root) {
        return null
      }
      current = current.parent
    }
    return null
  }

  /**
   * Gets the Gradle project path for the given directory in the format ":path:to:project". Returns
   * null if the directory is not part of a Gradle project.
   */
  fun getGradleProjectPath(project: Project, directory: VirtualFile): String? {
    @Suppress("DEPRECATION") val projectBasePath = project.baseDir.path
    val projectBaseDir = Paths.get(projectBasePath)
    val directoryPath = Paths.get(directory.path)

    // Check if the directory is within the project
    if (!directoryPath.startsWith(projectBaseDir)) {
      return null
    }

    // Get the relative path from the project base directory
    val relativePath = projectBaseDir.relativize(directoryPath)
    if (relativePath.toString().isEmpty()) {
      return ":"
    }

    // Convert the path to Gradle format
    return ":" + relativePath.toString().replace('/', ':')
  }

  /**
   * Gets the Gradle project accessor path for the given directory in the format
   * "projects.path.to.project". Returns null if the directory is not part of a Gradle project or
   * the root project
   */
  fun getGradleProjectAccessorPath(project: Project, directory: VirtualFile): String? {
    val gradlePath = getGradleProjectPath(project, directory) ?: return null
    if (gradlePath == ":") {
      return null
    }

    // Convert from ":path:to:project" to "projects.path.to.project"
    return "projects." + convertProjectPathToAccessor(gradlePath)
  }
}

/**
 * Converts a Gradle project path (e.g. ":module:sub-module") into a camelCased accessor path (e.g.
 * "module.subModule")
 */
fun String.gradleProjectAccessorify(): String {
  return buildString {
    var capNext = false
    for (c in this@gradleProjectAccessorify) {
      when (c) {
        '-' -> {
          capNext = true
          continue
        }
        ':' -> {
          append('.')
          continue
        }
        else -> {
          append(if (capNext) c.uppercaseChar() else c)
        }
      }
      capNext = false
    }
  }
}
