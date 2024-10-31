package foundry.gradle.util

import foundry.common.json.JsonTools
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider

internal inline fun <reified T : Any> JsonTools.toJson(
  provider: Provider<out FileSystemLocation>,
  value: T?,
  prettyPrint: Boolean = false,
) {
  return toJson(provider.get().asFile, value, prettyPrint)
}
