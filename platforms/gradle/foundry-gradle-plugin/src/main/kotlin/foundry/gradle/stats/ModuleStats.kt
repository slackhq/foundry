/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package foundry.gradle.stats

import app.cash.sqldelight.gradle.GenerateSchemaTask
import app.cash.sqldelight.gradle.SqlDelightTask
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.moshi.JsonClass
import com.squareup.wire.gradle.WireTask
import foundry.common.json.JsonTools
import foundry.gradle.FoundryProperties
import foundry.gradle.artifacts.FoundryArtifact
import foundry.gradle.artifacts.Publisher
import foundry.gradle.artifacts.Resolver
import foundry.gradle.capitalizeUS
import foundry.gradle.configure
import foundry.gradle.convertProjectPathToAccessor
import foundry.gradle.dependsOn
import foundry.gradle.namedLazy
import foundry.gradle.properties.mapToBoolean
import foundry.gradle.properties.setDisallowChanges
import foundry.gradle.register
import foundry.gradle.tasks.dependsOnSourceGeneratingTasks
import foundry.gradle.topography.DefaultFeatures
import foundry.gradle.topography.ModuleTopography
import foundry.gradle.topography.ModuleTopographyTask
import foundry.gradle.util.toJson
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import org.jgrapht.graph.GraphCycleProhibitedException

public object ModuleStatsTasks {
  public const val AGGREGATOR_NAME: String = "aggregateModuleStats"

  private val MAIN_SRC_DIRS = listOf("main", "commonMain", "internal", "debug", "internalDebug")

  // Option to disable inclusion of generated code, which is helpful for testing
  private fun Project.includeGenerated() =
    providers.environmentVariable("MODULE_SCORE_INCLUDE_GENERATED").mapToBoolean().orElse(true)

  internal fun configureRoot(rootProject: Project, foundryProperties: FoundryProperties) {
    if (!foundryProperties.modScoreGlobalEnabled) return
    val includeGenerated = rootProject.includeGenerated()
    val resolver = Resolver.interProjectResolver(rootProject, FoundryArtifact.ModStatsFiles)

    rootProject.tasks.register<ModuleStatsAggregatorTask>(AGGREGATOR_NAME) {
      projectPathsToAccessors.setDisallowChanges(
        rootProject.provider {
          rootProject.subprojects.associate { subproject ->
            val regularPath = subproject.path
            val projectAccessor = convertProjectPathToAccessor(regularPath)
            projectAccessor to regularPath
          }
        }
      )
      statsFiles.from(resolver.artifactView())
      outputFile.setDisallowChanges(
        rootProject.layout.buildDirectory.file("reports/foundry/moduleStats.json")
      )
      this.includeGenerated.setDisallowChanges(includeGenerated)
    }
  }

  private fun File.findMainSourceDir(): String? {
    return MAIN_SRC_DIRS.firstOrNull { File(this@findMainSourceDir, it).exists() }
  }

