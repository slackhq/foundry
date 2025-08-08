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

import com.intellij.ide.plugins.auth.PluginRepositoryAuthProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger

class ArtifactoryPluginRepositoryAuthProvider : PluginRepositoryAuthProvider {

  private val settings =
    ApplicationManager.getApplication().getService(AuthPluginSettings::class.java)
  private val logger = logger<ArtifactoryPluginRepositoryAuthProvider>()

  override fun getAuthHeaders(url: String): Map<String, String> {
    logger.debug("Getting auth headers for $url")
    val username = settings.username
    val token = settings.token
    logger.debug("Username: $username, token is null? ${token == null}")
    return if (username != null && token != null) {
      val encodedValue = "Basic " + "$username:$token".encodeBase64()
      mapOf("Authorization" to encodedValue)
    } else {
      emptyMap()
    }
  }

  override fun canHandle(url: String): Boolean {
    val canHandle = settings.enabled && settings.url?.let(url::startsWith) ?: false
    logger.debug("Can handle $url? $canHandle")
    return canHandle
  }
}
