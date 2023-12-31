package slack.gradle.artifacts

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

internal interface SgpArtifacts : Named {
  companion object {
    @JvmField val SGP_ARTIFACTS_ATTRIBUTE: Attribute<SgpArtifacts> = Attribute.of(
      "sgp.internal.artifacts", SgpArtifacts::class.java
    )
  }

  enum class Kind(
    val declarableName: String,
    val artifactName: String,
  ) {
    SKIPPY_UNIT_TESTS("skippyUnitTests", "skippy-unit-tests"),
    SKIPPY_AVOIDED_TASKS("skippyAvoidedTasks", "skippy-avoided-tasks"),
  }
}