  internal fun configureSubproject(
    project: Project,
    foundryProperties: FoundryProperties,
    topographyTask: TaskProvider<ModuleTopographyTask>,
  ) {
    if (!foundryProperties.modScoreGlobalEnabled) return
    if (!project.buildFile.exists()) return

    if (foundryProperties.modScoreIgnore) return

    // Don't run on the platform project, it's a special case
    if (project.path == foundryProperties.platformProjectPath) return

    val mainSrcDir = File(project.projectDir, "src").findMainSourceDir()

    val includeGenerated = project.includeGenerated().get()

    val locTask =
      if (mainSrcDir == null) {
        null
      } else {
        project.tasks.register<LocTask>("loc") {
          srcsDir.setDisallowChanges(project.layout.projectDirectory.dir("src/$mainSrcDir"))
          outputFile.setDisallowChanges(
            project.layout.buildDirectory.file("reports/foundry/loc.json")
          )
        }
      }

    /** Link task dependencies for both the loc and stats collector tasks. */
    fun linkToLocTask(body: (Task) -> Unit) {
      locTask?.configure { body(this) }
    }

    val moduleStatsCollector: Lazy<TaskProvider<ModuleStatsCollectorTask>> = lazy {
      val task =
        project.tasks.register<ModuleStatsCollectorTask>("moduleStats") {
          modulePath.setDisallowChanges(project.path)
          // TODO https://github.com/gradle/gradle/issues/25014
          buildFileProperty.set(project.buildFile)
          if (locTask != null) {
            locDataFiles.from(locTask.flatMap { it.outputFile })
          }
          this.includeGenerated.setDisallowChanges(includeGenerated)
          this.multiVariantAndroidLibrary.setDisallowChanges(foundryProperties.libraryWithVariants)
          topographyJson.set(topographyTask.flatMap { it.topographyOutputFile })
          outputFile.setDisallowChanges(
            project.layout.buildDirectory.file("reports/foundry/moduleStats.json")
          )
        }

      val publisher = Publisher.interProjectPublisher(project, FoundryArtifact.ModStatsFiles)
      publisher.publish(task.flatMap { it.outputFile })
      task
    }

    fun addCollectorTag(tag: String) {
      moduleStatsCollector.value.configure { tags.add(tag) }
    }

    val generatedSourcesAdded = AtomicBoolean()
    val addGeneratedSources = {
      locTask?.let { locTask ->
        val shouldConfigure = generatedSourcesAdded.compareAndSet(false, true) && includeGenerated
        if (shouldConfigure) {
          locTask.configure {
            generatedSrcsDir.setDisallowChanges(project.layout.buildDirectory.dir("generated"))
          }
          // Don't depend on compiler tasks. Technically doesn't cover javac apt but tbh we don't
          // really support that
          locTask.dependsOnSourceGeneratingTasks(project, includeCompilerTasks = false)
        }
      }
    }

    project.pluginManager.apply {
      withPlugin("org.jetbrains.kotlin.kapt") {
        addGeneratedSources()
        linkToLocTask { it.mustRunAfter(project.tasks.withType(KaptTask::class.java)) }
      }
      withPlugin("com.google.devtools.ksp") {
        addGeneratedSources()
        linkToLocTask { it.mustRunAfter(project.tasks.withType(KspTask::class.java)) }
      }
      withPlugin("com.squareup.wire") {
        addGeneratedSources()
        linkToLocTask { it.mustRunAfter(project.tasks.withType(WireTask::class.java)) }
      }
      withPlugin("app.cash.sqldelight") {
        addGeneratedSources()
        linkToLocTask {
          it.mustRunAfter(
            project.tasks.withType(GenerateSchemaTask::class.java),
            project.tasks.withType(SqlDelightTask::class.java),
          )
        }
      }
      withPlugin("com.android.library") {
        val multiVariant = foundryProperties.libraryWithVariants
        project.configure<LibraryAndroidComponentsExtension> {
          finalizeDsl { extension ->
            val targetVariant =
              if (multiVariant) {
                val defaultBuildType = extension.buildTypes.find { it.isDefault }?.name ?: "debug"
                val defaultFlavor = extension.productFlavors.find { it.isDefault }?.name ?: ""
                "$defaultFlavor${defaultBuildType.capitalizeUS()}"
              } else {
                "release"
              }

            if (includeGenerated && locTask != null) {
              project.namedLazy<Task>("compile${targetVariant.capitalizeUS()}Sources") {
                locTask.dependsOn(it)
              }
            }

            // TODO do we need to check the gradle properties too?
            // TODO move to the task action once we have these in ModuleTopography
            val androidResources = extension.buildFeatures.androidResources == true
            val viewBinding = extension.buildFeatures.viewBinding == true
            if (viewBinding) {
              addGeneratedSources()
            }
            if (androidResources) {
              addCollectorTag(ModuleStatsCollectorTask.TAG_RESOURCES_ENABLED)
            }
            if (viewBinding) {
              addCollectorTag(ModuleStatsCollectorTask.TAG_VIEW_BINDING)
            }
          }
        }
      }
    }
  }
}

