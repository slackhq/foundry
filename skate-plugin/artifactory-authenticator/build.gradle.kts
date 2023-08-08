plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader) apply false
}

group = "com.slack.intellij"

version = property("VERSION_NAME").toString()

repositories { mavenCentral() }

dependencies {
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
