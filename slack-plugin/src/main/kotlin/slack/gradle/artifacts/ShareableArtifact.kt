package slack.gradle.artifacts

import java.io.Serializable
import org.gradle.api.attributes.Attribute

internal interface ShareableArtifact<T : ShareableArtifact<T>> : Serializable {
  val attribute: Attribute<T>
  val declarableName: String
}