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
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.robolectricJars
import foundry.gradle.tasks.BootstrapTask
import foundry.tools.robolectric.sdk.management.RobolectricSdkAccess
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask

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
    group = "foundry"
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
        for (depCoordinates in
          RobolectricSdkAccess.loadSdks(foundryProperties.robolectricTestSdks)) {
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
