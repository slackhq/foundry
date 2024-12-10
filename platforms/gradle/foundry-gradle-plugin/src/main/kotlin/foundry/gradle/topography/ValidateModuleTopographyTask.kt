package foundry.gradle.topography

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import foundry.cli.walkEachFile
import foundry.common.json.JsonTools
import foundry.gradle.FoundryProperties
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.avoidance.SkippyArtifacts
import foundry.gradle.capitalizeUS
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.tasks.SimpleFileProducerTask
import foundry.gradle.tasks.publish
import foundry.gradle.util.toJson
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.options.Option
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.useLines
import kotlin.io.path.writeText

@DisableCachingByDefault
public abstract class ValidateModuleTopographyTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  @get:Optional
  public abstract val featuresConfigFile: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val topographyJson: RegularFileProperty

  @get:Optional
  @get:Option(option = "auto-fix", description = "Enables auto-fixing build files")
  @get:Input
  public abstract val autoFix: Property<Boolean>

  @get:Internal
  public abstract val projectDirProperty: DirectoryProperty

  @get:OutputFile
  public abstract val modifiedBuildFile: RegularFileProperty
  @get:OutputFile
  public abstract val featuresToRemoveOutputFile: RegularFileProperty

  init {
    group = "foundry"
    @Suppress("LeakingThis")
    notCompatibleWithConfigurationCache("This task modified build files in place")
    @Suppress("LeakingThis") doNotTrackState("This task modified build files in place")
  }

  @OptIn(ExperimentalPathApi::class)
  @TaskAction
  public fun validate() {
    val topography = ModuleTopography.from(topographyJson)
    val loadedFeatures =
      featuresConfigFile.asFile
        .map { ModuleFeaturesConfig.load(it.toPath()) }
        .getOrElse(ModuleFeaturesConfig.DEFAULT)
        .loadFeatures()
    val features = buildSet {
      addAll(topography.features.map { featureKey -> loadedFeatures.getValue(featureKey) })
      // Include plugin-specific features to the check here
      addAll(loadedFeatures.filterValues { it.matchingPlugin in topography.plugins }.values)
    }
    val featuresToRemove = mutableSetOf<ModuleFeature>()

    val projectDir = projectDirProperty.asFile.get().toPath()
    val srcsDir = projectDir.resolve("src")

    val buildFile = projectDir.resolve("build.gradle.kts")
    var buildFileText = buildFile.readText()
    val initialBuildFileHash = buildFileText.hashCode()

    for (feature in features) {
      val initialRemoveSize = featuresToRemove.size
      feature.matchingSourcesDir?.let { matchingSrcsDir ->
        if (projectDir.resolve(matchingSrcsDir).walkEachFile().none()) {
          featuresToRemove += feature
        }
      }

      feature.generatedSourcesDir?.let { generatedSrcsDir ->
        if (projectDir.resolve(generatedSrcsDir).walkEachFile().none()) {
          featuresToRemove += feature
        }
      }

      if (feature.matchingText.isNotEmpty()) {
        if (!feature.hasMatchingTextIn(srcsDir)) {
          featuresToRemove += feature
        }
      }

      val isRemoving = featuresToRemove.size != initialRemoveSize
      if (isRemoving) {
        feature.removalPatterns?.let { removalPatterns ->
          for (removalRegex in removalPatterns) {
            buildFileText = buildFileText.replace(removalRegex, "").removeEmptyBraces()
          }
        }
      }
    }

    JsonTools.toJson<Set<ModuleFeature>>(
      featuresToRemoveOutputFile,
      featuresToRemove.toSortedSet(compareBy { it.name }),
    )

    val hasBuildFileChanges = initialBuildFileHash != buildFileText.hashCode()
    val shouldAutoFix = autoFix.getOrElse(false)
    if (hasBuildFileChanges) {
      if (shouldAutoFix) {
        buildFile.writeText(buildFileText)
      } else {
        modifiedBuildFile.asFile.get().writeText(buildFileText)
      }
    }

    val allAutoFixed = featuresToRemove.all { !it.removalPatterns.isNullOrEmpty() }
    if (featuresToRemove.isNotEmpty()) {
      val message = buildString {
        appendLine(
          "**Validation failed! The following features appear to be unused and can be removed.**"
        )
        appendLine()
        var first = true
        featuresToRemove.forEach {
          if (first) {
            first = false
          } else {
            appendLine()
            appendLine()
          }
          appendLine("- **${it.name}:** ${it.explanation}")
          appendLine()
          appendLine("  - **Advice:** ${it.advice}")
        }
        appendLine()
        appendLine("Full list written to ${featuresToRemoveOutputFile.asFile.get().absolutePath}")
      }
      val t = Terminal(AnsiLevel.TRUECOLOR, interactive = true)
      val md = Markdown(message)
      t.println(md, stderr = true)
      if (shouldAutoFix) {
        if (allAutoFixed) {
          logger.lifecycle("All issues auto-fixed")
        } else {
          throw AssertionError("Not all issues could be fixed automatically")
        }
      } else {
        throw AssertionError()
      }
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun ModuleFeature.hasMatchingTextIn(srcsDir: Path): Boolean {
    logger.debug("Checking for $name annotation usages in sources")
    return srcsDir
      .walkEachFile()
      .run {
        if (matchingTextFileExtensions.isNotEmpty()) {
          filter { it.extension in matchingTextFileExtensions }
        } else {
          this
        }
      }
      .any { file ->
        file.useLines { lines ->
          for (line in lines) {
            if (matchingText.any { it in line }) {
              return@any true
            }
          }
        }
        false
      }
  }

  internal companion object {
    private const val LOG = "[ValidateModuleTopography]"
    private const val NAME = "validateModuleTopography"
    private val CI_NAME = "ci${NAME.capitalizeUS()}"
    internal val GLOBAL_CI_NAME = "global${CI_NAME.capitalizeUS()}"

    fun register(
      project: Project,
      topographyTask: TaskProvider<ModuleTopographyTask>,
      foundryProperties: FoundryProperties,
      affectedProjects: Set<String>?,
    ) {
      val publisher =
        if (affectedProjects == null || project.path in affectedProjects) {
          Publisher.Companion.interProjectPublisher(project, FoundryArtifact.SKIPPY_VALIDATE_TOPOGRAPHY)
        } else {
          val log = "$LOG Skipping ${project.path}:$CI_NAME because it is not affected."
          if (foundryProperties.debug) {
            project.logger.lifecycle(log)
          } else {
            project.logger.debug(log)
          }
          SkippyArtifacts.publishSkippedTask(project, NAME)
          null
        }

      val validateModuleTopographyTask =
        project.tasks.register<ValidateModuleTopographyTask>(NAME) {
          topographyJson.set(topographyTask.flatMap { it.topographyOutputFile })
          featuresConfigFile.convention(foundryProperties.topographyFeaturesConfig)
          projectDirProperty.set(project.layout.projectDirectory)
          autoFix.convention(foundryProperties.topographyAutoFix)
          featuresToRemoveOutputFile.setDisallowChanges(
            project.layout.buildDirectory.file("foundry/topography/validate/featuresToRemove.json")
          )
          modifiedBuildFile.setDisallowChanges(
            project.layout.buildDirectory.file(
              "foundry/topography/validate/modified-build.gradle.kts"
            )
          )
        }
      val ciValidateModuleTopographyTask =
        SimpleFileProducerTask.Companion.registerOrConfigure(
          project,
          CI_NAME,
          description = "Lifecycle task to run $NAME for ${project.path}.",
          group = LifecycleBasePlugin.VERIFICATION_GROUP,
        ) {
          dependsOn(validateModuleTopographyTask)
        }
      publisher?.publish(ciValidateModuleTopographyTask)
    }
  }
}

//// Usage
// var code = "foundry { features { compose() } }"
// code = code.replace(Regex("\\bcompose\\(\\)"), "") // remove compose()
// code = removeEmptyBraces(code) // recursively remove empty braces
//
// println(code) // Should print "<nothing>"
// TODO write tests for this
private val EMPTY_DSL_BLOCK = "(\\w*)\\s*\\{\\s*\\}".toRegex()

internal fun String.removeEmptyBraces(): String {
  var result = this
  while (EMPTY_DSL_BLOCK.containsMatchIn(result)) {
    result = EMPTY_DSL_BLOCK.replace(result, "")
  }
  return result
}