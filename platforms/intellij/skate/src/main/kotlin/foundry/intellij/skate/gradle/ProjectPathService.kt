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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import foundry.intellij.skate.SkatePluginSettings
import foundry.intellij.skate.gradle.GradleProjectUtils.parseProjectPaths
import java.io.IOException

private const val ALL_PROJECTS_PATH = "gradle/all-projects.txt"

/**
 * Service that manages available project paths by reading from `gradle/all-projects.txt` file. Only
 * provides functionality if the all-projects.txt file exists in the project.
 */
@Service(Service.Level.PROJECT)
class ProjectPathService(private val project: Project) : Disposable {

  private var cachedProjectPaths: Set<String>? = null
  private var connection: MessageBusConnection?

  init {
    connection =
      project.messageBus.connect().apply {
        subscribe(
          VirtualFileManager.VFS_CHANGES,
          object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
              events.forEach { event ->
                val eventPath = event.file?.path
                if (eventPath != null && eventPath.endsWith(ALL_PROJECTS_PATH)) {
                  invalidateCache()
                }
              }
            }
          },
        )
      }
  }

  /**
   * Gets all available project paths. Returns an empty set if the service is disabled,
   * all-projects.txt doesn't exist, or other conditions are not met. Uses simple caching to avoid
   * repeated file reads.
   */
  fun getProjectPaths(): Set<String> {
    if (!isEnabled() || !hasAllProjectsFile()) {
      return emptySet()
    }

    return cachedProjectPaths ?: loadProjectPathsFromFile().also { cachedProjectPaths = it }
  }

  /** Checks if a given project path exists in the available projects. */
  fun isValidProjectPath(path: String): Boolean {
    return getProjectPaths().contains(path)
  }

  /** Checks if the ProjectPathService is enabled via settings. */
  private fun isEnabled(): Boolean {
    return project.getService(SkatePluginSettings::class.java)?.isProjectPathServiceEnabled != false
  }

  fun invalidateCache() {
    cachedProjectPaths = null
  }

  private fun hasAllProjectsFile(): Boolean {
    val basePath = project.basePath ?: return false
    return VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$ALL_PROJECTS_PATH") !=
      null
  }

  private fun loadProjectPathsFromFile(): Set<String> {
    val basePath = project.basePath ?: return emptySet()
    val allProjectsFile =
      VirtualFileManager.getInstance().findFileByUrl("file://$basePath/$ALL_PROJECTS_PATH")
        ?: return emptySet()

    return try {
      val content = String(allProjectsFile.contentsToByteArray(), Charsets.UTF_8)
      parseProjectPaths(content)
    } catch (_: IOException) {
      emptySet()
    }
  }

  override fun dispose() {
    connection?.disconnect()
    connection = null
  }
}
