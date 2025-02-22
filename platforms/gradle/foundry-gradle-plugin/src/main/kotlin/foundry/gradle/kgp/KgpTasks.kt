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
package foundry.gradle.kgp

import com.android.build.gradle.BaseExtension
import foundry.gradle.Configurations
import foundry.gradle.Configurations.isKnownConfiguration
import foundry.gradle.FoundryProperties
import foundry.gradle.FoundryTools
import foundry.gradle.asProvider
import foundry.gradle.configure
import foundry.gradle.getByType
import foundry.gradle.lint.DetektTasks
import foundry.gradle.not
import foundry.gradle.onFirst
import foundry.gradle.util.configureKotlinCompilationTask
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/** Common configuration for Kotlin projects. */
internal object KgpTasks {
  private val KGP_PLUGINS =
    listOf(
      "org.jetbrains.kotlin.multiplatform",
      "org.jetbrains.kotlin.jvm",
      "org.jetbrains.kotlin.android",
    )

  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  @Suppress("LongMethod")
  fun configure(
    project: Project,
    foundryTools: FoundryTools,
    foundryProperties: FoundryProperties,
  ) {
    project.pluginManager.onFirst(KGP_PLUGINS) {
      val kotlinExtension = project.extensions.getByType<KotlinBaseExtension>()
      kotlinExtension.apply {
        foundryTools.globalConfig.kotlinDaemonArgs?.let { kotlinDaemonJvmArgs = it }
        foundryProperties.versions.jdk.ifPresent { jdkVersion ->
          jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(jdkVersion))
            foundryTools.globalConfig.jvmVendor?.let(vendor::set)
          }
        }
      }

      val isKotlinAndroid = kotlinExtension is KotlinAndroidProjectExtension

      val jvmTargetProvider =
        foundryProperties.versions.jvmTarget
          .map { JvmTarget.fromTarget(it.toString()) }
          .asProvider(project.providers)

      kotlinExtension.explicitApi =
        foundryProperties.kotlinExplicitApiMode.orNull?.let(ExplicitApiMode::valueOf)

      val jvmConfig: KotlinJvmCompilerOptions.() -> Unit = {
        project.logger.lifecycle("Configuring jvm compiler options for ${project.path}")
        jvmTarget.set(jvmTargetProvider)

        // Potentially useful for static analysis or annotation processors
        javaParameters.set(true)

        freeCompilerArgs.addAll(foundryProperties.kotlinJvmFreeArgs)

        // Set the module name to a dashified version of the project path to ensure uniqueness
        // in created .kotlin_module files
        moduleName.set(project.path.replace(":", "-"))

        if (!isKotlinAndroid) {
          // https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
          freeCompilerArgs.add(jvmTargetProvider.map { "-Xjdk-release=${it.target}" })
        }
      }

      if (kotlinExtension is HasConfigurableKotlinCompilerOptions<*>) {
        kotlinExtension.compilerOptions {
          progressiveMode.set(foundryProperties.kotlinProgressive)
          optIn.addAll(foundryProperties.kotlinOptIn)
          if (foundryProperties.kotlinLanguageVersionOverride.isPresent) {
            languageVersion.set(
              foundryProperties.kotlinLanguageVersionOverride.map(KotlinVersion::fromVersion)
            )
          }
          freeCompilerArgs.addAll(foundryProperties.kotlinFreeArgs)
          project.configureFreeKotlinCompilerArgs(freeCompilerArgs)

          if (this is KotlinJvmCompilerOptions) {
            jvmConfig()
          }
        }
      }

      // In KMP projects, compiler options are per-target
      if (kotlinExtension is KotlinMultiplatformExtension) {
        kotlinExtension.targets.configureEach {
          if (this is HasConfigurableKotlinCompilerOptions<*>) {
            compilerOptions {
              if (this is KotlinJvmCompilerOptions) {
                jvmConfig()
              }
            }
          }
        }
      }

      project.tasks.configureKotlinCompilationTask {
        compilerOptions {
          // These are set here because they're task-specific
          // Don't use setDisallowChanges because KGP may sync conventions later and that blocks
          // that
          if (this@configureKotlinCompilationTask.name.contains("test", ignoreCase = true)) {
            allWarningsAsErrors.set(foundryProperties.allowWarningsInTests.not())
          } else {
            allWarningsAsErrors.set(foundryProperties.allowWarnings.not())
          }
        }
      }

      if (foundryProperties.autoApplyDetekt) {
        project.project.pluginManager.apply("io.gitlab.arturbosch.detekt")
      }
    }

    DetektTasks.configureSubProject(
      project.project,
      foundryProperties,
      foundryTools.globalConfig.affectedProjects,
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
      if (!foundryProperties.enableKaptInTests) {
        project.tasks.configureEach {
          if (name.startsWith("kapt") && name.endsWith("TestKotlin", ignoreCase = true)) {
            enabled = false
          }
        }
      }

      project.tasks.withType(KaptGenerateStubsTask::class.java).configureEach {
        compilerOptions {
          val zipped =
            foundryProperties.kotlinProgressive.zip(
              foundryProperties.kaptLanguageVersion.orElse(KotlinVersion.DEFAULT)
            ) { progressive, kaptLanguageVersion ->
              if (kaptLanguageVersion != KotlinVersion.DEFAULT) {
                false
              } else {
                progressive
              }
            }
          progressiveMode.set(zipped)

          if (foundryProperties.kaptLanguageVersion.isPresent) {
            languageVersion.set(foundryProperties.kaptLanguageVersion)
          }
        }
      }
    }
  }

  /**
   * Configures per-dependency free Kotlin compiler args. This is necessary because otherwise
   * kotlinc will emit angry warnings.
   */
  private fun Project.configureFreeKotlinCompilerArgs(freeCompilerArgs: ListProperty<String>) {
    logger.debug("Configuring specific Kotlin compiler args on $path")
    val once = AtomicBoolean()
    configurations.configureEach {
      if (isKnownConfiguration(name, Configurations.Groups.RUNTIME)) {
        incoming.afterResolve {
          dependencies.forEach { dependency ->
            KotlinArgConfigs.ALL[dependency.name]?.let { config ->
              if (once.compareAndSet(false, true)) {
                freeCompilerArgs.addAll(config.args)
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
