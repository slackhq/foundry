package foundry.intellij.skate.pluginlist

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.diagnostic.Logger
import foundry.intellij.skate.tracing.SkateSpanBuilder
import foundry.intellij.skate.util.getTraceReporter
import foundry.intellij.skate.util.isTracingEnabled
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val bundled: Boolean
)

class PluginListCollector : ProjectActivity {
    private val log = Logger.getInstance(PluginListCollector::class.java)

    override suspend fun execute(project: Project) {
        val startTimestamp = Instant.now()
        val pluginList = PluginManagerCore.plugins.map {
            PluginInfo(
                id = it.pluginId.idString,
                name = it.name,
                version = it.version,
                enabled = it.isEnabled,
                bundled = it.isBundled
            )
        }

        // log to IDE log for testing
        val pluginInfo = "Installed plugins:\n" + pluginList.joinToString("\n") {
            "[${it.id}] ${it.name} v${it.version} (enabled=${it.enabled}, bundled=${it.bundled})"
        }
        log.info(pluginInfo)

        if (project.isTracingEnabled()) {
            val traceReporter = project.getTraceReporter()

            // send data for individual plugin information
            pluginList.forEach { plugin ->
                val pluginSpanBuilder = SkateSpanBuilder().apply {
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
                    pluginSpanBuilder.getKeyValueList()
                )
            }
        }
    }
}
