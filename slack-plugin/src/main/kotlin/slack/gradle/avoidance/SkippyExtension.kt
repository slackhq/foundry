package slack.gradle.avoidance

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import slack.gradle.SlackExtensionMarker
import slack.gradle.avoidance.AffectedProjectsDefaults.DEFAULT_INCLUDE_PATTERNS
import slack.gradle.avoidance.AffectedProjectsDefaults.DEFAULT_NEVER_SKIP_PATTERNS
import slack.gradle.setProperty

@SlackExtensionMarker
public abstract class SkippyExtension @Inject constructor(objects: ObjectFactory) {
  internal val configs: NamedDomainObjectContainer<SkippyGradleConfig> =
    objects.domainObjectContainer(SkippyGradleConfig::class.java)

  init {
    // One global default
    configs.maybeCreate("global")
  }

  public fun config(name: String, action: Action<SkippyGradleConfig>) {
    action.execute(configs.maybeCreate(name))
  }
}

/** A gradle representation of [SkippyConfig]. See its doc for more details. */
public abstract class SkippyGradleConfig @Inject constructor(objects: ObjectFactory) {
  @get:Input
  public val includePatterns: SetProperty<String> =
    objects.setProperty<String>().convention(DEFAULT_INCLUDE_PATTERNS)

  @get:Input
  public val excludePatterns: SetProperty<String> =
    objects.setProperty<String>().convention(emptySet())

  @get:Input
  public val neverSkipPatterns: SetProperty<String> =
    objects.setProperty<String>().convention(DEFAULT_NEVER_SKIP_PATTERNS)

  internal fun asSkippyConfig(): SkippyConfig {
    return SkippyConfig(includePatterns.get(), excludePatterns.get(), neverSkipPatterns.get())
  }
}

/**
 * Represents a Skippy configuration for a specific tool.
 *
 * @property includePatterns A set of glob patterns for files to include in computing affected
 *   projects. This should usually be source files, build files, gradle.properties files, and other
 *   projects that affect builds.
 * @property excludePatterns A set of glob patterns for files to exclude from computing affected
 *   projects. This is run _after_ [includePatterns] and can be useful for excluding files that
 *   would otherwise be included by an existing inclusion pattern.
 * @property neverSkipPatterns A set of glob patterns that, if matched with a file, indicate that
 *   nothing should be skipped and [AffectedProjectsComputer.compute] will return null. This is
 *   useful for globally-affecting things like root build files, `libs.versions.toml`, etc.
 *   **NOTE**: This list is always merged with [includePatterns] as these are implicitly relevant
 *   files.
 */
public data class SkippyConfig(
  public val includePatterns: Set<String> = DEFAULT_INCLUDE_PATTERNS,
  public val excludePatterns: Set<String> = emptySet(),
  public val neverSkipPatterns: Set<String> = DEFAULT_NEVER_SKIP_PATTERNS,
)
