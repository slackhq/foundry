package slack.gradle.util

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
