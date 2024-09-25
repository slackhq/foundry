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
package foundry.common

/** A simple logging abstraction for use in Foundry. */
public interface FoundryLogger {
  public fun debug(message: String)

  public fun info(message: String)

  public fun lifecycle(message: String)

  public fun warn(message: String)

  public fun warn(message: String, error: Throwable)

  public fun error(message: String)

  public fun error(message: String, error: Throwable)

  public companion object {

    public fun noop(): FoundryLogger = NoopFoundryLogger

    public fun system(): FoundryLogger = SystemFoundryLogger

    public fun prefix(prefix: String, delegate: FoundryLogger): FoundryLogger =
      PrefixFoundryLogger(prefix, delegate)
  }
}

/** A simple delegating logger that allows overwriting functions as desired. */
internal abstract class DelegatingFoundryLogger(private val delegate: FoundryLogger) :
  FoundryLogger by delegate

/** A logger that always adds the given [prefix] to log messages. */
internal class PrefixFoundryLogger(
  private val prefix: String,
  private val delegate: FoundryLogger,
) : FoundryLogger {
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

/** A quiet no-op [FoundryLogger]. */
private object NoopFoundryLogger : FoundryLogger {
  override fun debug(message: String) {}

  override fun info(message: String) {}

  override fun lifecycle(message: String) {}

  override fun warn(message: String) {}

  override fun warn(message: String, error: Throwable) {}

  override fun error(message: String) {}

  override fun error(message: String, error: Throwable) {}
}

/** An [FoundryLogger] that just writes to [System.out] and [System.err]. */
private object SystemFoundryLogger : FoundryLogger {
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
