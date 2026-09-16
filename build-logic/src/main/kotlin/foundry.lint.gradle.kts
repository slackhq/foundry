/*
 * Copyright (C) 2026 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.build.api.dsl.Lint

plugins { id("com.android.lint") }

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val baselineFile = layout.projectDirectory.file("lint-baseline.xml")

configure<Lint> {
  lintConfig = isolated.rootProject.projectDirectory.file("config/lint/lint.xml").asFile
  if (baselineFile.asFile.exists()) {
    baseline = baselineFile.asFile
  }
}

dependencies.add("lintChecks", catalog.findLibrary("slackLints-checks").get())

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
  dependencies.add("compileOnly", catalog.findLibrary("slackLints-annotations").get())
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
  dependencies.add("jvmMainCompileOnly", catalog.findLibrary("slackLints-annotations").get())
}
