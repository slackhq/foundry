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
  alias(libs.plugins.pluginUploader)
  alias(libs.plugins.buildConfig)
  alias(libs.plugins.compose)
  alias(libs.plugins.lint)
}

group = "com.slack.intellij"

repositories {
  mavenCentral()
  google()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  plugins.add("org.intellij.plugins.markdown")
  plugins.add("org.jetbrains.plugins.terminal")
  plugins.add("org.jetbrains.kotlin")
}

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
  } catch (e: Exception) {
    return null
  }
}

buildConfig {
  packageName("com.slack.sgp.intellij")
  buildConfigField("String", "VERSION", "\"${project.property("VERSION_NAME")}\"")
  buildConfigField(
    "String",
    "BUGSNAG_KEY",
    "\"${project.findProperty("SgpIntellijBugsnagKey")?.toString().orEmpty()}\"",
  )
  buildConfigField("String", "GIT_SHA", provider { "\"${readGitRepoCommit().orEmpty()}\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

dependencies {
  lintChecks(libs.composeLints)
  implementation(compose.animation)
  implementation(compose.desktop.currentOs)
  implementation(compose.foundation)
  implementation(compose.material)
  implementation(compose.material3)
  implementation(compose.ui)
  implementation(libs.bugsnag) { exclude(group = "org.slf4j") }
  implementation(libs.circuit)
  implementation(libs.gradlePlugins.compose)
  implementation(libs.kotlin.poet)
  implementation(libs.okhttp)
  implementation(libs.okhttp.loggingInterceptor)
  implementation(projects.tracing)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
