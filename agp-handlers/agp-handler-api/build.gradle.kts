plugins {
  kotlin("jvm")
}

if (hasProperty("SlackRepositoryUrl")) {
  apply(plugin = "com.vanniktech.maven.publish")
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  implementation(kotlin("reflect", version = libs.versions.kotlin.get()))
  implementation(libs.agp)
  implementation(libs.guava)
}
