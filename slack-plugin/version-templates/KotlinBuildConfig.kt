@file:JvmName("KotlinBuildConfig")

package slack.gradle.dependencies

internal object KotlinBuildConfig {
  const val kotlin = "$kotlinVersion"
  const val kotlinJvmTarget = "$kotlinJvmTarget"
  val kotlinCompilerArgs = listOf($kotlinCompilerArgs)
  val kotlinJvmCompilerArgs = listOf($kotlinJvmCompilerArgs)
}