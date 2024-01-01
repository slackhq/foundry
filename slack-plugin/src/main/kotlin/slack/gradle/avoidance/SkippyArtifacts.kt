package slack.gradle.avoidance

import org.gradle.api.Project
import slack.gradle.artifacts.Publisher
import slack.gradle.artifacts.SgpArtifacts
import slack.gradle.capitalizeUS
import slack.gradle.tasks.SimpleFileProducerTask

internal object SkippyArtifacts {
    fun publishSkippedTask(project: Project, name: String) {
        val skippedTask =
            SimpleFileProducerTask.registerOrConfigure(
                project,
                name = "skipped${name.capitalizeUS()}",
                description = "Lifecycle task to run unit tests for ${project.path} (skipped).",
            )
        Publisher.interProjectPublisher(
            project,
            SgpArtifacts.Kind.SKIPPY_AVOIDED_TASKS
        ).publish(skippedTask.flatMap { it.output })
    }
}