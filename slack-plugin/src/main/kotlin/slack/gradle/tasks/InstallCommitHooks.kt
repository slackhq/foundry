package slack.gradle.tasks

import com.google.common.io.Resources
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.setProperty

@UntrackedTask(because = "This is run on-demand")
public abstract class InstallCommitHooks
@Inject
constructor(
  layout: ProjectLayout,
  objects: ObjectFactory,
) : DefaultTask() {
  @get:Input
  public val names: SetProperty<String> =
    objects
      .setProperty<String>()
      .convention(
        setOf(
          "post-checkout",
          "post-commit",
          "post-merge",
          "pre-commit",
          "pre-push",
        )
      )

  @get:OutputDirectory
  public val outputHooksDir: DirectoryProperty =
    objects.directoryProperty().convention(layout.projectDirectory.dir("config/git/hooks"))

  init {
    group = "slack"
    description = "Installs basic git hook files for formatting to a given output directory."
  }

  @TaskAction
  public fun install() {
    val hookFiles =
      names.get().associate { name ->
        val fixedName = name.removePrefix("githook-")
        fixedName to Resources.getResource(fixedName).readText()
      }

    val outputDir = outputHooksDir.asFile.get()
    logger.lifecycle("Writing git hooks to $outputDir")
    outputDir.mkdirs()
    for ((name, text) in hookFiles) {
      logger.lifecycle("Writing $name")
      File(outputDir, name).writeText(text)
    }
  }

  internal companion object {
    private const val NAME = "installCommitHooks"

    fun register(rootProject: Project): TaskProvider<InstallCommitHooks> {
      return rootProject.tasks.register<InstallCommitHooks>(NAME)
    }
  }
}
