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
  api(platform(libs.okhttp.bom))

  implementation(libs.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.okio)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converters.wire)
}
