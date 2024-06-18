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
package slack.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BuildType
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import slack.gradle.AptOptionsConfig.AptOptionsConfigurer

/** Common configuration for annotation processing, including kapt and javac options. */
internal object AnnotationProcessing {
  private val APT_OPTION_CONFIGS: Map<String, AptOptionsConfig> =
    AptOptionsConfigs().associateBy { it.targetDependency }

  /** Common configuration for annotation processors, such as standard options. */
  fun configureFor(project: Project) {
    project.logger.debug("Configuring any annotation processors on ${project.path}")
    val configs = APT_OPTION_CONFIGS.mapValues { (_, value) -> value.newConfigurer(project) }
    project.configurations.configureEach {
      // Try common case first
      val isApt =
        name in Configurations.Groups.APT ||
          // Try custom configs like testKapt, debugAnnotationProcessor, etc.
          Configurations.Groups.APT.any { name.endsWith(it, ignoreCase = true) }
      if (isApt) {
        val context = ConfigurationContext(project, this@configureEach)
        incoming.afterResolve {
          dependencies.forEach { dependency -> configs[dependency.name]?.configure(context) }
        }
      }
    }
  }
}

/**
 * A common interface for configuration of annotation processors. It's recommended to make
 * implementers of this interface `object` types. The pipeline for configuration of projects will
 * appropriately call [newConfigurer] per-project to create a project-local context.
 */
internal interface AptOptionsConfig {

  /**
   * The targeted dependency of this config. This should be treated as the maven artifact ID of the
   * dependency, such as "dagger-compiler". This should ideally also be a constant.
   */
  val targetDependency: String

  /** @return a new [AptOptionsConfigurer] for this config on the target [project]. */
  fun newConfigurer(project: Project): AptOptionsConfigurer

  interface AptOptionsConfigurer {

    val project: Project

    /**
     * Configure appropriate annotation processor options on the given [project] given the current
     * [configurationContext].
     */
    fun configure(configurationContext: ConfigurationContext)
  }
}

/**
 * A basic [BasicAptOptionsConfig] that makes setup ceremony easier for common cases. This tries to
 * ensure an optimized configuration that avoids object/action allocations and simplify wiring into
 * our different common project types.
 *
 * The general usage that you define a top level object that extends this and override the necessary
 * properties.
 *
 * ```
 * object ButterKnifeAptOptionsConfig : BasicAptOptionsConfig() {
 *   override val targetDependency: String = "butterknife-compiler"
 *   override val globalOptions: Map<String, String> = mapOf("butterknife.minSdk" to "21")
 * }
 * ```
 */
internal abstract class BasicAptOptionsConfig : AptOptionsConfig {
  private val rawAptCompilerOptions by lazy {
    globalOptions.map { (option, value) -> "-A$option=$value" }
  }

  open val name: String = this::class.java.simpleName
  open val globalOptions: Map<String, String> = emptyMap()

  val javaCompileAptAction =
    Action<JavaCompile> { options.compilerArgs.addAll(rawAptCompilerOptions) }

  final override fun newConfigurer(project: Project): AptOptionsConfigurer {
    return newConfigurer(project, BasicAptOptionsConfigurer(project, this))
  }

  /**
   * Optional callback with the created basic configurer. By default this just returns that created
   * instance, but you can optionally override this to customize the behavior. Using class
   * delegation is recommended to simplify reuse.
   */
  open fun newConfigurer(
    project: Project,
    basicConfigurer: AptOptionsConfigurer,
  ): AptOptionsConfigurer = basicConfigurer

  private class BasicAptOptionsConfigurer(
    override val project: Project,
    private val baseConfig: BasicAptOptionsConfig,
  ) : AptOptionsConfigurer {

    private val baseBuildTypeAction =
      Action<BuildType> {
        project.logger.debug(
          "${baseConfig.name}: Adding javac apt options to android project " +
            "${project.path} at buildType $name"
        )
        baseConfig.globalOptions.forEach { (key, value) ->
          javaCompileOptions.annotationProcessorOptions.arguments[key] = value
        }
      }

    private val javaLibraryAction =
      Action<Any> {
        // Implicitly not using Kotlin because we would have to use Kapt
        project.logger.debug(
          "${baseConfig.name}: Adding javac apt options to android project ${project.path}"
        )
        project.tasks.withType(JavaCompile::class.java, baseConfig.javaCompileAptAction)
      }

    override fun configure(configurationContext: ConfigurationContext) =
      with(configurationContext.project) {
        if (configurationContext.isKaptConfiguration) {
          logger.debug("${baseConfig.name}: Adding kapt arguments to $path")
          configure<KaptExtension> {
            arguments { baseConfig.globalOptions.forEach { (key, value) -> arg(key, value) } }
          }
        } else {
          project.pluginManager.withPlugin("com.android.application") {
            logger.debug(
              "${baseConfig.name}: Adding javac apt options to android application project $path"
            )
            configure<AppExtension> { buildTypes.configureEach(baseBuildTypeAction) }
          }
          project.pluginManager.withPlugin("com.android.library") {
            logger.debug(
              "${baseConfig.name}: Adding javac apt options to android library project $path"
            )
            configure<LibraryExtension> { buildTypes.configureEach(baseBuildTypeAction) }
          }
          project.pluginManager.withPlugin("java", javaLibraryAction)
          project.pluginManager.withPlugin("java-library", javaLibraryAction)
        }
      }
  }
}

/**
 * All [AptOptionsConfig] types. Please follow the standard structure of one object per config and
 * don't add any other types. This ensures that [invoke] works smoothly.
 */
@Suppress("unused") // Nested classes here are looked up reflectively
internal object AptOptionsConfigs {

  operator fun invoke(): List<AptOptionsConfig> =
    AptOptionsConfigs::class
      .nestedClasses
      .map { it.objectInstance }
      .filterIsInstance<AptOptionsConfig>()

  object Dagger : BasicAptOptionsConfig() {
    override val targetDependency: String = "dagger-compiler"
    override val globalOptions: Map<String, String> =
      mapOf(
        "dagger.warnIfInjectionFactoryNotGeneratedUpstream" to "enabled",
        // New error messages. Feedback should go to https://github.com/google/dagger/issues/1769
        "dagger.experimentalDaggerErrorMessages" to "enabled",
        // Fast init mode for improved dagger perf on startup:
        // https://dagger.dev/dev-guide/compiler-options.html#fastinit-mode
        "dagger.fastInit" to "enabled",
        // https://dagger.dev/dev-guide/compiler-options#ignore-provision-key-wildcards
        "dagger.ignoreProvisionKeyWildcards" to "ENABLED",
      )
  }

  object Moshi : BasicAptOptionsConfig() {
    override val targetDependency: String = "moshi-kotlin-codegen"
    override val globalOptions: Map<String, String> =
      mapOf("moshi.generated" to "javax.annotation.Generated")
  }
}
