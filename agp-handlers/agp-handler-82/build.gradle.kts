plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.buildConfig)
}

buildConfig {
  packageName("slack.gradle.agphandler.v82")
  buildConfigField("String", "AGP_VERSION", libs.versions.agp.map { "\"$it\"" })
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
  compileOnly(libs.agp)
}
