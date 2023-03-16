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
package slack.gradle.tasks.robolectric

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import slack.gradle.property
import slack.gradle.tasks.BootstrapTask
import slack.gradle.util.mapToBoolean
import slack.gradle.util.shutdown

/**
 * Updates the Robolectric android-all jars. Is declared as a task dependency of all
 * robolectric-using test tasks.
 *
 * This will download any missing jars, skip any already downloaded ones, and delete any unused
 * existing ones.
 */
@UntrackedTask(because = "State for this is handled elsewhere.")
internal abstract class UpdateRobolectricJarsTask
@Inject
constructor(
  private val workerExecutor: WorkerExecutor,
  objects: ObjectFactory,
  providers: ProviderFactory,
) : DefaultTask(), BootstrapTask {

  @get:Internal
  val forceReDownload =
    objects
      .property<Boolean>()
      .convention(
        providers
          .environmentVariable("SLACK_FORCE_REDOWNLOAD_ROBOLECTRIC_JARS")
          .mapToBoolean()
          .orElse(false)
      )

  @get:Internal abstract val sdkVersions: ListProperty<Int>

  @get:Internal abstract val instrumentedVersion: Property<Int>

  @get:Internal abstract val outputDir: DirectoryProperty

  @get:Internal abstract val offline: Property<Boolean>

  @get:Internal
  internal val allSdks by lazy {
    val iVersion = instrumentedVersion.get()
    // Sourced from
    // https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
    listOf(
        DefaultSdk(21, "5.0.2_r3", "r0", "REL", 8, iVersion),
        DefaultSdk(22, "5.1.1_r9", "r2", "REL", 8, iVersion),
        DefaultSdk(23, "6.0.1_r3", "r1", "REL", 8, iVersion),
        DefaultSdk(24, "7.0.0_r1", "r1", "REL", 8, iVersion),
        DefaultSdk(25, "7.1.0_r7", "r1", "REL", 8, iVersion),
        DefaultSdk(26, "8.0.0_r4", "r1", "REL", 8, iVersion),
        DefaultSdk(27, "8.1.0", "4611349", "REL", 8, iVersion),
        DefaultSdk(28, "9", "4913185-2", "REL", 8, iVersion),
        DefaultSdk(29, "10", "5803371", "REL", 9, iVersion),
        DefaultSdk(30, "11", "6757853", "REL", 9, iVersion),
        DefaultSdk(31, "12", "7732740", "REL", 9, iVersion),
        DefaultSdk(32, "12.1", "8229987", "REL", 9, iVersion),
        DefaultSdk(33, "13", "9030017", "Tiramisu", 9, iVersion),
      )
      .associateBy { it.apiLevel }
  }

  init {
    group = "slack"
    description = "Downloads the Robolectric android-all jars."
  }

  internal fun sdkFor(version: Int): Sdk {
    return allSdks[version] ?: error("No robolectric jar coordinates found for $version.")
  }

  @TaskAction
  fun download() {
    val workQueue = workerExecutor.noIsolation()
    val destinationDir = outputDir.asFile.get()
    val versions = sdkVersions.get()

    logger.debug("$TAG Downloading robolectric jars")
    destinationDir.apply {
      if (!exists()) {
        mkdirs()
      }
    }

    // Track jars we currently have to which ones we try to download. At the end, we'll delete any
    // we don't want.
    val existingJars = jarsIn(destinationDir).associateByTo(LinkedHashMap(), File::getName)
    val forceReDownload = forceReDownload.get()

    for (version in versions) {
      val sdk = sdkFor(version)
      val dependencyJar = sdk.dependencyJar()
      val jarName = dependencyJar.name
      existingJars.remove(jarName)
      val destinationFile = File(destinationDir, jarName)
      val exists = destinationFile.exists()
      if (exists && !forceReDownload) {
        logger.debug("$TAG Skipping $jarName, already downloaded 👍.")
        continue
      } else {
        if (exists) {
          logger.lifecycle("$TAG Re-downloading $jarName from Maven Central.")
        } else {
          logger.lifecycle("$TAG Downloading $jarName from Maven Central.")
        }
        if (offline.get()) {
          throw IllegalStateException(
            "Missing robolectric jar ${destinationFile.name} but can't" +
              " download it because gradle is in offline mode."
          )
        }
        destinationFile.createNewFile()
        workQueue.submit(DownloadJarAction::class.java) {
          this.dependencyJar.set(dependencyJar)
          this.destinationFile.set(destinationFile)
        }
      }
    }

    workQueue.await()
    existingJars.forEach { (name, file) ->
      logger.lifecycle("Deleting unused Robolectric jar '$name'")
      file.delete()
    }
  }

  internal companion object {
    private const val TAG = "[RobolectricJarsDownloadTask]"

    fun jarsIn(dir: File): Set<File> {
      return dir.listFiles().orEmpty().filterTo(LinkedHashSet()) { it.extension == "jar" }
    }
  }
}

internal interface DownloadJarParameters : WorkParameters {
  val dependencyJar: Property<DependencyJar>
  val destinationFile: RegularFileProperty
}

internal abstract class DownloadJarAction : WorkAction<DownloadJarParameters> {
  override fun execute() {
    // Can't share this from SlackTools because there's a cyclical dependency between GlobalConfig
    // (which creates this task) and SlackTools' availability
    val client =
      OkHttpClient.Builder().connectionPool(ConnectionPool(1, 5, TimeUnit.MINUTES)).build()
    val dependencyJar = parameters.dependencyJar.get()
    val destinationFile = parameters.destinationFile.asFile.get()
    val url =
      with(dependencyJar) {
        "https://repo1.maven.org/maven2/${groupId.replace(".", "/")}/$artifactId/$version/$name"
      }

    val request = Request.Builder().url(url).build()

    // TODO not possible to show progress here because Gradle:
    //  https://github.com/gradle/gradle/issues/3654
    //  Best we could do would be to create a separate task for each jar and show progress for each
    client.newBuilder().build().newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("Unexpected code $response")
      }
      response.body.source().use { source ->
        destinationFile.sink().buffer().use { sink -> sink.writeAll(source) }
      }
    }

    // TODO is this... necessary?
    client.shutdown()
  }
}
