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
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.buildConfig)
  alias(libs.plugins.compose)
  alias(libs.plugins.lint)
}

kotlin {
  jvm()

  sourceSets {
    jvmMain {
      dependencies {
        implementation(compose.animation)
        implementation(compose.desktop.common)
        implementation(compose.desktop.linux_arm64)
        implementation(compose.desktop.linux_x64)
        implementation(compose.desktop.macos_arm64)
        implementation(compose.desktop.macos_x64)
        implementation(compose.desktop.windows_x64)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(libs.circuit)
        implementation(libs.kotlin.poet)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.truth)
      }
    }
  }
}

configure<ComposeExtension> {
  val kotlinVersion = libs.versions.kotlin.get()
  // Flag to disable Compose's kotlin version check because they're often behind
  // Or ahead
  // Or if they're the same, do nothing
  // It's basically just very noisy.
  kotlinCompilerPlugin.set(libs.compose.compilerJb.map { it.toString() })
  val suppressComposeKotlinVersion = kotlinVersion != libs.versions.compose.jb.kotlinVersion.get()
  if (suppressComposeKotlinVersion) {
    kotlinCompilerPluginArgs.add("suppressKotlinVersionCompatibilityCheck=$kotlinVersion")
  }
}

// Tell lint to only resolve the jvm attrs for our compose deps
configurations
  .named { it.endsWith("ForLint") }
  .configureEach { attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) } }

dependencies { lintChecks(libs.composeLints) }
