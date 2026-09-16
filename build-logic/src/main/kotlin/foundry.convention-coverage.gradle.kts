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
import foundry.buildlogic.VerifyConventionPluginsTask

plugins { base }

val verifyConventionPlugins =
  tasks.register<VerifyConventionPluginsTask>("verifyConventionPlugins") {
    val rootDirectory = isolated.rootProject.projectDirectory
    buildFileContents.set(
      providers.provider {
        sequenceOf("platforms", "tools")
          .flatMap { sourceRoot ->
            rootDirectory
              .dir(sourceRoot)
              .asFile
              .walkTopDown()
              .onEnter { directory -> directory.name !in setOf("build", ".gradle") }
              .filter { file -> file.isFile && file.name == "build.gradle.kts" }
          }
          .associate { buildFile ->
            val relativePath = buildFile.relativeTo(rootDirectory.asFile).invariantSeparatorsPath
            relativePath to buildFile.readText()
          }
      }
    )
    // This module historically did not apply the standalone Android lint plugin.
    lintExemptBuildFiles.add("platforms/intellij/artifactory-authenticator/build.gradle.kts")
  }

tasks.named("check") { dependsOn(verifyConventionPlugins) }
