package slack.gradle.avoidance

import com.jraska.module.graph.DependencyGraph
import com.jraska.module.graph.assertion.GradleDependencyGraphFactory
import java.io.ObjectOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import slack.gradle.SlackProperties
import slack.gradle.register
import slack.gradle.util.setDisallowChanges

/** A simple task that writes a serialized [dependencyGraph] to an [outputFile]. */
@CacheableTask
internal abstract class GenerateDependencyGraphTask : DefaultTask() {

  @get:Input abstract val dependencyGraph: Property<DependencyGraph.SerializableGraph>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun generate() {
    ObjectOutputStream(outputFile.asFile.get().outputStream()).use {
      it.writeObject(dependencyGraph.get())
    }
  }

  companion object {
    private const val NAME = "generateDependencyGraph"
    private val DEFAULT_CONFIGURATIONS =
      setOf(
        "androidTestImplementation",
        "annotationProcessor",
        "api",
        "compileOnly",
        "debugApi",
        "debugImplementation",
        "implementation",
        "kapt",
        "kotlinCompilerPluginClasspath",
        "ksp",
        "releaseApi",
        "releaseImplementation",
        "testImplementation",
      )

    fun register(
      rootProject: Project,
      slackProperties: SlackProperties
    ): TaskProvider<GenerateDependencyGraphTask> {
      val configurationsToLook by lazy {
        val providedConfigs = slackProperties.affectedProjectConfigurations
        providedConfigs?.splitToSequence(',')?.toSet()?.let { providedConfigSet ->
          if (slackProperties.buildUponDefaultAffectedProjectConfigurations) {
            DEFAULT_CONFIGURATIONS + providedConfigSet
          } else {
            providedConfigSet
          }
        } ?: DEFAULT_CONFIGURATIONS
      }

      val lazyGraph by lazy {
        GradleDependencyGraphFactory.create(rootProject, configurationsToLook).serializableGraph()
      }

      return rootProject.tasks.register<GenerateDependencyGraphTask>(NAME) {
        dependencyGraph.setDisallowChanges(rootProject.provider { lazyGraph })
        outputFile.setDisallowChanges(
          rootProject.layout.buildDirectory.file("slack/androidTestProjectPaths/paths.txt")
        )
      }
    }
  }
}
