/*
 * Copyright (C) 2023 Slack Technologies, LLC
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
plugins {
  java
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.intellij)
  alias(libs.plugins.pluginUploader) apply false
}

group = "com.slack.intellij"

version = property("VERSION_NAME").toString()

intellijPlatform {
  pluginConfiguration {
    name = "Artifactory Authenticator"
    id = "com.slack.intellij.artifactory"
    version = property("VERSION_NAME").toString()
    description = "A plugin for authenticating plugin repositories with Artifactory."
    vendor {
      name = "Slack"
      url =
        "https://github.com/slackhq/foundry/tree/main/platforms/intellij/artifactory-authenticator"
      email = "oss@slack-corp.com"
    }
  }
}

dependencies {
  intellijPlatform { instrumentationTools() }

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
