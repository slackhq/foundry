plugins {
  kotlin("jvm")
}

if (hasProperty("SlackRepositoryUrl")) {
  apply(plugin = "com.vanniktech.maven.publish")
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(gradleKotlinDsl())
  compileOnly(libs.agp)
  implementation(libs.guava)
}
