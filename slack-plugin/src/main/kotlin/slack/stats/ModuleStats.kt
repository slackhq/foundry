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
package slack.stats

import app.cash.sqldelight.gradle.GenerateSchemaTask
import app.cash.sqldelight.gradle.SqlDelightTask
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.google.devtools.ksp.gradle.KspTask
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.wire.gradle.WireTask
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import okio.buffer
import okio.sink
import okio.source
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
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jgrapht.alg.scoring.BetweennessCentrality
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedAcyclicGraph
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties
import slack.gradle.convertProjectPathToAccessor
import slack.gradle.dependsOn
import slack.gradle.namedLazy
import slack.gradle.safeCapitalize
import slack.gradle.util.JsonTools
import slack.gradle.util.mapToBoolean

public object ModuleStatsTasks {
  public const val AGGREGATOR_NAME: String = "aggregateModuleStats"

  // Option to disable inclusion of generated code, which is helpful for testing
  private fun Project.includeGenerated() =
    providers.environmentVariable("MODULE_SCORE_INCLUDE_GENERATED").mapToBoolean().orElse(true)

  internal fun configureRoot(rootProject: Project) {
    val includeGenerated = rootProject.includeGenerated()

    rootProject.tasks.register<ModuleStatsAggregatorTask>(AGGREGATOR_NAME) {
      outputFile.set(rootProject.layout.buildDirectory.file("reports/slack/moduleStats.json"))
      this.includeGenerated.set(includeGenerated)
    }
  }

