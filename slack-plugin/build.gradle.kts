plugins {
  `kotlin-dsl`
  kotlin("jvm")
  alias(libs.plugins.mavenPublish)
}

gradlePlugin {
  plugins.create("unitTest") {
    id = "slack.unit-test"
    implementationClass = "slack.unittest.UnitTestPlugin"
  }
  plugins.create("slack-root") {
    id = "slack.root"
    implementationClass = "slack.gradle.SlackRootPlugin"
  }
  plugins.create("slack-base") {
    id = "slack.base"
    implementationClass = "slack.gradle.SlackBasePlugin"
  }
  plugins.create("apkVersioning") {
    id = "slack.apk-versioning"
    implementationClass = "slack.gradle.ApkVersioningPlugin"
  }
}

sourceSets {
  main.configure {
    java.srcDir(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
  }
}

// NOTE: DON'T CHANGE THIS TASK NAME WITHOUT CHANGING IT IN THE ROOT BUILD FILE TOO!
val copyVersionTemplatesProvider = tasks.register<Copy>("copyVersionTemplates") {
  from(project.layout.projectDirectory.dir("version-templates"))
  into(project.layout.buildDirectory.dir("generated/sources/version-templates/kotlin/main"))
  filteringCharset = "UTF-8"

  doFirst {
    if (destinationDir.exists()) {
      // Clear output dir first if anything is present
      destinationDir.listFiles()?.forEach { it.delete() }
    }
  }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
  dependsOn(copyVersionTemplatesProvider)
}

dependencies {
  compileOnly(gradleApi())
  compileOnly(libs.gradlePlugins.enterprise)

  compileOnly(platform(kotlin("bom", version = libs.versions.kotlin.get())))
  compileOnly(kotlin("gradle-plugin", version = libs.versions.kotlin.get()))
  implementation(kotlin("reflect", version = libs.versions.kotlin.get()))

  // compileOnly because we want to leave versioning to the consumers
  // Add gradle plugins for the slack project itself, separate from plugins. We do this so we can de-dupe version
  // management between this plugin and the root build.gradle.kts file.
  compileOnly(libs.gradlePlugins.bugsnag)
  compileOnly(libs.gradlePlugins.doctor)
  compileOnly(libs.gradlePlugins.versions)
  compileOnly(libs.gradlePlugins.detekt)
  compileOnly(libs.detekt)
  compileOnly(libs.gradlePlugins.errorProne)
  compileOnly(libs.gradlePlugins.napt)
  compileOnly(libs.gradlePlugins.nullaway)
  compileOnly(libs.gradlePlugins.dependencyAnalysis)
  compileOnly(libs.gradlePlugins.retry)
  compileOnly(libs.gradlePlugins.anvil)
  compileOnly(libs.gradlePlugins.spotless)
  compileOnly(libs.gradlePlugins.ksp)
  compileOnly(libs.gradlePlugins.redacted)
  compileOnly(libs.gradlePlugins.moshix)

  implementation(libs.oshi) {
    because("To read hardware information")
  }

  compileOnly(libs.agp)
  api(projects.agpHandlers.agpHandlerApi)
  testImplementation(libs.agp)

  implementation(libs.commonsText) {
    because("For access to its StringEscapeUtils")
  }
  implementation(libs.guava)
  implementation(libs.kotlinCliUtil)
  implementation(libs.jna)

  implementation(libs.rxjava)

  api(platform(libs.okhttp.bom))
  api(libs.okhttp)

  implementation(libs.moshi)
  implementation(libs.moshi.kotlin)

  // Graphing library with Betweenness Centrality algo for modularization score
  implementation(libs.jgrapht)

  // Progress bar for downloads
  implementation(libs.progressBar)

  // Better I/O
  api(libs.okio)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
