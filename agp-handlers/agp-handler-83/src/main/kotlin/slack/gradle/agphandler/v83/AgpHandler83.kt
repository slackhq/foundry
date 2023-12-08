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
package slack.gradle.agphandler.v83

import com.android.build.api.AndroidPluginVersion
import com.android.build.gradle.internal.SdkLocationSourceSet
import com.android.build.gradle.internal.SdkLocator
import com.google.auto.service.AutoService
import java.io.File
import org.gradle.api.provider.ProviderFactory
import slack.gradle.agp.AgpHandler
import slack.gradle.agp.computeAndroidPluginVersion
import slack.gradle.agp.internal.NoOpIssueReporter

public class AgpHandler83 private constructor(override val agpVersion: AndroidPluginVersion) :
  AgpHandler {

  override fun getAndroidSdkDirectory(projectRootDir: File, providers: ProviderFactory): File =
    SdkLocator.getSdkDirectory(
      projectRootDir,
      NoOpIssueReporter,
      SdkLocationSourceSet(projectRootDir, providers)
    )

  @AutoService(AgpHandler.Factory::class)
  public class Factory : AgpHandler.Factory {
    override val minVersion: AndroidPluginVersion by lazy {
      computeAndroidPluginVersion(AGP_VERSION)
    }

    override val currentVersion: AndroidPluginVersion by lazy { AndroidPluginVersion.getCurrent() }

    override fun create(): AgpHandler = AgpHandler83(currentVersion)
  }
}
