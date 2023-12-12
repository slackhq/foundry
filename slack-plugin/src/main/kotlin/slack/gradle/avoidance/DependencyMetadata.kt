package slack.gradle.avoidance

public class DependencyMetadata(
  public val projectsToDependents: Map<String, Set<String>> = emptyMap(),
  public val projectsToDependencies: Map<String, Set<String>> = emptyMap(),
)
