plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly("com.android.tools.build:gradle:8.0.0-beta01")
  compileOnly("com.android.tools.build:gradle-settings-api:8.0.0-beta01")
  api(projects.agpHandlers.agpHandlerApi)
  implementation(libs.autoService.annotations)
  ksp(libs.autoService.ksp)
}
