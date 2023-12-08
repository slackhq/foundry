plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.buildConfig)
}

buildConfig {
  packageName("slack.gradle.agphandler.v83")
  buildConfigField("String", "AGP_VERSION", libs.versions.agpAlpha.map { "\"$it\"" })
  useKotlinOutput {
    topLevelConstants = true
    internalVisibility = true
  }
}

dependencies {
  ksp(libs.autoService.ksp)

  api(projects.agpHandlers.agpHandlerApi)

  implementation(libs.autoService.annotations)

  compileOnly(gradleApi())
  compileOnly(libs.agpAlpha)
}
