/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
import foundry.gradle.GitCommitValueSource

plugins {
  id("foundry.spotless")
  id("foundry.kotlin-jvm-intellij")
  id("foundry.intellij-platform")
  java
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader) apply false
  alias(libs.plugins.buildConfig)
}

group = "com.slack.intellij"

val gitCommit =
  providers
    .of(GitCommitValueSource::class) {
      parameters.rootDir.set(layout.projectDirectory.dir("../../.."))
    }
    .orElse("")

val versionName = providers.gradleProperty("VERSION_NAME")
val bugsnagKey = providers.gradleProperty("FoundryIntellijBugsnagKey").orElse("")

buildConfig {
  packageName("foundry.intellij.artifactory")
  buildConfigField("String", "CURRENT_VERSION", versionName.map { "\"$it\"" })
  buildConfigField("String", "BUGSNAG_KEY", bugsnagKey.map { "\"$it\"" })
  buildConfigField("String", "GIT_SHA", gitCommit.map { "\"$it\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

version = versionName.get()

intellijPlatform {
  pluginConfiguration {
    vendor {
      name = "Slack"
      url =
        "https://github.com/slackhq/foundry/tree/main/platforms/intellij/artifactory-authenticator"
      email = "oss@slack-corp.com"
    }
  }
}

dependencies {
  intellijPlatform {
    intellijIdea(libs.versions.intellij.version)
    pluginVerifier()
    zipSigner()
  }
}
