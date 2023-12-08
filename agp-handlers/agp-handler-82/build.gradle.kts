plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.buildConfig)
}

val agpVersion = libs.versions.agp.get()

buildConfig {
  packageName("slack.gradle.agphandler.v82")
  buildConfigField("String", "AGP_VERSION", "\"$agpVersion\"")
}

dependencies {
  ksp(libs.autoService.ksp)

  api(projects.agpHandlers.agpHandlerApi)

  implementation(libs.autoService.annotations)

  compileOnly(gradleApi())
  compileOnly(libs.agp)
}
