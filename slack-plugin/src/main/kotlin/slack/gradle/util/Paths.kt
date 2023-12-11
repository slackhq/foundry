package slack.gradle.util

import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists

internal fun Path.prepareForGradleOutput() = apply {
  deleteIfExists()
  createParentDirectories()
  createFile()
}