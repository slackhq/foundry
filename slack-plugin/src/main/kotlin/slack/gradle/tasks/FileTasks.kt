package slack.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

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
}