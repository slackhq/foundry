package slack.gradle.bazel

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
    operator fun invoke(builder: Builder<*>): CommonJvmProjectSpec =
      CommonJvmProjectSpecImpl(builder)
  }
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
