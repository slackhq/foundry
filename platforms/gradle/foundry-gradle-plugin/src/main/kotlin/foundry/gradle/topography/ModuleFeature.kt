package foundry.gradle.topography

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public data class ModuleFeature(
  val name: String,
  val explanation: String,
  val advice: String,
  val removalPatterns: Set<Regex>?,
  /**
   * Generated sources root dir relative to the project dir, if any. Files are checked recursively.
   */
  val generatedSourcesDir: String? = null,
  val generatedSourcesExtensions: Set<String> = emptySet(),
  val matchingText: Set<String> = emptySet(),
  val matchingTextFileExtensions: Set<String> = emptySet(),
  /**
   * If specified, looks for any sources in this dir relative to the project dir. Files are checked
   * recursively.
   */
  val matchingSourcesDir: String? = null,
  val matchingPlugin: String? = null,
)