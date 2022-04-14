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
package slack.gradle

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject
import kotlin.reflect.KClass
import okhttp3.OkHttpClient
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.registerIfAbsent
import slack.cli.AppleSiliconCompat
import slack.cli.AppleSiliconCompat.Arch.ARM64
import slack.cli.AppleSiliconCompat.Arch.X86_64
import slack.gradle.SlackTools.Companion.SERVICE_NAME
import slack.gradle.SlackTools.Parameters
import slack.gradle.agp.AgpHandler
import slack.gradle.util.M1ThermalParser
import slack.gradle.util.M1ThermalParser.ThermalState.CRITICAL
import slack.gradle.util.M1ThermalParser.ThermalState.FAIR
import slack.gradle.util.M1ThermalParser.ThermalState.NOMINAL
import slack.gradle.util.M1ThermalParser.ThermalState.SERIOUS
import slack.gradle.util.ThermLog
import slack.gradle.util.Thermals
import slack.gradle.util.ThermalsData
import slack.gradle.util.ThermalsParser
import slack.gradle.util.mapToBoolean

/** Misc tools for Slack Gradle projects, usable in tasks as a [BuildService] too. */
public abstract class SlackTools @Inject constructor(providers: ProviderFactory) :
  BuildService<Parameters>, AutoCloseable {

  public val agpHandler: AgpHandler by lazy { AgpHandlers.createHandler() }

  // I really really wish we could do this the "correct" way but Gradle is problematic with its
  // inconsistent expectations of Serializability. Specifically - it seems that `@Nested` does not
  // work for BuildService parameters
  public lateinit var globalConfig: GlobalConfig

  public var thermalsReporter: ThermalsReporter? = null

  private val logger = Logging.getLogger("SlackTools")
  private val extensions = ConcurrentHashMap<KClass<out SlackToolsExtension>, SlackToolsExtension>()

  public lateinit var okHttpClient: Lazy<OkHttpClient>

  private val logThermals =
    OperatingSystem.current().isMacOsX &&
      providers.gradleProperty(SlackProperties.LOG_THERMALS).mapToBoolean().getOrElse(false)

  public fun registerExtension(extension: SlackToolsExtension) {
    val dependencies =
      object : SlackToolsDependencies {
        override val okHttpClient: Lazy<OkHttpClient>
          get() = this@SlackTools.okHttpClient
      }
    val previous = extensions.put(extension::class, extension)
    check(previous == null) { "Duplicate extension registered for ${extension::class.simpleName}" }
    extension.bind(dependencies)
    if (extension is ThermalsReporter) {
      thermalsReporter = extension
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

  private val thermalsLogProcess: Process? =
    if (logThermals && AppleSiliconCompat.Arch.get() == X86_64) {
      ProcessBuilder("pmset", "-g", "thermlog").redirectOutput(thermalsFile()).start()
    } else {
      null
    }

  // TODO unify this API with the other thermals logging?
  private val m1Thermals: BehaviorSubject<Thermals> = BehaviorSubject.create()
  @Suppress("UNCHECKED_CAST")
  private val m1ThermalsHeartbeat: Disposable =
    if (logThermals && AppleSiliconCompat.Arch.get() == ARM64) {
      Observable.interval(5, SECONDS)
        .map {
          ThermLog(
            timestamp = LocalDateTime.now(),
            schedulerLimit = 0,
            availableCpus = 0,
            // These aren't entirely true numbers but best effort for now
            speedLimit =
              when (M1ThermalParser.getThermals()) {
                NOMINAL -> 100
                FAIR -> 75
                SERIOUS -> 50
                CRITICAL -> 25
                null -> -1
              },
          )
        }
        .filter { it.speedLimit != -1 }
        .map {
          thermalsFile().appendText("\n${it.timestamp} - ${it.speedLimit}")
          it
        }
        .scanWith<Thermals>({ Thermals.Empty }) { acc, next ->
          when (acc) {
            is Thermals.Empty -> ThermalsData(listOf(next))
            is ThermalsData -> ThermalsData(acc.logs + next)
          }
        }
        .subscribe { m1Thermals.onNext(it) }
    } else {
      Disposable.empty()
    }

  private var thermalsAtClose: Thermals? = null

  /** Returns the current or latest captured thermals log. */
  public val thermals: Thermals?
    get() {
      return thermalsAtClose ?: readThermalsResult()
    }

  @Synchronized
  private fun readThermalsResult(): Thermals? {
    return if (logThermals) {
      if (AppleSiliconCompat.Arch.get() == ARM64) {
        m1Thermals.value
      } else {
        val file = parameters.thermalsOutputFile.asFile.get()
        // File may no longer exist if they were running `clean`
        if (file.exists()) {
          val thermalsLog = file.readText()
          ThermalsParser.parse(thermalsLog)
        } else {
          Thermals.Empty
        }
      }
    } else {
      null
    }
  }

  override fun close() {
    m1ThermalsHeartbeat.dispose()
    try {
      // Close thermals process and save off its current value
      thermalsAtClose = readThermalsResult()
      if (!parameters.offline.get()) {
        thermalsAtClose?.let { thermalsReporter?.reportThermals(it) }
      }
    } catch (t: Throwable) {
      logger.error("Failed to parse thermals", t)
    } finally {
      thermalsLogProcess?.destroyForcibly()?.waitFor()
      if (okHttpClient.isInitialized()) {
        with(okHttpClient.value) {
          dispatcher.executorService.shutdown()
          connectionPool.evictAll()
          cache?.close()
        }
      }
    }
  }

  internal companion object {
    internal const val SERVICE_NAME = "slack-tools"

    internal fun register(
      project: Project,
      okHttpClient: Lazy<OkHttpClient>,
    ): Provider<SlackTools> {
      return project.gradle.sharedServices
        .registerIfAbsent(SERVICE_NAME, SlackTools::class) {
          parameters.thermalsOutputFile.set(
            project.layout.buildDirectory.file("outputs/logs/last-build-thermals.log")
          )
          parameters.offline.set(project.gradle.startParameter.isOffline)
        }
        .apply {
          get().apply {
            globalConfig = GlobalConfig(project)
            this.okHttpClient = okHttpClient
          }
        }
    }
  }

  public interface Parameters : BuildServiceParameters {
    /** An output file that the thermals process (continuously) writes to during the build. */
    public val thermalsOutputFile: RegularFileProperty
    public val offline: Property<Boolean>
  }
}

public interface ThermalsReporter {
  public fun reportThermals(thermals: Thermals)
}

public interface SlackToolsDependencies {
  public val okHttpClient: Lazy<OkHttpClient>
}

/** An extension for SlackTools. */
public interface SlackToolsExtension {
  public fun bind(sharedDependencies: SlackToolsDependencies)
}

@Suppress("UNCHECKED_CAST")
public fun Project.slackTools(): SlackTools {
  return slackToolsProvider().get()
}

@Suppress("UNCHECKED_CAST")
public fun Project.slackToolsProvider(): Provider<SlackTools> {
  return (project.gradle.sharedServices.registrations.getByName(SERVICE_NAME) as
      BuildServiceRegistration<SlackTools, Parameters>)
    .service
}
