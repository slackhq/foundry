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
package com.slack.sgp.common

/** A simple logging abstraction for use in SGP. */
public interface SgpLogger {
  public fun debug(message: String)

  public fun info(message: String)

  public fun lifecycle(message: String)

  public fun warn(message: String)

  public fun warn(message: String, error: Throwable)

  public fun error(message: String)

  public fun error(message: String, error: Throwable)

  public companion object {

    public fun noop(): SgpLogger = NoopSgpLogger

    public fun system(): SgpLogger = SystemSgpLogger

    public fun prefix(prefix: String, delegate: SgpLogger): SgpLogger =
      PrefixSgpLogger(prefix, delegate)
  }
}

/** A simple delegating logger that allows overwriting functions as desired. */
internal abstract class DelegatingSgpLogger(private val delegate: SgpLogger) :
  SgpLogger by delegate

/** A logger that always adds the given [prefix] to log messages. */
internal class PrefixSgpLogger(private val prefix: String, private val delegate: SgpLogger) :
  SgpLogger {
  override fun debug(message: String) {
    delegate.debug("$prefix $message")
  }

  override fun info(message: String) {
    delegate.info("$prefix $message")
  }

  override fun lifecycle(message: String) {
    delegate.lifecycle("$prefix $message")
  }

  override fun warn(message: String) {
    delegate.warn("$prefix $message")
  }

  override fun warn(message: String, error: Throwable) {
    delegate.warn("$prefix $message", error)
  }

  override fun error(message: String) {
    delegate.error("$prefix $message")
  }

  override fun error(message: String, error: Throwable) {
    delegate.error("$prefix $message", error)
  }
}

/** A quiet no-op [SgpLogger]. */
private object NoopSgpLogger : SgpLogger {
  override fun debug(message: String) {}

  override fun info(message: String) {}

  override fun lifecycle(message: String) {}

  override fun warn(message: String) {}

  override fun warn(message: String, error: Throwable) {}

  override fun error(message: String) {}

  override fun error(message: String, error: Throwable) {}
}

/** An [SgpLogger] that just writes to [System.out] and [System.err]. */
private object SystemSgpLogger : SgpLogger {
  override fun debug(message: String) {
    println(message)
  }

  override fun info(message: String) {
    println(message)
  }

  override fun lifecycle(message: String) {
    println(message)
  }

  override fun warn(message: String) {
    System.err.println(message)
  }

  override fun warn(message: String, error: Throwable) {
    System.err.println(message)
    error.printStackTrace(System.err)
  }

  override fun error(message: String) {
    System.err.println(message)
  }

  override fun error(message: String, error: Throwable) {
    System.err.println(message)
    error.printStackTrace(System.err)
  }
}
