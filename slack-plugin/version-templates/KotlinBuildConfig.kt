@file:JvmName("KotlinBuildConfig")

package slack.gradle.dependencies

internal object KotlinBuildConfig {
  const val kotlin = "$kotlinVersion"
  val kotlinCompilerArgs = listOf($kotlinCompilerArgs)
  val kotlinJvmCompilerArgs = listOf($kotlinJvmCompilerArgs)
}