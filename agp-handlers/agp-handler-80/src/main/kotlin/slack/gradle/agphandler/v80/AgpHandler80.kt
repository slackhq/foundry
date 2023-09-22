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

import com.android.build.api.AndroidPluginVersion
import com.google.auto.service.AutoService
import slack.gradle.agp.AgpHandler

public class AgpHandler80 private constructor(override val agpVersion: AndroidPluginVersion) :
  AgpHandler {
  @AutoService(AgpHandler.Factory::class)
  public class Factory : AgpHandler.Factory {
    override val minVersion: AndroidPluginVersion = AndroidPluginVersion(8, 0, 0)

    // TODO Remove once it's public
    //  https://issuetracker.google.com/issues/297440098
    @Suppress("invisible_reference", "invisible_member")
    override val currentVersion: AndroidPluginVersion =
      com.android.build.api.extension.impl.CURRENT_AGP_VERSION

    override fun create(): AgpHandler = AgpHandler80(currentVersion)
  }
}
