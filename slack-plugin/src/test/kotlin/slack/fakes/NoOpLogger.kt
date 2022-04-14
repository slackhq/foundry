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
package slack.fakes

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.helpers.NOPLogger

/** A [Logger] that doesn't log. */
open class NoOpLogger : Logger, org.slf4j.Logger by NOPLogger.NOP_LOGGER {
  override fun isQuietEnabled(): Boolean = false

  override fun log(level: LogLevel, message: String) = Unit

  override fun log(level: LogLevel, message: String, vararg objects: Any) = Unit

  override fun log(level: LogLevel, message: String, throwable: Throwable) = Unit

  override fun isEnabled(level: LogLevel): Boolean = true

  override fun lifecycle(message: String) = Unit

  override fun lifecycle(message: String, vararg objects: Any) = Unit

  override fun lifecycle(message: String, throwable: Throwable) = Unit

  override fun quiet(message: String) = Unit

  override fun quiet(message: String, vararg objects: Any) = Unit

  override fun quiet(message: String, throwable: Throwable) = Unit

  override fun isLifecycleEnabled(): Boolean = true
}
