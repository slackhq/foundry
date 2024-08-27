/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package slack.gradle.kgp

import com.android.build.gradle.BaseExtension
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import slack.gradle.Configurations
import slack.gradle.Configurations.isKnownConfiguration
import slack.gradle.SlackProperties
import slack.gradle.SlackTools
import slack.gradle.asProvider
import slack.gradle.configure
import slack.gradle.lint.DetektTasks
import slack.gradle.onFirst
import slack.gradle.util.configureKotlinCompilationTask

/** Common configuration for Kotlin projects. */
internal object KgpTasks {
  private val KGP_PLUGINS =
    listOf(
      "org.jetbrains.kotlin.multiplatform",
      "org.jetbrains.kotlin.jvm",
      "org.jetbrains.kotlin.android",
    )

  @Suppress("LongMethod")
  fun configure(project: Project, slackTools: SlackTools, slackProperties: SlackProperties) {
    project.pluginManager.onFirst(KGP_PLUGINS) {
      val kotlinExtension = project.kotlinExtension
      kotlinExtension.apply {
        kotlinDaemonJvmArgs = slackTools.globalConfig.kotlinDaemonArgs
        slackProperties.versions.jdk.ifPresent { jdkVersion ->
          jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
            slackTools.globalConfig.jvmVendor?.let(vendor::set)
          }
        }
      }

      val isKotlinAndroid = kotlinExtension is KotlinAndroidProjectExtension

      project.tasks.configureKotlinCompilationTask(includeKaptGenerateStubsTask = true) {
        // Don't add compiler args to KaptGenerateStubsTask because it inherits arguments from the
        // target compilation
        val isKaptGenerateStubsTask = this is KaptGenerateStubsTask

        compilerOptions {
          if (isKaptGenerateStubsTask && slackProperties.kaptLanguageVersion.isPresent) {
            val zipped =
              slackProperties.kotlinProgressive.zip(slackProperties.kaptLanguageVersion) {
                progressive,
                kaptLanguageVersion ->
                if (kaptLanguageVersion != KotlinVersion.DEFAULT) {
                  false
                } else {
                  progressive
                }
              }
            progressiveMode.set(zipped)
          } else {
            progressiveMode.set(slackProperties.kotlinProgressive)
          }
          optIn.addAll(slackProperties.kotlinOptIn)
          if (slackProperties.kotlinLanguageVersionOverride.isPresent) {
            languageVersion.set(
              slackProperties.kotlinLanguageVersionOverride.map(KotlinVersion::fromVersion)
            )
          } else if (isKaptGenerateStubsTask && slackProperties.kaptLanguageVersion.isPresent) {
            languageVersion.set(slackProperties.kaptLanguageVersion)
          }
          if (
            !slackProperties.allowWarnings &&
              !this@configureKotlinCompilationTask.name.contains("test", ignoreCase = true)
          ) {
            allWarningsAsErrors.set(true)
          }
          if (!isKaptGenerateStubsTask) {
            freeCompilerArgs.addAll(slackProperties.kotlinFreeArgs)
          }

          val jvmTargetProvider =
            slackProperties.versions.jvmTarget
              .map { JvmTarget.fromTarget(it.toString()) }
              .asProvider(project.providers)
          if (this is KotlinJvmCompilerOptions) {
            jvmTarget.set(jvmTargetProvider)
            // Potentially useful for static analysis or annotation processors
            javaParameters.set(true)
            freeCompilerArgs.addAll(slackProperties.kotlinJvmFreeArgs)

            // Set the module name to a dashified version of the project path to ensure uniqueness
            // in created .kotlin_module files
            moduleName.set(project.path.replace(":", "-"))

            if (!isKotlinAndroid) {
              // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
              freeCompilerArgs.add(jvmTargetProvider.map { "-Xjdk-release=${it.target}" })
            }
          }
        }
      }

      project.configureFreeKotlinCompilerArgs()

      if (slackProperties.autoApplyDetekt) {
        project.project.pluginManager.apply("io.gitlab.arturbosch.detekt")
      }
    }

    DetektTasks.configureSubProject(
      project.project,
      slackProperties,
      slackTools.globalConfig.affectedProjects,
    )

    project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
      // Configure kotlin sources in Android projects
      project.configure<BaseExtension> {
        this.sourceSets.configureEach {
          val nestedSourceDir = "src/${this.name}/kotlin"
          val dir = File(project.projectDir, nestedSourceDir)
          if (dir.exists()) {
            // Standard source set
            // Only added if it exists to avoid potentially adding empty source dirs
            this.java.srcDirs(project.layout.projectDirectory.dir(nestedSourceDir))
          }
        }
      }
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.android.extensions") {
      throw GradleException(
        "Don't use the deprecated 'android.extensions' plugin, switch to " +
          "'plugin.parcelize' instead."
      )
    }

    project.pluginManager.withPlugin("org.jetbrains.kotlin.kapt") {
      project.configure<KaptExtension> {
        // By default, Kapt replaces unknown types with `NonExistentClass`. This flag asks kapt
        // to infer the type, which is useful for processors that reference to-be-generated
        // classes.
        // https://kotlinlang.org/docs/reference/kapt.html#non-existent-type-correction
        this.correctErrorTypes = true

        // Maps source errors to Kotlin sources rather than Java stubs
        // Disabled because this triggers a bug in kapt on android 30
        // https://github.com/JetBrains/kotlin/pull/3610
        this.mapDiagnosticLocations = false
      }

      // See doc on the property for details
      if (!slackProperties.enableKaptInTests) {
        project.tasks.configureEach {
          if (name.startsWith("kapt") && name.endsWith("TestKotlin", ignoreCase = true)) {
            enabled = false
          }
        }
      }
    }
  }

  /**
   * Configures per-dependency free Kotlin compiler args. This is necessary because otherwise
   * kotlinc will emit angry warnings.
   */
  private fun Project.configureFreeKotlinCompilerArgs() {
    logger.debug("Configuring specific Kotlin compiler args on $path")
    val once = AtomicBoolean()
    configurations.configureEach {
      if (isKnownConfiguration(name, Configurations.Groups.RUNTIME)) {
        incoming.afterResolve {
          dependencies.forEach { dependency ->
            KotlinArgConfigs.ALL[dependency.name]?.let { config ->
              if (once.compareAndSet(false, true)) {
                tasks.configureKotlinCompilationTask {
                  compilerOptions { freeCompilerArgs.addAll(config.args) }
                }
              }
            }
          }
        }
      }
    }
  }

  interface KotlinArgConfig {
    val targetDependency: String
    val args: Set<String>
  }

  @Suppress("unused") // Nested classes here are looked up reflectively
  object KotlinArgConfigs {

    val ALL: Map<String, KotlinArgConfig> by lazy {
      KotlinArgConfigs::class
        .nestedClasses
        .map { it.objectInstance }
        .filterIsInstance<KotlinArgConfig>()
        .associateBy { it.targetDependency }
    }

    object Coroutines : KotlinArgConfig {
      override val targetDependency: String = "kotlinx-coroutines-core"
      override val args =
        setOf(
          "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
          "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
  }
}
