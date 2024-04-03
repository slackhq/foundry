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
package slack.gradle.bazel

import com.grab.grazel.bazel.starlark.BazelDependency
import com.grab.grazel.bazel.starlark.StatementsBuilder
import java.io.File
import java.util.SortedSet
import okio.FileSystem
import okio.Path
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties

internal interface CommonJvmProjectSpec {
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String
  /** The path to this project, i.e. `path/to/project` */
  val path: String
  /** The source for rules to import. */
  val ruleSource: String
  // Deps
  val deps: List<Dep>
  val exportedDeps: List<Dep>
  val hasTests: Boolean
  val testDeps: List<Dep>
  // Source globs
  val srcGlobs: List<String>
  val testSrcGlobs: List<String>
  val compilerPlugins: List<Dep>
  val kspProcessors: List<KspProcessor>
  val freeCompilerArgs: List<String>

  @CheckReturnValue
  fun StatementsBuilder.writeCommonJvmStatements(): JvmRuleDependencies {
    val implementationDeps =
      (deps + compilerPlugins).map { BazelDependency.StringDependency(it.toString()) }.sorted()

    val compileTarget = Dep.Local(path).toString()
    val compositeTestDeps =
      buildSet {
          addAll(deps)
          addAll(exportedDeps)
          addAll(testDeps)
          addAll(compilerPlugins)
        }
        .map { BazelDependency.StringDependency(it.toString()) }
        // Don't duplicate the compile target
        .filterNot { it.toString() == compileTarget }
        .sorted()

    // TODO capture ksp options
    if (kspProcessors.isNotEmpty()) {
      val allDeps = kspProcessors.flatMapTo(mutableSetOf()) { it.deps }
      kspProcessor(KSP_TARGET, "ignored", allDeps.map(BazelDependency::StringDependency).sorted())
    }

    val compilerPlugins =
      buildList {
          addAll(compilerPlugins.map { BazelDependency.StringDependency(it.toString()) })
          if (kspProcessors.isNotEmpty()) {
            add(BazelDependency.StringDependency(":$KSP_TARGET"))
          }
        }
        .sorted()

    return JvmRuleDependencies(implementationDeps, compositeTestDeps, compilerPlugins)
  }

  @Suppress("UNCHECKED_CAST")
  interface Builder<out T : Builder<T>> {
    val name: String
    val path: String
    var ruleSource: String
    val deps: MutableList<Dep>
    val exportedDeps: MutableList<Dep>
    val testDeps: MutableList<Dep>
    var hasTests: Boolean
    val srcGlobs: MutableList<String>
    val testSrcGlobs: MutableList<String>
    val compilerPlugins: MutableList<Dep>
    val kspProcessors: MutableList<KspProcessor>
    val freeCompilerArgs: MutableList<String>

    fun ruleSource(source: String): T = apply { ruleSource = source } as T

    fun addDep(dep: Dep): T = apply { deps.add(dep) } as T

    fun addExportedDep(dep: Dep): T = apply { exportedDeps.add(dep) } as T

    fun addTestDep(dep: Dep): T =
      apply {
        hasTests = true
        testDeps.add(dep)
      }
        as T

    fun addSrcGlob(glob: String): T = apply { srcGlobs.add(glob) } as T

    fun addTestSrcGlob(glob: String): T = apply { testSrcGlobs.add(glob) } as T

    fun addCompilerPlugin(plugin: Dep): T = apply { compilerPlugins.add(plugin) } as T

    fun addKspProcessor(processor: KspProcessor): T = apply { kspProcessors.add(processor) } as T

    fun addFreeCompilerArg(arg: String): T = apply { freeCompilerArgs.add(arg) } as T
  }

  data class JvmRuleDependencies(
    val deps: List<BazelDependency>,
    val testDeps: List<BazelDependency>,
    val plugins: List<BazelDependency>,
  )

  companion object {
    private const val TEST_TARGET = "test"
    private const val KSP_TARGET = "_ksp"

    /** Some projects are named "test", so we have to disambiguate. */
    fun testName(projectName: String) = if (projectName == TEST_TARGET) "test_" else TEST_TARGET

    operator fun invoke(builder: Builder<*>): CommonJvmProjectSpec =
      CommonJvmProjectSpecImpl(builder)
  }
}

