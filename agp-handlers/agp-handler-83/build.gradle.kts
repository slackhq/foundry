plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.buildConfig)
}

val agpVersion = libs.versions.agpAlpha.get()

buildConfig {
  packageName("slack.gradle.agphandler.v83")
  buildConfigField("String", "AGP_VERSION", "\"$agpVersion\"")
}

dependencies {
  ksp(libs.autoService.ksp)

  api(projects.agpHandlers.agpHandlerApi)

  implementation(libs.autoService.annotations)

  compileOnly(gradleApi())
  compileOnly(libs.agpAlpha)
}
