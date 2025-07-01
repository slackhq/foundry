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
import com.jetbrains.plugin.structure.base.utils.exists
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.readText

plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader) apply false
  alias(libs.plugins.buildConfig)
}

group = "com.slack.intellij"

fun isGitHash(hash: String): Boolean {
  if (hash.length != 40) {
    return false
  }

  return hash.all { it in '0'..'9' || it in 'a'..'f' }
}

// Impl from https://gist.github.com/madisp/6d753bde19e278755ec2b69ccfc17114
fun readGitRepoCommit(): String? {
  try {
    val head = Paths.get("${rootProject.projectDir}/.git").resolve("HEAD")
    if (!head.exists()) {
      return null
    }

    val headContents = head.readText(Charsets.UTF_8).lowercase(Locale.US).trim()

    if (isGitHash(headContents)) {
      return headContents
    }

    if (!headContents.startsWith("ref:")) {
      return null
    }

    val headRef = headContents.removePrefix("ref:").trim()
    val headFile = Paths.get(".git").resolve(headRef)
    if (!headFile.exists()) {
      return null
    }

    return headFile.readText(Charsets.UTF_8).trim().takeIf { isGitHash(it) }
  } catch (_: Exception) {
    return null
  }
}

buildConfig {
  packageName("foundry.intellij.artifactory")
  buildConfigField("String", "CURRENT_VERSION", "\"${project.property("VERSION_NAME")}\"")
  buildConfigField(
    "String",
    "BUGSNAG_KEY",
    "\"${project.findProperty("FoundryIntellijBugsnagKey")?.toString().orEmpty()}\"",
  )
  buildConfigField("String", "GIT_SHA", provider { "\"${readGitRepoCommit().orEmpty()}\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

version = property("VERSION_NAME").toString()

intellijPlatform {
  pluginConfiguration {
    name = "Artifactory Authenticator"
    id = "com.slack.intellij.artifactory"
    version = property("VERSION_NAME").toString()
    description = "A plugin for authenticating plugin repositories with Artifactory."
    vendor {
      name = "Slack"
      url =
        "https://github.com/slackhq/foundry/tree/main/platforms/intellij/artifactory-authenticator"
      email = "oss@slack-corp.com"
    }
  }
}

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
