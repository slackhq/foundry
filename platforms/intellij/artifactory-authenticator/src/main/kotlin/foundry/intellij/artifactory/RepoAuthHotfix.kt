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
package foundry.intellij.artifactory

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.auth.PluginRepositoryAuthService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger

/** Workaround for https://youtrack.jetbrains.com/issue/IDEA-315487. */
class RepoAuthHotfix : AppLifecycleListener {
  private val logger = logger<RepoAuthHotfix>()

  override fun appFrameCreated(commandLineArgs: List<String>) {
    logger.debug("Initializing ${PluginRepositoryAuthService::class.simpleName}")
    service<PluginRepositoryAuthService>()
  }
}
