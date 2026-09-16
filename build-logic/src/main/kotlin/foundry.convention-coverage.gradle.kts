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
    buildFiles.from(
      isolated.rootProject.projectDirectory.asFileTree.matching {
        include("platforms/**/build.gradle.kts")
        include("tools/**/build.gradle.kts")
      }
    )
    // This module historically did not apply the standalone Android lint plugin.
    lintExemptBuildFiles.add("platforms/intellij/artifactory-authenticator/build.gradle.kts")
  }

tasks.named("check") { dependsOn(verifyConventionPlugins) }
