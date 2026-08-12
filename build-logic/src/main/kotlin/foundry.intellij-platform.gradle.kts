/*
 * Copyright (C) 2026 Slack Technologies, LLC
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
import dev.bmac.gradle.intellij.GenerateBlockMapTask
import dev.bmac.gradle.intellij.PluginUploader
import dev.bmac.gradle.intellij.UploadPluginTask
import java.util.Base64
import java.util.Properties
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val localProperties =
  providers.fileContents(layout.projectDirectory.file("gradle.properties")).asText.map { text ->
    Properties().apply { load(text.reader()) }
  }

fun pluginProperty(name: String) =
  providers
    .gradleProperty(name)
    .orElse(
      localProperties.map { properties ->
        requireNotNull(properties.getProperty(name)) {
          "Missing '$name' in ${layout.projectDirectory.file("gradle.properties").asFile}."
        }
      }
    )

pluginManager.withPlugin("org.jetbrains.intellij.platform") {
  data class PluginDetails(
    val pluginId: String,
    val name: String,
    val description: String,
    val version: String,
    val sinceBuild: String,
    val urlSuffix: String,
  )

  val pluginDetails =
    PluginDetails(
      pluginId = pluginProperty("PLUGIN_ID").get(),
      name = pluginProperty("PLUGIN_NAME").get(),
      description = pluginProperty("PLUGIN_DESCRIPTION").get(),
      version = pluginProperty("VERSION_NAME").get(),
      sinceBuild = catalog.findVersion("intellij.sinceBuild").get().toString(),
      urlSuffix = pluginProperty("ARTIFACTORY_URL_SUFFIX").get(),
    )

  configure<IntelliJPlatformExtension> {
    pluginConfiguration {
      id.set(pluginDetails.pluginId)
      name.set(pluginDetails.name)
      version.set(pluginDetails.version)
      description.set(pluginDetails.description)
      ideaVersion {
        sinceBuild.set(pluginDetails.sinceBuild)
        untilBuild.set(project.provider { null })
      }
    }
  }

  val artifactoryBaseUrl = providers.gradleProperty("FoundryIntellijArtifactoryBaseUrl")
  if (artifactoryBaseUrl.isPresent) {
    pluginManager.apply("dev.bmac.intellij.plugin-uploader")

    val archive = tasks.named<BuildPluginTask>("buildPlugin").flatMap { it.archiveFile }
    val blockMapTask =
      tasks.named<GenerateBlockMapTask>(GenerateBlockMapTask.TASK_NAME) {
        notCompatibleWithConfigurationCache(
          "Blockmap generation is not compatible with the configuration cache"
        )
        file.set(archive)
        blockmapFile.set(
          layout.buildDirectory.file(
            "blockmap/blockmap${GenerateBlockMapTask.BLOCKMAP_FILE_SUFFIX}"
          )
        )
        blockmapHashFile.set(
          layout.buildDirectory.file("blockmap/blockmap${GenerateBlockMapTask.HASH_FILE_SUFFIX}")
        )
      }

    tasks.register<UploadPluginTask>("uploadPluginToArtifactory") {
      notCompatibleWithConfigurationCache(
        "UploadPluginTask is not compatible with the configuration cache"
      )
      dependsOn(blockMapTask)
      blockmapFile.set(blockMapTask.flatMap { it.blockmapFile })
      blockmapHashFile.set(blockMapTask.flatMap { it.blockmapHashFile })
      url.set(artifactoryBaseUrl.map { baseUrl -> "$baseUrl/${pluginDetails.urlSuffix}" })
      pluginName.set(pluginDetails.name)
      file.set(archive)
      repoType.set(PluginUploader.RepoType.REST_PUT)
      pluginId.set(pluginDetails.pluginId)
      version.set(pluginDetails.version)
      pluginDescription.set(pluginDetails.description)
      val changeNotesFile = file("change-notes.html")
      if (changeNotesFile.exists()) {
        changeNotes.set(changeNotesFile.readText())
      }
      sinceBuild.set(pluginDetails.sinceBuild)
      authentication.set(
        providers.gradleProperty("FoundryIntellijArtifactoryUsername").zip(
          providers.gradleProperty("FoundryIntellijArtifactoryToken")
        ) { username, token ->
          "Basic ${Base64.getEncoder().encodeToString("$username:$token".toByteArray())}"
        }
      )
    }
  }
}
