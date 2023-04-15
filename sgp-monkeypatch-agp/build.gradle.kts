plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(libs.guava)
  compileOnly(libs.agp)
  compileOnly("com.android.tools:common:31.0.0")
}