  internal fun configureSubproject(project: Project) {
    if (
      !project.buildFile.exists() ||
        // Some projects don't have src/main. Right now we don't handle them
        !File(project.projectDir, "src/main").exists() ||
        project.path == ":app" || // TODO need to handle application and androidTest better
        "slack-platform" in project.path
    ) {
      return
    }

    val slackProperties = SlackProperties(project)
    val includeGenerated = project.includeGenerated().get()

    val locTask =
      project.tasks.register<LocTask>("loc") {
        srcsDir.set(project.layout.projectDirectory.dir("src/main"))
        outputFile.set(project.layout.buildDirectory.file("reports/slack/loc.json"))
      }

    val collector: Lazy<TaskProvider<ModuleStatsCollectorTask>> = lazy {
      val task =
        project.tasks.register<ModuleStatsCollectorTask>("moduleStats") {
          modulePath.set(project.path)
          buildFileProperty.set(project.buildFile)
          locDataFiles.from(locTask.flatMap { it.outputFile })
          this.includeGenerated.set(includeGenerated)
          outputFile.set(project.layout.buildDirectory.file("reports/slack/moduleStats.json"))
        }

      val aggregatorTask =
        project.rootProject.tasks.named<ModuleStatsAggregatorTask>(AGGREGATOR_NAME)
      val regularPath = project.path
      val projectAccessor = convertProjectPathToAccessor(regularPath)
      aggregatorTask.configure {
        projectPathsToAccessors.put(projectAccessor, regularPath)
        statsFiles.from(task.map { it.outputFile })
      }
      task
    }

    val generatedSourcesAdded = AtomicBoolean()
    val addGeneratedSources = {
      if (includeGenerated && generatedSourcesAdded.compareAndSet(false, true)) {
        locTask.configure { generatedSrcsDir.set(project.layout.buildDirectory.dir("generated")) }
      }
    }

    if (includeGenerated) {
      collector.value.configure {
        mustRunAfter(project.tasks.withType<JavaCompile>())
        mustRunAfter(project.tasks.withType<KotlinCompile>())
      }
    }
    project.pluginManager.apply {
      withPlugin("org.jetbrains.kotlin.jvm") {
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_KOTLIN) }
        if (includeGenerated) {
          collector.value.configure {
            dependsOn(project.tasks.withType<JavaCompile>())
            dependsOn(project.tasks.withType<KotlinCompile>())
          }
        }
      }
      withPlugin("org.jetbrains.kotlin.kapt") {
        addGeneratedSources()
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_KAPT) }
        if (includeGenerated) {
          collector.value.configure { mustRunAfter(project.tasks.withType<KaptTask>()) }
        }
      }
      withPlugin("com.google.devtools.ksp") {
        addGeneratedSources()
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_KSP) }
        if (includeGenerated) {
          collector.value.configure { mustRunAfter(project.tasks.withType<KspTask>()) }
        }
      }
      withPlugin("org.jetbrains.kotlin.android") {
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_KOTLIN) }
      }
      withPlugin("org.jetbrains.kotlin.plugin.parcelize") {
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_PARCELIZE) }
      }
      withPlugin("com.squareup.wire") {
        addGeneratedSources()
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_WIRE) }
        if (includeGenerated) {
          collector.value.configure { mustRunAfter(project.tasks.withType<WireTask>()) }
        }
      }
      withPlugin("app.cash.sqldelight") {
        addGeneratedSources()
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_SQLDELIGHT) }
        if (includeGenerated) {
          collector.value.configure {
            mustRunAfter(
              project.tasks.withType(GenerateSchemaTask::class.java),
              project.tasks.withType(SqlDelightTask::class.java),
            )
          }
        }
      }
      withPlugin("com.android.application") {
        collector.value.configure { tags.add(ModuleStatsCollectorTask.TAG_ANDROID) }
        //          val multiVariant =
        // project.booleanProperty(SlackPluginConfigs.LIBRARY_WITH_VARIANTS, false, local = true)
        //          afterEvaluate {
        //            val targetVariant = if (multiVariant) "internalDebug" else "release"
        //            val compileLifecycleTask =
        // tasks.named("compile${targetVariant.safeCapitalize()}Sources")
        //            collector.value.dependsOn(compileLifecycleTask)
        //          }
      }
      withPlugin("com.android.library") {
        val multiVariant = slackProperties.libraryWithVariants
        collector.value.configure {
          tags.add(ModuleStatsCollectorTask.TAG_ANDROID)
          if (multiVariant) {
            tags.add(ModuleStatsCollectorTask.TAG_VARIANTS)
          }
        }

        project.configure<LibraryAndroidComponentsExtension> {
          finalizeDsl { extension ->
            val targetVariant =
              if (multiVariant) {
                val defaultBuildType = extension.buildTypes.find { it.isDefault }?.name ?: "debug"
                val defaultFlavor = extension.productFlavors.find { it.isDefault }?.name ?: ""
                "$defaultFlavor${defaultBuildType.safeCapitalize()}"
              } else {
                "release"
              }

            if (includeGenerated) {
              project.namedLazy<Task>("compile${targetVariant.safeCapitalize()}Sources") {
                locTask.dependsOn(it)
              }
            }

            // TODO do we need to check the gradle properties too?
            val androidResources = extension.buildFeatures.androidResources == true
            val viewBinding = extension.buildFeatures.viewBinding == true
            if (viewBinding) {
              addGeneratedSources()
            }
            collector.value.configure {
              if (androidResources) {
                tags.add(ModuleStatsCollectorTask.TAG_RESOURCES_ENABLED)
              }
              if (viewBinding) {
                tags.add(ModuleStatsCollectorTask.TAG_VIEW_BINDING)
              }
            }
          }
        }
      }
      withPlugin("java-library") {
        // Trigger init
        collector.value
      }
      withPlugin("java") {
        // Trigger init
        collector.value
      }

      // TODO would be nice if we could drop autovalue or glide into this
      project.afterEvaluate {
        val extension = the<SlackExtension>()
        val daggerConfig = extension.featuresHandler.daggerHandler.computeConfig()
        if (daggerConfig != null) {
          collector.value.configure {
            if (daggerConfig.useDaggerCompiler) {
              tags.add(ModuleStatsCollectorTask.TAG_DAGGER_COMPILER)
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
    group = "slack"
  }

  @TaskAction
  internal fun dumpStats() {
    val adapter = JsonTools.MOSHI.adapter<ModuleStats>()
    val allStats =
      statsFiles.map { it.source().buffer().use(adapter::fromJson)!! }.sortedBy { it.modulePath }

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
        } catch (e: IllegalArgumentException) {
          // Surprisingly, not unexpected. This can happen when project A has a compileOnly
          // dependency
          // on project B and project B has a testImplementation dependency on project A.
          // This _only_ happens with model and test-model, which we should just modularize out to a
          // third
          // "model-tests" module
          if ("model" !in subproject || "model" !in dependency) {
            throw RuntimeException(
              "Cycle from $subproject to $dependency. Please modularize this better!",
              e
            )
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
    outputFile.asFile.get().sink().buffer().use { sink ->
      JsonTools.MOSHI.adapter<AggregateModuleScore>().toJson(sink, AggregateModuleScore(scores))
    }
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

  @get:Input abstract val tags: SetProperty<String>

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

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  init {
    group = "slack"
  }

  @TaskAction
  fun dumpStats() {
    val (sources, generatedSources) =
      locDataFiles.singleFile.source().buffer().use {
        moshi.adapter<LocTask.LocData>().fromJson(it)!!
      }

    val dependencies = StatsUtils.parseProjectDeps(buildFileProperty.asFile.get())

    logger.debug("Writing stats to ${outputFile.asFile.get()}")
    outputFile.asFile.get().sink().buffer().use { sink ->
      moshi
        .adapter<ModuleStats>()
        .toJson(
          sink,
          ModuleStats(modulePath.get(), sources, generatedSources, tags.get(), dependencies)
        )
    }
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

public data class AggregateModuleScore(val scores: List<ModuleScore>)

public data class ModuleScore(
  val moduleName: String,
  val score: Long,
  val weights: Weights,
  val includesGenerated: Boolean
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
  centrality: Double
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
    modulePath = modulePath
  )
}

// Weights we want to check
// TODO capture build times in this. Percent of total build, mainly. Possibly factor it together
// with centrality
//  where centrality is a multiplier for build times.
public data class Weights(
  val percentOfTotalCode: Double,
  val javaKotlinRatio: Double,
  val centrality: Double,
  val loc: Int,
  val locGenerated: Int,
  val tags: Set<String>,
  val modulePath: String
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

internal data class ModuleStats(
  val modulePath: String,
  val source: Map<String, LanguageStats>,
  val generated: Map<String, LanguageStats>,
  val tags: Set<String>,
  val deps: Set<String>
) {
  val totalSource
    get() = source.mergeWith(generated)
}
