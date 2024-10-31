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
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose)
  alias(libs.plugins.kotlin.plugin.compose)
  alias(libs.plugins.lint)
  alias(libs.plugins.roborazzi)
}

roborazzi {
  outputDir.set(
    layout.projectDirectory.dir(
      "src/jvmTest/kotlin/foundry/intellij/compose/playground/snapshots/images"
    )
  )
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
        implementation(libs.circuit.foundation)
        implementation(libs.compose.markdown)
        implementation(libs.jewel.standalone)
        implementation(libs.kotlin.poet)
        implementation(libs.markdown)
        implementation(projects.platforms.intellij.compose)
      }
    }
    jvmTest {
      dependencies {
        api(libs.testing.roborazzi.rules)

        implementation(libs.kotlin.test)
        implementation(libs.roborazzi)
        implementation(libs.testing.roborazzi)
        implementation(libs.testing.roborazzi.core)
        implementation(libs.testing.roborazzi.compose)
        implementation(compose.desktop.common)
        implementation(compose.desktop.uiTestJUnit4)
      }
    }
  }
}

// Tell lint to only resolve the jvm attrs for our compose deps
configurations
  .named { it.endsWith("ForLint") }
  .configureEach { attributes { attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm) } }

dependencies { lintChecks(libs.composeLints) }

// Roborazzi Desktop support uses Context Receivers
// This setup is for JVM test builds only
tasks.named<KotlinCompile>("compileTestKotlinJvm").configure {
  compilerOptions { freeCompilerArgs.add("-Xcontext-receivers") }
}