internal fun CommonJvmProjectSpec.writeTo(path: Path, fs: FileSystem = FileSystem.SYSTEM) {
  path.parent?.let(fs::createDirectories)
  fs.write(path) { writeUtf8(this@writeTo.toString()) }
}

private class CommonJvmProjectSpecImpl(builder: CommonJvmProjectSpec.Builder<*>) :
  CommonJvmProjectSpec {
  override val name: String = builder.name
  override val path: String = builder.path
  override val ruleSource: String = builder.ruleSource
  override val deps: List<Dep> = builder.deps.toList()
  override val exportedDeps: List<Dep> = builder.exportedDeps.toList()
  override val hasTests: Boolean = builder.hasTests
  override val testDeps: List<Dep> = builder.testDeps.toList()
  override val srcGlobs: List<String> = builder.srcGlobs.toList()
  override val testSrcGlobs: List<String> = builder.testSrcGlobs.toList()
  override val compilerPlugins = builder.compilerPlugins.toList()
  override val kspProcessors = builder.kspProcessors.toList()
  override val freeCompilerArgs = builder.freeCompilerArgs.toList()
}

internal interface CommonJvmProjectBazelTask : Task {
  @get:Input val projectPath: Property<String>
  @get:Input val targetName: Property<String>
  @get:Input val ruleSource: Property<String>

  @get:Input val projectDir: Property<File>

  @get:Input val deps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val exportedDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input @get:Optional val testDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val kspDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val kaptDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val compilerPlugins: SetProperty<Dep>
  @get:Input val kspProcessors: SetProperty<KspProcessor>
  @get:Input val freeCompilerArgs: ListProperty<String>

  // Features
  @get:Optional @get:Input val moshix: Property<Boolean>
  @get:Optional @get:Input val redacted: Property<Boolean>
  @get:Optional @get:Input val parcelize: Property<Boolean>
  @get:Optional @get:Input val autoService: Property<Boolean>

  @get:OutputFile val outputFile: RegularFileProperty

  fun <B : CommonJvmProjectSpec.Builder<B>> B.applyCommonJvmConfig() = apply {
    val deps = this@CommonJvmProjectBazelTask.deps.mapDeps()
    val exportedDeps = this@CommonJvmProjectBazelTask.exportedDeps.mapDeps()
    val testDeps =
      if (!this@CommonJvmProjectBazelTask.testDeps.isPresent) null
      else this@CommonJvmProjectBazelTask.testDeps.mapDeps()

    // Only moshix and redacted are supported in JVM projects
    val compilerPlugins = this@CommonJvmProjectBazelTask.compilerPlugins.get().toMutableList()

    val kspProcessors = this@CommonJvmProjectBazelTask.kspProcessors.get().toMutableList()

    if (moshix.getOrElse(false)) {
      // TODO we technically could choose IR or KSP for this, but for now assume IR
      compilerPlugins.add(CompilerPluginDeps.moshix)
      kspProcessors.add(KspProcessors.moshiProguardRuleGen)
    }
    if (redacted.getOrElse(false)) {
      compilerPlugins.add(CompilerPluginDeps.redacted)
    }
    if (parcelize.getOrElse(false)) {
      compilerPlugins.add(CompilerPluginDeps.parcelize)
    }
    if (autoService.getOrElse(false)) {
      kspProcessors.add(KspProcessors.autoService)
    }

    // TODO make this pluggable and single-pass
    val mappedKspDeps = kspDeps.mapDeps()
    when {
      mappedKspDeps.any { "guinness" in it.toString() } -> {
        kspProcessors += KspProcessors.guinness
      }
      mappedKspDeps.any { "feature-flag/compiler" in it.toString() } -> {
        kspProcessors += KspProcessors.featureFlag
      }
    }
    val allKspDeps = mappedKspDeps.map { it.toString() }

    // TODO kapt

    val hasTestSources =
      projectDir.get().resolve("src/test").walkTopDown().any {
        it.extension == "kt" || it.extension == "java"
      }
    this@CommonJvmProjectBazelTask.ruleSource.orNull?.let(::ruleSource)
    deps.forEach { addDep(it) }
    exportedDeps.forEach { addExportedDep(it) }
    if (!hasTestSources || testDeps == null) {
      hasTests = false
    } else {
      hasTests = true
      testDeps.forEach { addTestDep(it) }
    }
    compilerPlugins.forEach { addCompilerPlugin(it) }
    kspProcessors.forEach { addKspProcessor(it.withAddedDeps(allKspDeps)) }
  }

