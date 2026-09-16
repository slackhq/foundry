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
package foundry.buildlogic

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal fun parseJdkVersion(version: String): Int = version.removeSuffix("-ea").toInt()

internal fun Project.configureKotlinJvmConvention(
  jvmTargetVersion: JvmTarget,
  jdkVersion: Int,
  languageVersion: KotlinVersion,
  allWarningsAsErrors: Boolean,
  useExplicitApi: Boolean,
  gradleCompatibility: Boolean = false,
) {
  if (useExplicitApi) {
    extensions.configure<KotlinJvmProjectExtension> { explicitApi() }
  }

  extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.release.set(jvmTargetVersion.target.toInt())
  }

  tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
      this.languageVersion.set(languageVersion)
      apiVersion.set(languageVersion)
      this.allWarningsAsErrors.set(allWarningsAsErrors)

      check(this is KotlinJvmCompilerOptions)
      jvmTarget.set(jvmTargetVersion)
      jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
      if (gradleCompatibility) {
        freeCompilerArgs.addAll("-Xsam-conversions=class", "-Xlambdas=class")
      }
      freeCompilerArgs.addAll(
        "-Xjsr305=strict",
        "-Xassertions=jvm",
        "-Xemit-jvm-type-annotations",
        "-Xjspecify-annotations=strict",
        "-Xjdk-release=${jvmTargetVersion.target}",
      )
      optIn.addAll(
        "kotlin.contracts.ExperimentalContracts",
        "kotlin.experimental.ExperimentalTypeInference",
        "kotlin.ExperimentalStdlibApi",
        "kotlin.time.ExperimentalTime",
      )
    }
  }

  pluginManager.withPlugin("io.gitlab.arturbosch.detekt") {
    tasks.withType<Detekt>().configureEach { jvmTarget = jvmTargetVersion.target }
  }
}
