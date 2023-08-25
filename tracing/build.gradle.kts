plugins {
  kotlin("jvm")
  alias(libs.plugins.wire)
  alias(libs.plugins.mavenPublish)
}

wire {
  kotlin {}
  sourcePath {
    srcDir("src/main/proto")
    include("trace.proto")
  }
}

dependencies {
  api(platform(libs.coroutines.bom))
  implementation(libs.coroutines.core)
  implementation(libs.retrofit.converters.wire)
  implementation(libs.retrofit)
  api(platform(libs.okhttp.bom))
  implementation(libs.okhttp)
  implementation(libs.okio)
}
