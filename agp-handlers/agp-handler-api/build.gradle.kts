plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

dependencies {
  implementation(libs.guava)

  compileOnly(gradleApi())
  compileOnly(libs.agp)

  testImplementation(gradleApi())
  testImplementation(libs.agp)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
