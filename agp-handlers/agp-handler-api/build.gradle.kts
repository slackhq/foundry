plugins {
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly(libs.agp)
  implementation(libs.guava)
}