@CacheableTask
public abstract class ModuleStatsAggregatorTask : DefaultTask() {
  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFiles
  @get:SkipWhenEmpty
  internal abstract val statsFiles: ConfigurableFileCollection

  @get:Input internal abstract val includeGenerated: Property<Boolean>

  /**
   * Creates a mapping of project accessors to their module paths so we can re-map these back when
   * computing centrality.
   *
   * ```
   * services.slackKitIntegrations -> :services:slack-kit-integrations
   * ```
   */
  @get:Input internal abstract val projectPathsToAccessors: MapProperty<String, String>

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @TaskAction
  internal fun dumpStats() {
    val allStats = statsFiles.map<File, ModuleStats>(JsonTools::fromJson).sortedBy { it.modulePath }

    val globalStats = allStats.map { it.totalSource }.reduce(Map<String, LanguageStats>::mergeWith)

    val projectAccessorsMapping = projectPathsToAccessors.get()
    val shallowProjectDeps =
      allStats.associate { stats ->
        stats.modulePath to stats.deps.map { projectAccessorsMapping.getValue(it) }
      }

    val graph = DirectedAcyclicGraph<String, DefaultEdge>(DefaultEdge::class.java)
    for ((subproject, dependencies) in shallowProjectDeps) {
      graph.addVertex(subproject)
      for (dependency in dependencies) {
        graph.addVertex(dependency)
        try {
          graph.addEdge(subproject, dependency)
        } catch (e: GraphCycleProhibitedException) {
          // Surprisingly, not unexpected. This can happen when project A has a compileOnly
          // dependency on project B and project B has a testImplementation dependency on project A.
          if ("model" !in subproject || "model" !in dependency) {
            if (subproject.contains("test-fixtures") xor dependency.contains("test-fixtures")) {
              // This is a big bandaid over the ability for projects to depend own their own test
              // fixtures, which breaks the cycle in these scenarios.
              // We allow this specific case, ideally in the future with native testFixtures()
              // support this would just go away.
            } else {
              throw RuntimeException(
                "Cycle from $subproject to $dependency. Please modularize this better!",
                e,
              )
            }
          }
        }
      }
    }

    val centralities = BetweennessCentrality(graph).scores
    val scores =
      allStats
        .map {
          val centrality = centralities.getValue(it.modulePath)
          val weights = it.weighted(globalStats, centrality)
          val score = weights.score()
          ModuleScore(it.modulePath, score, weights, includeGenerated.get())
        }
        .sortedByDescending { it.score }

    logger.debug("Scores are ${scores.joinToString("\n")}")
    JsonTools.toJson(outputFile, AggregateModuleScore(scores))
  }
}

