/*
 * Copyright (C) 2024 Slack Technologies, LLC
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
package foundry.gradle.util

import com.slack.sgp.common.SgpLogger
import org.gradle.api.logging.Logger

/** A Gradle [Logger]-based [SgpLogger]. */
private class GradleSgpLogger(private val delegate: Logger) : SgpLogger {
  override fun debug(message: String) {
    delegate.debug(message)
  }

  override fun info(message: String) {
    delegate.info(message)
  }

  override fun lifecycle(message: String) {
    delegate.lifecycle(message)
  }

  override fun warn(message: String) {
    delegate.warn(message)
  }

  override fun warn(message: String, error: Throwable) {
    delegate.warn(message, error)
  }

  override fun error(message: String) {
    delegate.error(message)
  }

  override fun error(message: String, error: Throwable) {
    delegate.error(message, error)
  }
}

internal fun SgpLogger.Companion.gradle(logger: Logger): SgpLogger = GradleSgpLogger(logger)
