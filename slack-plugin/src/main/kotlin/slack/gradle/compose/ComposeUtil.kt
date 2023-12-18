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
package slack.gradle.compose

import org.gradle.api.Project
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import slack.gradle.SlackProperties
import slack.gradle.configure
import slack.gradle.util.configureKotlinCompilationTask

private const val COMPOSE_COMPILER_GOOGLE_GROUP = "androidx.compose.compiler"
private const val COMPOSE_COMPILER_JB_GROUP = "org.jetbrains.compose.compiler"
internal const val COMPOSE_COMPILER_OPTION_PREFIX =
  "plugin:androidx.compose.compiler.plugins.kotlin"

/**
 * The compose compiler has an extremely irritating version checking mechanism that requires a bunch
 * of boilerplate below to suppress. This is further magnified by the fact that Compose
 * Multiplatform has its own compiler artifact, and we have to suppress the version check
 * appropriately for each depending on which is applied. Finally, we also sometimes switch versions
 * used depending on which one has better support for the current Kotlin version at the time (see
 * [SlackProperties.forceAndroidXComposeCompilerForComposeMultiplatform]).
 */
internal fun Project.configureComposeCompiler(
  slackProperties: SlackProperties,
  isMultiplatform: Boolean
) {
  val kotlinVersion = slackProperties.versions.kotlin
  // Flag to disable Compose's kotlin version check because they're often behind
  // Or ahead
  // Or if they're the same, do nothing
  // It's basically just very noisy.
  val (compilerDep, composeCompilerKotlinVersion) =
    if (isMultiplatform && !slackProperties.forceAndroidXComposeCompilerForComposeMultiplatform) {
      // JB version
      val composeJbDepVersion =
        slackProperties.versions.composeJb
          ?: error("No compose-jb version defined in libs.versions.toml")
      val composeJbKotlinVersion =
        slackProperties.versions.composeJbKotlinVersion
          ?: error("No compose-jb-kotlinVersion version defined in libs.versions.toml")
      "$COMPOSE_COMPILER_JB_GROUP:compiler:$composeJbDepVersion" to composeJbKotlinVersion
    } else {
      // Google version
      val composeDepVersion =
        slackProperties.versions.composeCompiler
          ?: error("No compose-compiler version defined in libs.versions.toml")
      val composeKotlinVersion =
        slackProperties.versions.composeCompilerKotlinVersion
          ?: error("No compose-compiler-kotlinVersion version defined in libs.versions.toml")
      "$COMPOSE_COMPILER_GOOGLE_GROUP:compiler:$composeDepVersion" to composeKotlinVersion
    }

  if (isMultiplatform) {
    configure<ComposeExtension> { kotlinCompilerPlugin.set(compilerDep) }
  }

  val suppressComposeKotlinVersion = kotlinVersion != composeCompilerKotlinVersion
  if (suppressComposeKotlinVersion) {
    tasks.configureKotlinCompilationTask {
      compilerOptions {
        logger.debug(
          "Configuring compose compiler args in ${project.path}:${this@configureKotlinCompilationTask.name}"
        )
        if (this is KotlinJvmCompilerOptions) {
          freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-P",
            "$COMPOSE_COMPILER_OPTION_PREFIX:suppressKotlinVersionCompatibilityCheck=$kotlinVersion"
          )
        }
      }
    }
  } else {
    logger.debug(
      "Not configuring compose compiler args in ${project.path}, kotlin and compose compiler versions are aligned"
    )
  }

  if (isMultiplatform) {
    // Force the compiler plugin dep + version we're using in KMP projects
    dependencies.apply {
      add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, compilerDep)
      add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, compilerDep)
    }

    // Some Gradle plugins, such as Mosaic, masquerade as the Compose compiler plugin and bring the
    // version we don't want, so we force all the compiler plugin classpaths to exclude the one
    // we're _not_ using here.
    configurations
      .matching { it.name.startsWith("kotlinCompilerPluginClasspath") }
      .configureEach {
        val group =
          if (slackProperties.forceAndroidXComposeCompilerForComposeMultiplatform) {
            COMPOSE_COMPILER_JB_GROUP
          } else {
            COMPOSE_COMPILER_GOOGLE_GROUP
          }
        logger.debug("Excluding compose compiler plugin group '$group' from configuration '$name'")
        exclude(mapOf("group" to group, "module" to "compiler"))
      }
  }
}