  private fun SetProperty<ComponentArtifactIdentifier>.mapDeps(): SortedSet<Dep> {
    return map { result ->
        result.asSequence().mapNotNull { component ->
          when (component) {
            is ModuleComponentArtifactIdentifier -> {
              val componentId = component.componentIdentifier
              val identifier = "${componentId.group}:${componentId.module}"
              Dep.Remote.fromMavenIdentifier(identifier)
            }
            is PublishArtifactLocalArtifactMetadata -> {
              val projectIdentifier = component.componentIdentifier
              check(projectIdentifier is ProjectComponentIdentifier)
              Dep.Local.fromGradlePath(projectIdentifier.projectPath)
            }
            else -> {
              System.err.println("Unknown component type: $component (${component.javaClass})")
              null
            }
          }
        }
      }
      .get()
      .toSortedSet()
  }

  private fun resolvedDependenciesFromOld(
    provider: NamedDomainObjectProvider<ResolvableConfiguration>
  ): Provider<List<ComponentArtifactIdentifier>> {
    return provider.flatMap { configuration ->
      configuration.incoming.artifacts.resolvedArtifacts.map { it.map { it.id } }
    }
  }

  private fun resolvedDependenciesFrom(
    provider: NamedDomainObjectProvider<ResolvableConfiguration>
  ): Provider<List<ComponentArtifactIdentifier>> {
    return provider.flatMap { configuration ->
      configuration.externalResolvedArtifactsFor("jar").map { it.map { it.id } }
    }
  }

  private fun Configuration.externalResolvedArtifactsFor(
    attrValue: String
  ): Provider<Set<ResolvedArtifactResult>> {
    return externalArtifactViewFor(attrValue).artifacts.resolvedArtifacts
  }

  private fun Configuration.externalArtifactViewFor(attrValue: String): ArtifactView =
    incoming.artifactView {
      // only give us artifacts that match the requested attribute
      attributes.attribute(attributeKey, attrValue)
      // keep going if there is an artifact resolution failure or some dependencies doesn't have
      // artifacts matching the requested attribute
      lenient(true)
      // only give us artifacts from external components ("Modules" vs "Projects")
      //      componentFilter { it is ModuleComponentIdentifier }
    }

  fun configureCommonJvm(
    project: Project,
    slackProperties: SlackProperties,
    depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
    exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
    testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
    kspConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
    kaptConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
    slackExtension: SlackExtension,
    kotlinCompilation: TaskProvider<KotlinCompile>?,
  ) {
    targetName.set(project.name)
    projectPath.set(project.path)
    ruleSource.set(slackProperties.bazelRuleSource)
    projectDir.set(project.layout.projectDirectory.asFile)
    deps.set(resolvedDependenciesFrom(depsConfiguration))
    exportedDeps.set(resolvedDependenciesFrom(exportedDepsConfiguration))
    testConfiguration?.let { testDeps.set(resolvedDependenciesFrom(it)) }
    kspConfiguration?.let { kspDeps.set(resolvedDependenciesFrom(it)) }
    kaptConfiguration?.let { kaptDeps.set(resolvedDependenciesFrom(it)) }
    outputFile.set(project.layout.projectDirectory.file("BUILD.bazel"))
    moshix.set(slackExtension.featuresHandler.moshiHandler.moshiCodegen)
    redacted.set(slackExtension.featuresHandler.redacted)
    parcelize.set(project.pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.parcelize"))
    autoService.set(slackExtension.featuresHandler.autoService)
    kotlinCompilation?.let {
      this.freeCompilerArgs.addAll(it.flatMap { it.compilerOptions.freeCompilerArgs })
    }
  }

  companion object {
    private val attributeKey = Attribute.of("artifactType", String::class.java)
  }
}