@CacheableTask
internal abstract class ModuleStatsCollectorTask @Inject constructor(objects: ObjectFactory) :
  DefaultTask() {

  companion object {
    const val TAG_KAPT = "kapt"
    const val TAG_KSP = "ksp"
    const val TAG_KOTLIN = "kotlin"
    const val TAG_DAGGER_COMPILER = "dagger-compiler"
    const val TAG_VIEW_BINDING = "viewbinding"
    const val TAG_ANDROID = "android"
    const val TAG_WIRE = "wire"
    const val TAG_SQLDELIGHT = "sqldelight"
    const val TAG_RESOURCES_ENABLED = "android-resources"
    const val TAG_PARCELIZE = "android-parcelize"
    const val TAG_VARIANTS = "android-variants"
  }

  @get:Input abstract val modulePath: Property<String>

  @get:Input abstract val includeGenerated: Property<Boolean>

  // TODO eventually pull this from ModuleTopography too once it has properties
  @get:Input @get:Optional abstract val multiVariantAndroidLibrary: Property<Boolean>

  @get:Input abstract val tags: SetProperty<String>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val topographyJson: RegularFileProperty

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFile
  abstract val buildFileProperty: RegularFileProperty

  // Collection since it's optional and Gradle doesn't handle optional files well
  // when chained from outputs of other (possibly-skipped) tasks.
  // https://github.com/gradle/gradle/issues/2016
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  val locDataFiles: ConfigurableFileCollection = objects.fileCollection()

  @get:OutputFile abstract val outputFile: RegularFileProperty

  init {
    group = "foundry"
  }

  @TaskAction
  fun dumpStats() {
    val locSrcFiles = locDataFiles.files
    val (sources, generatedSources) =
      if (locSrcFiles.isNotEmpty()) {
        JsonTools.fromJson<LocTask.LocData>(locDataFiles.singleFile)
      } else {
        LocTask.LocData.EMPTY
      }

    val topography = ModuleTopography.from(topographyJson)
    val finalTags = tags.getOrElse(emptySet()).toMutableSet()

    for (plugin in topography.plugins) {
      when (plugin) {
        "org.jetbrains.kotlin.jvm",
        "org.jetbrains.kotlin.android",
        "org.jetbrains.kotlin.multiplatform" -> {
          finalTags.add(TAG_KOTLIN)
        }

        "org.jetbrains.kotlin.kapt" -> {
          finalTags.add(TAG_KAPT)
        }

        "com.google.devtools.ksp" -> {
          finalTags.add(TAG_KSP)
        }

        "org.jetbrains.kotlin.plugin.parcelize" -> {
          finalTags.add(TAG_PARCELIZE)
        }

        "com.squareup.wire" -> {
          finalTags.add(TAG_WIRE)
        }

        "app.cash.sqldelight" -> {
          finalTags.add(TAG_SQLDELIGHT)
        }

        "com.android.application" -> {
          finalTags.add(TAG_ANDROID)
        }

        "com.android.library" -> {
          val multiVariant = multiVariantAndroidLibrary.getOrElse(false)
          finalTags.add(TAG_ANDROID)
          if (multiVariant) {
            finalTags.add(TAG_VARIANTS)
          }
        }
      }
    }

    for (feature in topography.features) {
      when (feature) {
        DefaultFeatures.DaggerCompiler.name -> finalTags.add(TAG_DAGGER_COMPILER)
      }
    }

    val dependencies = StatsUtils.parseProjectDeps(buildFileProperty.asFile.get())

    logger.debug("Writing stats to ${outputFile.asFile.get()}")
    JsonTools.toJson(
      outputFile,
      ModuleStats(modulePath.get(), sources, generatedSources, finalTags, dependencies),
    )
  }
}

internal object StatsUtils {
  fun parseProjectDeps(buildFile: File): Set<String> {
    return parseProjectDeps(buildFile.readText())
  }

  fun parseProjectDeps(text: String): Set<String> {
    val deps = mutableSetOf<String>()
    text.lineSequence().forEach { line ->
      if ("(projects." in line) {
        // testFixtures*( are just for gradle module metadata and not actually a dependency
        if ("testFixturesApi(" in line) return@forEach
        if ("testFixturesImplementation(" in line) return@forEach
        deps +=
          line
            .substringAfter("(projects.")
            .substringBefore(")")
            .substringBefore(".dependencyProject")
      }
    }
    return deps.toSortedSet()
  }
}

@JsonClass(generateAdapter = true)
public data class AggregateModuleScore(val scores: List<ModuleScore>)

@JsonClass(generateAdapter = true)
public data class ModuleScore(
  val moduleName: String,
  val score: Long,
  val weights: Weights,
  val includesGenerated: Boolean,
)

private fun Int.percentOf(other: Int): Double {
  if (other == 0) return 0.0
  if (this == 0) return 0.0
  return toDouble().div(other) * 100
}

