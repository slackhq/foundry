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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that manages available project paths by reading from `gradle/all-projects.txt` file.
 * Provides caching and automatic invalidation when the file changes.
 */
@Service(Service.Level.PROJECT)
class ProjectPathService(private val project: Project) : Disposable {

  private val projectPathsCache = ConcurrentHashMap<String, Set<String>>()
  private var connection: MessageBusConnection?

  init {
    // Listen for file changes to invalidate cache
    // TODO is there a finer-grained way?
    connection =
      project.messageBus.connect().apply {
        subscribe(
          VirtualFileManager.VFS_CHANGES,
          object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
              events.forEach { event ->
                if (event.file?.name == "all-projects.txt") {
                  invalidateCache()
                }
              }
            }
          },
        )
      }
  }

  /**
   * Gets all available project paths. Returns cached results if available, otherwise reads from
   * `gradle/all-projects.txt` file.
   */
  fun getProjectPaths(): Set<String> {
    val cacheKey = project.basePath ?: return emptySet()

    return projectPathsCache.computeIfAbsent(cacheKey) { loadProjectPathsFromFile() }
  }

  /** Checks if a given project path exists in the available projects. */
  fun isValidProjectPath(path: String): Boolean {
    return getProjectPaths().contains(path)
  }

  fun invalidateCache() {
    projectPathsCache.clear()
  }

  private fun loadProjectPathsFromFile(): Set<String> {
    val basePath = project.basePath ?: return emptySet()
    val allProjectsFile =
      VirtualFileManager.getInstance().findFileByUrl("file://$basePath/gradle/all-projects.txt")
        ?: return emptySet()

    return try {
      val content = String(allProjectsFile.contentsToByteArray(), Charsets.UTF_8)
      parseProjectPaths(content)
    } catch (_: IOException) {
      emptySet()
    }
  }

  internal fun parseProjectPaths(content: String): Set<String> {
    return content
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .toSet()
  }

  override fun dispose() {
    connection?.disconnect()
    connection = null
  }
}
