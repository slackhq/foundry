plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

dependencies {
  implementation(libs.guava)

  compileOnly(gradleApi())
  compileOnly(libs.agp)
}
