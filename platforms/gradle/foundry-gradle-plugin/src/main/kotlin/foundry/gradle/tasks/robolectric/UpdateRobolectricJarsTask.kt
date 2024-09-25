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
package foundry.gradle.tasks.robolectric

import foundry.gradle.FoundryProperties
import foundry.gradle.register
import foundry.gradle.robolectricJars
import foundry.gradle.tasks.BootstrapTask
import foundry.gradle.util.setDisallowChanges
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*

/**
 * Updates the Robolectric android-all jars. Is declared as a task dependency of all
 * robolectric-using test tasks.
 *
 * This will download any missing jars, skip any already downloaded ones, and delete any unused
 * existing ones.
 */
@UntrackedTask(because = "State for this is handled elsewhere.")
internal abstract class UpdateRobolectricJarsTask : DefaultTask(), BootstrapTask {

  /**
   * This needs to use [InputFiles] and [PathSensitivity.ABSOLUTE] because the path to the jars
   * really does matter here. Using [Classpath] is an error, as it looks only at content and not
   * name or path, and we really do need to know the actual path to the artifact, even if its
   * contents haven't changed.
   */
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  @get:InputFiles
  abstract val allJars: ListProperty<File>

  @get:Internal abstract val outputDir: DirectoryProperty

  init {
    group = "slack"
    description = "Downloads the Robolectric android-all jars."
  }

  @TaskAction
  fun download() {
    val destinationDir = outputDir.asFile.get()

    logger.debug("$TAG Copying downloaded robolectric jars")
    destinationDir.apply {
      if (!exists()) {
        mkdirs()
      }
    }

    // Track jars we currently have to which ones we try to download. At the end, we'll delete any
    // we don't want.
    val existingJars = jarsIn(destinationDir).associateByTo(LinkedHashMap(), File::getName)
    for (jarToCopy in allJars.get()) {
      existingJars.remove(jarToCopy.name)
      val destinationFile = File(destinationDir, jarToCopy.name)
      if (destinationFile.exists()) {
        logger.debug("$TAG Skipping $jarToCopy, already copied 👍.")
      } else {
        logger.lifecycle("$TAG Copying $jarToCopy to $destinationFile.")
        jarToCopy.copyTo(destinationFile)
      }
    }
    existingJars.forEach { (name, file) ->
      logger.lifecycle("Deleting unused Robolectric jar '$name'")
      file.delete()
    }
  }

  companion object {
    private const val TAG = "[RobolectricJarsDownloadTask]"
    internal const val NAME = "updateRobolectricJars"

    fun jarsIn(dir: File): Set<File> {
      return dir.listFiles().orEmpty().filterTo(LinkedHashSet()) { it.extension == "jar" }
    }

    fun register(
      project: Project,
      foundryProperties: FoundryProperties,
    ): TaskProvider<UpdateRobolectricJarsTask> {
      return project.tasks.register<UpdateRobolectricJarsTask>(NAME) {
        val iVersion = foundryProperties.robolectricIVersion
        for (sdkInt in foundryProperties.robolectricTestSdks) {
          // Create a new configuration
          val sdk = sdkFor(sdkInt, iVersion)
          // Add relevant dep
          val depCoordinates = sdk.dependencyCoordinates
          val configuration =
            project.configurations.detachedConfiguration(
              project.dependencies.create(depCoordinates)
            )
          logger.debug(
            "Adding '$depCoordinates' to Robolectric jars in configuration '${configuration.name}'"
          )
          // Wire to jar classpath
          this.allJars.addAll(configuration.artifactView())
        }
        val gradleUserHomeDir = project.gradle.gradleUserHomeDir
        outputDir.setDisallowChanges(
          project.layout.dir(project.provider { robolectricJars(gradleUserHomeDir) })
        )
      }
    }
  }
}

private fun Configuration.artifactView(): Provider<Set<File>> {
  return incoming
    .artifactView {
      attributes { attribute(Attribute.of("artifactType", String::class.java), "jar") }
    }
    .artifacts
    .resolvedArtifacts
    .map { it.asSequence().map { it.file }.toSet() }
}

private fun sdkFor(api: Int, iVersion: Int): DefaultSdk {
  val sdk = SDKS[api] ?: error("No robolectric jar coordinates found for $api.")
  return sdk.copy(iVersion = iVersion)
}

// Sourced from
// https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
// TODO depend on it directly? compileOnly though
private val SDKS =
  listOf(
      DefaultSdk(21, "5.0.2_r3", "r0", "REL", 8, -1),
      DefaultSdk(22, "5.1.1_r9", "r2", "REL", 8, -1),
      DefaultSdk(23, "6.0.1_r3", "r1", "REL", 8, -1),
      DefaultSdk(24, "7.0.0_r1", "r1", "REL", 8, -1),
      DefaultSdk(25, "7.1.0_r7", "r1", "REL", 8, -1),
      DefaultSdk(26, "8.0.0_r4", "r1", "REL", 8, -1),
      DefaultSdk(27, "8.1.0", "4611349", "REL", 8, -1),
      DefaultSdk(28, "9", "4913185-2", "REL", 8, -1),
      DefaultSdk(29, "10", "5803371", "REL", 9, -1),
      DefaultSdk(30, "11", "6757853", "REL", 9, -1),
      DefaultSdk(31, "12", "7732740", "REL", 9, -1),
      DefaultSdk(32, "12.1", "8229987", "REL", 9, -1),
      DefaultSdk(33, "13", "9030017", "Tiramisu", 9, -1),
      DefaultSdk(34, "14", "10818077", "REL", 17, -1),
    )
    .associateBy { it.apiLevel }
