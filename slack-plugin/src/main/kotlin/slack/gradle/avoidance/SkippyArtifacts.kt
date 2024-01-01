package slack.gradle.avoidance

import org.gradle.api.Project
import slack.gradle.artifacts.Publisher
import slack.gradle.artifacts.SgpArtifacts
import slack.gradle.capitalizeUS
import slack.gradle.tasks.SimpleFileProducerTask
import slack.gradle.tasks.publishWith

internal object SkippyArtifacts {
    fun publishSkippedTask(project: Project, name: String) {
        SimpleFileProducerTask.registerOrConfigure(
                project,
                name = "skipped${name.capitalizeUS()}",
                description = "Lifecycle task to run unit tests for ${project.path} (skipped).",
            ).publishWith(
            Publisher.interProjectPublisher(
                project,
                SgpArtifacts.Kind.SKIPPY_AVOIDED_TASKS
            )
            )
    }
}