plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly("com.android.tools:common:31.0.2")
  compileOnly(gradleApi())
  compileOnly(libs.agp)
  compileOnly(libs.guava)
}
