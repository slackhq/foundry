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
package foundry.gradle

import com.google.common.collect.Sets
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import foundry.cli.AppleSiliconCompat
import foundry.gradle.FoundryTools.Companion.SERVICE_NAME
import foundry.gradle.FoundryTools.Parameters
import foundry.gradle.agp.AgpHandler
import foundry.gradle.agp.AgpHandlers
import foundry.gradle.util.JsonTools
import foundry.gradle.util.LocalProperties
import foundry.gradle.util.Thermals
import foundry.gradle.util.ThermalsWatcher
import foundry.gradle.util.setDisallowChanges
import foundry.gradle.util.shutdown
import foundry.gradle.util.sneakyNull
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.streams.asSequence
import okhttp3.OkHttpClient
import okio.buffer
import okio.sink
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.internal.os.OperatingSystem

/** Misc tools for Foundry Gradle projects, usable in tasks as a [BuildService] too. */
public abstract class FoundryTools : BuildService<Parameters>, AutoCloseable {

  public val agpHandler: AgpHandler by lazy { AgpHandlers.createHandler() }
  public val moshi: Moshi
    get() = JsonTools.MOSHI

  // I really really wish we could do this the "correct" way but Gradle is problematic with its
  // inconsistent expectations of Serializability. Specifically - it seems that `@Nested` does not
  // work for BuildService parameters
  public lateinit var globalConfig: GlobalConfig

  private val logger = Logging.getLogger("FoundryTools")
  private val extensions: Map<Class<out FoundryToolsExtension>, FoundryToolsExtension>

  private val okHttpClient = lazy { OkHttpClient.Builder().build() }

  // Thermals watching vars
  private var thermalsReporter: ThermalsReporter? = null
  private val thermalsWatcher: ThermalsWatcher?
  private val thermalsExecutor: ExecutorService?
  private var thermalsAtClose: Thermals? = null

  /** Returns the current or latest captured thermals log. */
  public val thermals: Thermals?
    get() {
      return thermalsAtClose ?: peekThermals()
    }

  private data class AvoidedTask(val type: String, val taskName: String)

  /**
   * A set of logged [AvoidedTask]s. This is used for logging skippy diagnostics. These are written
   * out to a diagnostics file at the end of the build if enabled.
   */
  private val avoidedTasks = Sets.newConcurrentHashSet<AvoidedTask>()

  init {
    debugLog("SlackTools created")

    // Thermals logging
    var canLogThermals = parameters.logThermals.get()
    if (canLogThermals && parameters.configurationCacheEnabled.get()) {
      if (AppleSiliconCompat.Arch.get() != AppleSiliconCompat.Arch.ARM64) {
        logger.warn(
          "Thermals logging is enabled but configuration cache is enabled and this is not an " +
            "Apple Silicon machine. Thermals logging will be disabled. Please set 'slack.log-thermals' " +
            "to false in your home gradle.properties."
        )
        canLogThermals = false
      }
    }
    if (canLogThermals) {
      thermalsExecutor =
        Executors.newSingleThreadExecutor { r ->
          Thread(r, "SlackToolsThermalsHeartbeat").apply { isDaemon = true }
        }
      thermalsWatcher = ThermalsWatcher(logger, ::thermalsFile)
      thermalsWatcher.start(thermalsExecutor)
    } else {
      thermalsWatcher = null
      thermalsExecutor = null
    }

    extensions = buildMap {
      ServiceLoader.load(
          FoundryToolsExtension::class.java,
          FoundryToolsExtension::class.java.classLoader,
        )
        .stream()
        .asSequence()
        .forEach { provider ->
          val extension =
            try {
              provider.get()
            } catch (e: InstantiationException) {
              logger.error("Failed to load extension ${provider.type().simpleName}", e)
              return@forEach
            }
          val type = provider.type()
          debugLog("Loaded extension ${type.simpleName}")
          val previous = put(type, extension)
          check(previous == null) {
            "Duplicate extension registered for ${provider.type().simpleName}"
          }
        }
    }

    if (extensions.isNotEmpty()) {
      val dependencies =
        object : FoundryToolsDependencies {
          override val okHttpClient: Lazy<OkHttpClient>
            get() = this@FoundryTools.okHttpClient
        }
      val context =
        object : FoundryToolsExtension.Context {
          override val isOffline: Boolean
            get() = this@FoundryTools.parameters.offline.get()

          override val sharedDependencies: FoundryToolsDependencies
            get() = dependencies
        }
      for (extension in extensions.values) {
        extension.bind(context)
        if (extension is ThermalsReporter) {
          if (thermalsReporter != null) {
            logger.warn("Multiple thermals reporters registered, only the last one will be used")
          }
          thermalsReporter = extension
        }
      }
    }
  }

