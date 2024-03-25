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
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.jetbrains.annotations.CheckReturnValue
import slack.gradle.SlackExtension
import slack.gradle.SlackProperties

internal interface CommonJvmProjectSpec {
  /**
   * The name of the project. Usually just the directory name but could be different if there are
   * multiple targets.
   */
  val name: String
  /** The source for rules to import. */
  val ruleSource: String
  // Deps
  val deps: List<Dep>
  val exportedDeps: List<Dep>
  val testDeps: List<Dep>
  // Source globs
  val srcGlobs: List<String>
  val testSrcGlobs: List<String>
  val compilerPlugins: List<Dep>
  val kspProcessors: List<KspProcessor>

  @CheckReturnValue
  fun StatementsBuilder.writeCommonJvmStatements():
    Pair<List<BazelDependency>, List<BazelDependency>> {
    val kspTargets = kspProcessors.associateBy { it.name }
    val depsWithCodeGen = buildSet {
      addAll(kspTargets.keys.sorted().map { Dep.Target(it) })
      addAll(deps)
    }

    val implementationDeps =
      (depsWithCodeGen + compilerPlugins)
        .map { BazelDependency.StringDependency(it.toString()) }
        .sorted()

    val compositeTestDeps =
      buildSet {
          add(Dep.Target("lib"))
          addAll(depsWithCodeGen)
          addAll(exportedDeps)
          addAll(testDeps)
          addAll(compilerPlugins)
        }
        .map { BazelDependency.StringDependency(it.toString()) }
        .sorted()

    for (processor in kspProcessors) {
      writeKspRule(processor)
    }

    return implementationDeps to compositeTestDeps
  }

  @Suppress("UNCHECKED_CAST")
  interface Builder<out T : Builder<T>> {
    val name: String
    var ruleSource: String
    val deps: MutableList<Dep>
    val exportedDeps: MutableList<Dep>
    val testDeps: MutableList<Dep>
    val srcGlobs: MutableList<String>
    val testSrcGlobs: MutableList<String>
    val compilerPlugins: MutableList<Dep>
    val kspProcessors: MutableList<KspProcessor>

    fun ruleSource(source: String): T = apply { ruleSource = source } as T

    fun addDep(dep: Dep): T = apply { deps.add(dep) } as T

    fun addExportedDep(dep: Dep): T = apply { exportedDeps.add(dep) } as T

    fun addTestDep(dep: Dep): T = apply { testDeps.add(dep) } as T

    fun addSrcGlob(glob: String): T = apply { srcGlobs.add(glob) } as T

    fun addTestSrcGlob(glob: String): T = apply { testSrcGlobs.add(glob) } as T

    fun addCompilerPlugin(plugin: Dep): T = apply { compilerPlugins.add(plugin) } as T

    fun addKspProcessor(processor: KspProcessor): T = apply { kspProcessors.add(processor) } as T
  }

  companion object {
    const val LIB_TARGET = "lib"
    const val TEST_TARGET = "test"

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
  override val ruleSource: String = builder.ruleSource
  override val deps: List<Dep> = builder.deps.toList()
  override val exportedDeps: List<Dep> = builder.exportedDeps.toList()
  override val testDeps: List<Dep> = builder.testDeps.toList()
  override val srcGlobs: List<String> = builder.srcGlobs.toList()
  override val testSrcGlobs: List<String> = builder.testSrcGlobs.toList()
  override val compilerPlugins = builder.compilerPlugins.toList()
  override val kspProcessors = builder.kspProcessors.toList()
}

internal interface CommonJvmProjectBazelTask : Task {
  @get:Input val targetName: Property<String>
  @get:Input val ruleSource: Property<String>

  @get:Input val projectDir: Property<File>

  @get:Input val deps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val exportedDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val testDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val kspDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val kaptDeps: SetProperty<ComponentArtifactIdentifier>
  @get:Input val compilerPlugins: SetProperty<Dep>
  @get:Input val kspProcessors: SetProperty<KspProcessor>

  // Features
  @get:Optional @get:Input val moshix: Property<Boolean>
  @get:Optional @get:Input val redacted: Property<Boolean>
  @get:Optional @get:Input val parcelize: Property<Boolean>
  @get:Optional @get:Input val autoService: Property<Boolean>

  @get:OutputFile val outputFile: RegularFileProperty

  fun <B : CommonJvmProjectSpec.Builder<B>> B.applyCommonJvmConfig() = apply {
    val deps = this@CommonJvmProjectBazelTask.deps.mapDeps()
    val exportedDeps = this@CommonJvmProjectBazelTask.exportedDeps.mapDeps()
    val testDeps = this@CommonJvmProjectBazelTask.testDeps.mapDeps()

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
        logger.lifecycle("[KSP] Adding guinness compiler")
        kspProcessors += KspProcessors.guinness
      }
      mappedKspDeps.any { "feature-flag/compiler" in it.toString() } -> {
        logger.lifecycle("[KSP] Adding feature flag compiler")
        kspProcessors += KspProcessors.featureFlag
      }
    }
    val allKspDeps = mappedKspDeps.map { it.toString() }

    // TODO kapt

    this@CommonJvmProjectBazelTask.ruleSource.orNull?.let(::ruleSource)
    deps.forEach { addDep(it) }
    exportedDeps.forEach { addExportedDep(it) }
    testDeps.forEach { addTestDep(it) }
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
              // Map to "path/to/local/dependency1" format
              Dep.Local(
                projectIdentifier.projectPath.removePrefix(":").replace(":", "/"),
                target = "lib",
              )
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

  private fun resolvedDependenciesFrom(
    provider: NamedDomainObjectProvider<ResolvableConfiguration>
  ): Provider<List<ComponentArtifactIdentifier>> {
    return provider.flatMap { configuration ->
      configuration.incoming.artifacts.resolvedArtifacts.map { it.map { it.id } }
    }
  }

  fun configureCommonJvm(
    project: Project,
    slackProperties: SlackProperties,
    depsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
    exportedDepsConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
    testConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>,
    kspConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
    kaptConfiguration: NamedDomainObjectProvider<ResolvableConfiguration>?,
    slackExtension: SlackExtension,
  ) {
    targetName.set(project.name)
    ruleSource.set(slackProperties.bazelRuleSource)
    projectDir.set(project.layout.projectDirectory.asFile)
    deps.set(resolvedDependenciesFrom(depsConfiguration))
    exportedDeps.set(resolvedDependenciesFrom(exportedDepsConfiguration))
    testDeps.set(resolvedDependenciesFrom(testConfiguration))
    kspConfiguration?.let { kspDeps.set(resolvedDependenciesFrom(it)) }
    kaptConfiguration?.let { kaptDeps.set(resolvedDependenciesFrom(it)) }
    outputFile.set(project.layout.projectDirectory.file("BUILD.bazel"))
    moshix.set(slackExtension.featuresHandler.moshiHandler.moshiCodegen)
    redacted.set(slackExtension.featuresHandler.redacted)
    parcelize.set(project.pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.parcelize"))
    autoService.set(slackExtension.featuresHandler.autoService)
  }
}