private fun Map<String, LanguageStats>.jvmCode(): LanguageStats {
  var languageStats = LanguageStats.EMPTY
  get("Kotlin")?.let { languageStats += it }
  get("Java")?.let { languageStats += it }
  return languageStats
}

private fun ModuleStats.weighted(
  globalStats: Map<String, LanguageStats>,
  centrality: Double,
): Weights {
  return Weights(
    percentOfTotalCode = totalSource.jvmCode().total.percentOf(globalStats.jvmCode().total),
    javaKotlinRatio =
      totalSource
        .getOrDefault("Java", LanguageStats.EMPTY)
        .total
        .percentOf(totalSource.jvmCode().total),
    centrality = centrality,
    loc = source.jvmCode().total,
    locGenerated = generated.jvmCode().total,
    tags = tags,
    modulePath = modulePath,
  )
}

// Weights we want to check
// TODO capture build times in this. Percent of total build, mainly. Possibly factor it together
// with centrality
//  where centrality is a multiplier for build times.
@JsonClass(generateAdapter = true)
public data class Weights(
  val percentOfTotalCode: Double,
  val javaKotlinRatio: Double,
  val centrality: Double,
  val loc: Int,
  val locGenerated: Int,
  val tags: Set<String>,
  val modulePath: String,
) {
  public fun score(): Long {
    // Base score is their centrality
    var score = 0L

    // Centrality can range from 0 to 200. Dampen it here so it doesn't dominate the total score
    val dampenedCentrality = centrality * 0.25f
    score += dampenedCentrality.toInt()

    // If percent of total code is > 10%, add every percentage
    // TODO do we care about XML?
    if (percentOfTotalCode > 10f) {
      score += percentOfTotalCode.toInt()
    }

    // We want pure Kotlin projects. Start with 10, lower or higher than 100% raises it
    // Idea is if they have 0%, then this is zeroed out. If they have 50/50, 5. Etc.
    // TODO discount generated code here?
    // TODO maybe this is more of a health score thing
    score += javaKotlinRatio.div(10).toInt()

    val kapt = ModuleStatsCollectorTask.TAG_KAPT in tags
    val ksp = ModuleStatsCollectorTask.TAG_KSP in tags
    val android = ModuleStatsCollectorTask.TAG_ANDROID in tags
    val resourcesEnabled = ModuleStatsCollectorTask.TAG_RESOURCES_ENABLED in tags
    val resourcesHavePublicXml = true // TODO
    val androidVariants = ModuleStatsCollectorTask.TAG_VARIANTS in tags
    val daggerCompiler = ModuleStatsCollectorTask.TAG_DAGGER_COMPILER in tags

    // Kapt slows down projects. We want KSP/Anvil longer term, for now we just add a fixed hit.
    if (kapt) {
      // Dagger is a necessary evil on build times
      if (daggerCompiler) {
        score += 5
      } else {
        // Non-dagger stuff gets an extra bite because it's likely autovalue
        score += 10
      }
    }

    if (ksp) {
      score += 2
    }

    // Enabling both kapt and KSP is a bad idea and we want to discourage it
    if (ksp && kapt) {
      score += 10
    }

    // Android slows down projects. We just add a fixed hit.
    if (android) {
      score += 5
    }

    // Resources are expensive and we have designated projects for these
    if (resourcesEnabled) {
      score += 1
    }

    // If we have resources, they should be gated via API
    if (!resourcesHavePublicXml) {
      score += 5
    }

    // We want to move away from variants because they're expensive
    if (androidVariants) {
      score += 10
    }

    return score
  }
}

@JsonClass(generateAdapter = true)
internal data class ModuleStats(
  val modulePath: String,
  val source: Map<String, LanguageStats>,
  val generated: Map<String, LanguageStats>,
  val tags: Set<String>,
  val deps: Set<String>,
) {
  val totalSource
    get() = source.mergeWith(generated)
}