  private fun debugLog(message: String, throwable: Throwable? = null) {
    if (parameters.logVerbosely.get()) {
      logger.lifecycle(message, throwable)
    } else {
      logger.debug(message, throwable)
    }
  }

  private fun thermalsFile(): File {
    return parameters.thermalsOutputFile.asFile.get().apply {
      if (!exists()) {
        parentFile.mkdirs()
        createNewFile()
      }
    }
  }

  private fun peekThermals(): Thermals? {
    return thermalsWatcher?.peek()
  }

  /** Retrieves a loaded instance of [T], if any. */
  public fun <T : FoundryToolsExtension> findExtension(type: Class<out T>): T? {
    @Suppress("UNCHECKED_CAST")
    return extensions[type] as T?
  }

  internal fun logAvoidedTask(taskType: String, taskName: String) {
    debugLog("[Skippy] Skipping '$taskType' task: $taskName")
    avoidedTasks.add(AvoidedTask(taskType, taskName))
  }

  override fun close() {
    // Close thermals process and save off its current value
    thermalsAtClose = thermalsWatcher?.stop()
    runCatchingWithLog("Failed to report thermals") {
      thermalsAtClose?.let { thermalsAtClose ->
        // Write final thermals to output file
        val thermalsJsonFile = parameters.thermalsOutputJsonFile.get().asFile
        if (thermalsJsonFile.exists()) {
          thermalsJsonFile.delete()
        }
        thermalsJsonFile.parentFile.mkdirs()
        thermalsJsonFile.createNewFile()
        JsonWriter.of(thermalsJsonFile.sink().buffer()).use { writer ->
          moshi.adapter<Thermals>().toJson(writer, thermalsAtClose)
        }
        if (!parameters.offline.get()) {
          thermalsReporter?.reportThermals(thermalsAtClose)
        }
      }
    }

    if (parameters.enableSkippyDiagnostics.get()) {
      runCatchingWithLog("Failed to write Skippy diagnostics") {
        val outputFile = parameters.skippyDiagnosticsOutputFile.get().asFile
        if (outputFile.exists()) {
          outputFile.delete()
        }
        outputFile.parentFile.mkdirs()

        avoidedTasks
          .groupBy { it.type }
          .mapValues { (_, tasks) -> tasks.map { it.taskName }.sorted() }
          .toSortedMap()
          .forEach { (type, tasks) ->
            outputFile.appendText("$type:\n")
            for (task in tasks) {
              outputFile.appendText("  $task\n")
            }
          }
      }
    }

    runCatchingWithLog("Failed to close extension") {
      extensions.values.forEach(FoundryToolsExtension::close)
    }

    thermalsExecutor?.shutdown()
    if (okHttpClient.isInitialized()) {
      okHttpClient.value.shutdown()
    }
  }

  private fun runCatchingWithLog(errorMessage: String, block: () -> Unit) {
    runCatching(block).onFailure { t -> logger.error(errorMessage, t) }
  }

