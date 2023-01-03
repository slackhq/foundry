/*
 * Copyright (C) 2022 Slack Technologies, LLC
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
package slack.gradle.agphandler.v80

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.google.auto.service.AutoService
import org.gradle.api.tasks.testing.Test
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.AgpHandlerFactory
import slack.gradle.agp.AgpSettingsHandler
import slack.gradle.agp.VersionNumber

@AutoService(AgpHandlerFactory::class)
public class AgpHandlerFactory80 : AgpHandlerFactory {
  override val minVersion: VersionNumber = VersionNumber.parse("8.0.0")

  override fun currentVersion(): String = ANDROID_GRADLE_PLUGIN_VERSION

  override fun createHandler(): AgpHandler = AgpHandler80()

  override fun createSettingsHandler(): AgpSettingsHandler = AgpSettingsHandler80()
}

private class AgpHandler80 : AgpHandler {
  override val agpVersion: String
    get() = ANDROID_GRADLE_PLUGIN_VERSION

  override fun allUnitTestOptions(options: TestOptions.UnitTestOptions, body: (Test) -> Unit) {
    options.all(body)
  }

  override fun packagingOptions(
    commonExtension: CommonExtension<*, *, *, *>,
    resourceExclusions: Collection<String>,
    jniPickFirsts: Collection<String>
  ) {
    commonExtension.packagingOptions {
      resources.excludes += resourceExclusions
      jniLibs.pickFirsts += jniPickFirsts
    }
  }
}
