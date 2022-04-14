plugins {
  kotlin("jvm")
  alias(libs.plugins.ksp)
}

if (hasProperty("SlackRepositoryUrl")) {
  apply(plugin = "com.vanniktech.maven.publish")
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly("com.android.tools.build:gradle:7.1.2")
  // Android tools versioning is wild
  compileOnly("com.android.tools:common:30.1.2")
  implementation(kotlin("reflect", version = libs.versions.kotlin.get()))

  compileOnly(libs.autoService.annotations)
  ksp(libs.autoService.ksp)

  api(projects.agpHandlers.agpHandlerApi)
}
