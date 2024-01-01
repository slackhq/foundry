package slack.gradle.tasks

import java.io.File
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import slack.gradle.registerOrConfigure

@CacheableTask
internal abstract class SimpleFileProducerTask : DefaultTask() {
    @get:Input
    abstract val input: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun writeText() {
        val outputFile = output.get().asFile
        outputFile.writeText(input.get())
    }

    companion object {
        fun registerOrConfigure(
            project: Project,
            name: String,
            description: String,
            outputFilePath: String = "lifecycleFiles/$name/producedTask.txt",
            input: String = "${project.path}:$name",
            group: String = "slack",
            action: Action<SimpleFileProducerTask> = Action {},
        ): TaskProvider<SimpleFileProducerTask> {
            return project.tasks.registerOrConfigure<SimpleFileProducerTask>(name) {
                this.group = group
                this.description = description
                this.input.set(input)
                output.set(project.layout.buildDirectory.file(outputFilePath))
                action.execute(this)
            }
        }
    }
}

@CacheableTask
internal abstract class SimpleFilesConsumerTask : DefaultTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun mergeFiles() {
        val outputFile = output.get().asFile
        outputFile.writeText(inputFiles.files.map { it.readText() }.sorted().joinToString("\n"))
    }

    companion object {
        fun registerOrConfigure(
            project: Project,
            name: String,
            description: String,
            inputFiles: Provider<Set<File>>,
            outputFilePath: String = "lifecycleFiles/$name/consumedTasks.txt",
            group: String = "slack",
            action: Action<SimpleFilesConsumerTask> = Action {},
        ): TaskProvider<SimpleFilesConsumerTask> {
            return project.tasks.registerOrConfigure<SimpleFilesConsumerTask>(name) {
                this.group = group
                this.description = description
                this.inputFiles.from(inputFiles)
                output.set(project.layout.buildDirectory.file(outputFilePath))
                action.execute(this)
            }
        }
    }
}