  /**
   * Abstraction for loading a [Map] provider that handles caching automatically per root project.
   * This way properties are only ever parsed at most once per root project. The goal for this is to
   * build on top of [LocalProperties] and provide a more convenient API for accessing properties
   * from multiple sources in a configuration-caching-compatible way. Start parameters are special
   * because they come from [StartParameter.projectProperties] and are intended to supersede any
   * other property values.
   */
  internal fun globalStartParameterProperty(key: String): Provider<String> {
    return parameters.startParameterProperties.map { sneakyNull(it[key]) }
  }

  internal fun globalLocalProperty(key: String): Provider<String> {
    return parameters.localProperties.map { sneakyNull(it[key]) }
  }

  public companion object {
    public const val SERVICE_NAME: String = "FoundryTools"

    internal fun register(
      project: Project,
      logThermals: Boolean,
      enableSkippyDiagnostics: Boolean,
      logVerbosely: Boolean,
      thermalsLogJsonFileProvider: Provider<RegularFile>,
      isConfigurationCacheRequested: Provider<Boolean>,
      startParameterProperties: Provider<Map<String, String>>,
      globalLocalProperties: Provider<Map<String, String>>,
    ): Provider<FoundryTools> {
      return project.gradle.sharedServices
        .registerIfAbsent(SERVICE_NAME, FoundryTools::class.java) {
          parameters.thermalsOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("outputs/logs/last-build-thermals.log")
          )
          parameters.thermalsOutputJsonFile.setDisallowChanges(thermalsLogJsonFileProvider)
          parameters.offline.setDisallowChanges(project.gradle.startParameter.isOffline)
          parameters.cleanRequested.setDisallowChanges(
            project.gradle.startParameter.taskNames.any { it.equals("clean", ignoreCase = true) }
          )
          parameters.logThermals.setDisallowChanges(
            project.provider {
              logThermals && !parameters.cleanRequested.get() && OperatingSystem.current().isMacOsX
            }
          )
          parameters.configurationCacheEnabled.setDisallowChanges(isConfigurationCacheRequested)
          parameters.enableSkippyDiagnostics.setDisallowChanges(enableSkippyDiagnostics)
          parameters.skippyDiagnosticsOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("outputs/logs/skippy-diagnostics.txt")
          )
          parameters.logVerbosely.setDisallowChanges(logVerbosely)
          parameters.localProperties.setDisallowChanges(globalLocalProperties)
          parameters.startParameterProperties.setDisallowChanges(startParameterProperties)
        }
        .apply { get().apply { globalConfig = GlobalConfig(project) } }
    }
  }

  public interface Parameters : BuildServiceParameters {
    /** An output file that the thermals process (continuously) writes to during the build. */
    public val thermalsOutputFile: RegularFileProperty
    /** A structured version of [thermalsOutputFile] using JSON. */
    public val thermalsOutputJsonFile: RegularFileProperty
    public val offline: Property<Boolean>
    public val cleanRequested: Property<Boolean>
    public val logThermals: Property<Boolean>
    public val configurationCacheEnabled: Property<Boolean>
    public val enableSkippyDiagnostics: Property<Boolean>
    /** An output file of skippy diagnostics. */
    public val skippyDiagnosticsOutputFile: RegularFileProperty
    public val logVerbosely: Property<Boolean>
    public val localProperties: MapProperty<String, String>
    public val startParameterProperties: MapProperty<String, String>
  }
}

public interface ThermalsReporter {
  public fun reportThermals(thermals: Thermals)
}

public interface FoundryToolsDependencies {
  public val okHttpClient: Lazy<OkHttpClient>
}

/** An extension for SlackTools. */
public interface FoundryToolsExtension : AutoCloseable {
  public fun bind(context: Context)

  public interface Context {
    public val isOffline: Boolean
    public val sharedDependencies: FoundryToolsDependencies
  }
}

public fun Project.foundryTools(): FoundryTools {
  return foundryToolsProvider().get()
}

@Suppress("UNCHECKED_CAST")
public fun Project.foundryToolsProvider(): Provider<FoundryTools> {
  return (project.gradle.sharedServices.registrations.getByName(SERVICE_NAME)
      as BuildServiceRegistration<FoundryTools, Parameters>)
    .service
}
