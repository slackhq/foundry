plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader) apply false
}

group = "com.slack.intellij"

version = property("VERSION_NAME").toString()

repositories { mavenCentral() }

intellij {
  version.set("2022.2.5")
  type.set("IC")
}

dependencies {
  implementation(libs.okio)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
