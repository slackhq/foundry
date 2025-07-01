package foundry.intellij.artifactory

import java.util.Base64

internal fun String.encodeBase64() = Base64.getEncoder().encodeToString(encodeToByteArray())

internal fun String.decodeBase64(): String =
  Base64.getDecoder().decode(this).toString(Charsets.UTF_8)
