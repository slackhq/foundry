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
import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
  id("org.jetbrains.dokka")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val isForGradle = project.path.startsWith(":platforms:gradle")
val rootDirectory = isolated.rootProject.projectDirectory
val projectDirectory = layout.projectDirectory

pluginManager.withPlugin("com.vanniktech.maven.publish") {
  configure<DokkaExtension> {
    basePublicationsDirectory.set(layout.buildDirectory.dir("dokkaDir"))
    dokkaSourceSets.configureEach {
      documentedVisibilities.add(VisibilityModifier.Public)
      skipDeprecated.set(true)
      if (isForGradle) {
        externalDocumentationLinks.register("Gradle") {
          packageListUrl("https://docs.gradle.org/${gradle.gradleVersion}/javadoc/element-list")
          url("https://docs.gradle.org/${gradle.gradleVersion}/javadoc")
        }
        externalDocumentationLinks.register("AGP") {
          val simpleApi =
            catalog.findVersion("agp").get().toString().split(".").take(2).joinToString(".")
          packageListUrl(
            "https://developer.android.com/reference/tools/gradle-api/$simpleApi/package-list"
          )
          url("https://developer.android.com/reference/tools/gradle-api/$simpleApi/classes")
        }
      }
      sourceLink {
        localDirectory.set(projectDirectory.dir("src"))
        val relPath =
          rootDirectory.asFile.toPath().relativize(projectDirectory.asFile.toPath()).toString()
        remoteUrl(
          providers.gradleProperty("POM_SCM_URL").map { scmUrl -> "$scmUrl/tree/main/$relPath/src" }
        )
        remoteLineSuffix.set("#L")
      }
    }
  }

  configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.NONE)
    signAllPublications()
  }
}
