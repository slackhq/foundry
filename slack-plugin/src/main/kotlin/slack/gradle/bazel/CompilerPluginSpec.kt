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

/*
kt_compiler_plugin(
    name = "parcelize_plugin",
    compile_phase = True,
    id = "org.jetbrains.kotlin.parcelize",
    stubs_phase = True,
    deps = list["@com_github_jetbrains_kotlin//:parcelize-compiler-plugin"],
)
 */
internal class CompilerPluginSpec(builder: Builder) {
  val name = builder.name
  val id = builder.id
  val compilePhase = builder.compilePhase
  val stubsPhase = builder.stubsPhase
  val deps = builder.deps.toList()

  override fun toString(): String {
    return """
      kt_compiler_plugin(
          name = "$name",
          compile_phase = ${compilePhase.pythonLiteral},
          id = "$id",
          stubs_phase = ${stubsPhase.pythonLiteral},
          deps = ${deps.depsString("deps")},
      )
    """
      .trimIndent()
  }

  private val Boolean.pythonLiteral: String
    get() = if (this) "True" else "False"

  class Builder(val name: String, val id: String) {
    var deps = mutableListOf<Dep>()
    var compilePhase = true
    var stubsPhase = true

    fun addDep(dep: Dep) = apply { deps.add(dep) }

    fun build() = CompilerPluginSpec(this)
  }
}

internal enum class CompilerPlugin(val spec: CompilerPluginSpec) {
  PARCELIZE(
    CompilerPluginSpec.Builder(name = "parcelize_plugin", id = "org.jetbrains.kotlin.parcelize")
      .addDep(
        Dep.Remote(source = "com_github_jetbrains_kotlin", target = "parcelize-compiler-plugin")
      )
      .build()
  ),
  // TODO
  //  moshi-ir
  //  redacted
  //  compose
  //  anvil
  //  KSP?
  //  KAPT?
}
