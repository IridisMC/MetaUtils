import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.concurrent.TimeUnit


class MetaUtils : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("metaUtils", BuildMetaUtilsExtension::class.java, project)

        project.tasks.create("push", GitPushTask::class.java)
    }
}

open class GitPushTask : DefaultTask() {
    private lateinit var commitMessage: String

    init {
        group = "git"
    }

    @Option(option = "message", description = "Commit Message")
    fun setCommitMessage(commitMessage: String) {
        this.commitMessage = commitMessage
    }

    @TaskAction
    fun doTask() {
        require(::commitMessage.isInitialized) { "A commit message must be specified via '--message \"<message>\"'." }
        val metaUtilsDir = File("MetaUtils")
        runCommand("git add .", workingDirectory = metaUtilsDir)
        runCommand("git commit -m \"$commitMessage\"", workingDirectory = metaUtilsDir)
        runCommand("git push", workingDirectory = metaUtilsDir)
//        runCommand("git submodule update --remote")
//        runCommand("git add .")
//        runCommand("git commit -m \"$commitMessage\"")
//        runCommand("git push")
    }


}

private fun runCommand(command: String, workingDirectory: File? = null) {
    println("> $command")
    val parts = command.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
        .directory(workingDirectory)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    proc.waitFor(60, TimeUnit.MINUTES)
    val result = proc.inputStream.bufferedReader().readText()
    println(result)
}



open class BuildMetaUtilsExtension(private val project: Project) {
    fun createJarTest(name: String): SourceSet = with(project) {
        val sourceSet = sourceSets.create(name)
        val jarTask = tasks.create(name, Jar::class.java) { task ->
            group = "testing"
            task.from(sourceSet.output)

            task.destinationDirectory.set(sourceSets.getByName("test").resources.srcDirs.first())
            task.archiveFileName.set("$name.jar")
        }

        tasks.named("processTestResources") { task ->
            task.dependsOn(jarTask)
        }

        return@with sourceSet
    }
}

private val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets