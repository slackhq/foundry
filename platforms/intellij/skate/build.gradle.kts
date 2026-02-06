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
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
  id("foundry.spotless")
  id("foundry.kotlin-jvm-intellij")
  alias(libs.plugins.kotlin.plugin.serialization)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader)
  alias(libs.plugins.buildConfig)
  alias(libs.plugins.lint)
}

group = "com.slack.intellij"

intellijPlatform {
  pluginConfiguration {
    vendor {
      name = "Slack"
      url = "https://github.com/slackhq/foundry/tree/main/platforms/intellij/skate"
      email = "oss@slack-corp.com"
    }
  }
}

val gitCommit =
  providers
    .of(GitCommitValueSource::class) {
      parameters.rootDir.set(layout.projectDirectory.dir("../../.."))
    }
    .orElse("")

val versionName = providers.gradleProperty("VERSION_NAME")
val bugsnagKey = providers.gradleProperty("FoundryIntellijBugsnagKey").orElse("")

buildConfig {
  packageName("foundry.intellij.skate")
  buildConfigField("String", "VERSION", versionName.map { "\"$it\"" })
  buildConfigField("String", "BUGSNAG_KEY", bugsnagKey.map { "\"$it\"" })
  buildConfigField("String", "GIT_SHA", gitCommit.map { "\"$it\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

// TODO reconcile exclusions and this by figuring out which configurations need to exclude
//  coroutines. https://youtrack.jetbrains.com/issue/IJPL-163489
configurations.configureEach {
  // Do not bring in Material (we use Jewel)
  exclude(group = "org.jetbrains.compose.material")
  // Do not bring Coroutines or slf4j (the IDE has its own)
  exclude(group = "org.slf4j")
}

val exclusions: Action<ModuleDependency> = Action {
  // Do not bring in Material (we use Jewel)
  exclude(group = "org.jetbrains.compose.material")
  // Do not bring Coroutines or slf4j (the IDE has its own)
  exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
  exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
  exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
  exclude(group = "org.slf4j")
}

configurations
  .named { it.endsWith("ForLint") }
  .configureEach { attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) } }

dependencies {
  intellijPlatform {
    intellijIdeaCommunity(libs.versions.intellij.version)
    plugin(libs.versions.intellij.android.map { "org.jetbrains.android:${it}" }.get())
    bundledPlugins(
      "com.intellij.java",
      "org.intellij.plugins.markdown",
      "org.jetbrains.plugins.terminal",
      "org.jetbrains.kotlin",
      "org.jetbrains.plugins.gradle",
    )

    pluginVerifier()
    zipSigner()

    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Bundled)

    val runLocalIde by
      intellijPlatformTesting.runIde.registering {
        // https://plugins.jetbrains.com/docs/intellij/android-studio.html#configuring-the-plugin-gradle-build-script
        providers.gradleProperty("intellijPlatformTesting.idePath").orNull?.let {
          localPath.set(file(it))
        }
      }
  }
  implementation(project(":platforms:intellij:compose"), exclusions)
  implementation(project(":tools:tracing"), exclusions)
  implementation(project(":tools:foundry-common"), exclusions)

  implementation(libs.bugsnag)
  implementation(libs.kaml)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.okhttp)
  implementation(libs.okhttp.loggingInterceptor)
  implementation(libs.okio)

  compileOnly(libs.coroutines.core.ij)

  testImplementation(libs.junit)
  testImplementation(libs.truth)

  lintChecks(libs.composeLints)
}
