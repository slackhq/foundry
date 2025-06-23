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
package foundry.intellij.skate.pluginlist

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PluginInfo(
  val id: String,
  val name: String,
  val version: String,
  val enabled: Boolean,
  val bundled: Boolean,
)

class PluginListCollector : ProjectActivity {
  private val log = Logger.getInstance(PluginListCollector::class.java)

  override suspend fun execute(project: Project) {
    val startTimestamp = Instant.now()
    val pluginList =
      PluginManagerCore.plugins.map {
        PluginInfo(
          id = it.pluginId.idString,
          name = it.name,
          version = it.version,
          enabled = it.isEnabled,
          bundled = it.isBundled,
        )
      }

    // log to IDE log for testing
    val pluginInfo =
      "Installed plugins:\n" +
        pluginList.joinToString("\n") {
          "[${it.id}] ${it.name} v${it.version} (enabled=${it.enabled}, bundled=${it.bundled})"
        }
    log.info(pluginInfo)

    if (project.isTracingEnabled()) {
      val traceReporter = project.getTraceReporter()

      // send data for individual plugin information
      pluginList.forEach { plugin ->
        val pluginSpanBuilder =
          SkateSpanBuilder().apply {
            addTag("event", "PLUGIN_INFO")
            addTag("plugin_id", plugin.id)
            addTag("plugin_name", plugin.name)
            addTag("plugin_version", plugin.version)
            addTag("plugin_enabled", plugin.enabled.toString())
            addTag("plugin_bundled", plugin.bundled.toString())
          }

        log.info("Sending plugin trace: ${pluginSpanBuilder.getKeyValueList()}")

        traceReporter.createPluginUsageTraceAndSendTrace(
          "plugin_info",
          startTimestamp,
          pluginSpanBuilder.getKeyValueList(),
        )
      }
    }
  }
}
