plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly("com.android.tools.build:gradle:7.3.1")
  api(projects.agpHandlers.agpHandlerApi)
  implementation(libs.autoService.annotations)
  ksp(libs.autoService.ksp)
}
