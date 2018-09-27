package kotlin2ts

import kotlin2ts.extension.TemporaryDir
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(TemporaryDir::class)
class PluginTest {

    private fun File.runGradleTask(useCache: Boolean = false): BuildResult {
        val args = mutableListOf("kt2ts")
        if (useCache) {
            args += "--build-cache"
        }
        return GradleRunner.create()
                .withProjectDir(this)
                .withPluginClasspath()
                .apply {
                    if (args.isNotEmpty()) {
                        withArguments(*args.toTypedArray())
                    }
                }
                .withDebug(true)
                .build()
    }

    private fun BuildResult.assertOutcome(outcome: TaskOutcome) {
        assertThat(task(":kt2ts")!!.outcome).isEqualTo(outcome)
    }

    private fun File.build_gradle(vararg packs: String) {
        resolve("build.gradle").run {
            val confPacks = packs.asSequence().map { "projectDir.path + \"/$it\"" }.joinToString()
            writeText(
                    """
                        plugins {
                            id "kotlin2ts"
                        }

                        kt2ts {
                            packs = [$confPacks]
                        }
                        """
            )
        }
    }

    private fun File.settings_gradle() = resolve("settings.gradle").writeText(
            """
                        buildCache {
                            local { directory = "$this/cache" }
                        }
                 """
    )

    private fun File.src(path: String, content: String) {
        resolve(path).run {
            if (!parentFile.exists()) {
                parentFile.mkdirs()
            }
            writeText(content)
        }
    }

    private fun File.readTaskOutput() = resolve("build/kt2ts/kt2ts.txt").readText()

    private fun File.assertOutputContains(vararg chunks: String) = readTaskOutput().run {
        chunks.forEach { assertThat(this).contains(it) }
    }

    @Test
    fun `apply run task twice - should be up to date`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("source/test.xml", "This \n is \n droidcon \n italy \n turin")

        runGradleTask().assertOutcome(SUCCESS)
        runGradleTask().assertOutcome(UP_TO_DATE)
    }

    @Test
    fun `task should read test xml file and should write loc in it`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("source/test.xml", "This \n is \n droidcon \n italy \n turin")

        runGradleTask()
        assertOutputContains("5")
    }

    @Test
    fun `task should run again after test file was modified`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("source/test.xml", "This \n is \n droidcon \n italy \n turin")

        runGradleTask().assertOutcome(SUCCESS)
        assertOutputContains("5")

        // updating same file
        src("source/test.xml", "This \n is \n droidcon \n italy \n turin\nta\nda-a-am")

        runGradleTask().assertOutcome(SUCCESS)
        assertOutputContains("7")
    }

    @Test
    fun `task should read recursive files and should write loc in it`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("source/test.xml", "This\nis\na\nnew\nfile")
        src("source/another/test.xml", "Another\nfile\nwith\nnew\nlines")

        runGradleTask()
        assertOutputContains("10")
    }

    @Test
    fun `task should read recursive files and multiple dirs should write loc in it`(tempDir: File) = tempDir.run {
        build_gradle("source", "otherSource")
        src("source/test.xml", "This\nis\na\nnew\nfile")
        src("source/another/test.xml", "Another\nfile\nwith\nnew\nlines")
        src("otherSource/test.xml", "Awesome\nnew\nlines")

        runGradleTask()
        assertOutputContains("10", "3")
    }

    @Test
    fun `task should read from build cache`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("source/test.xml", "This\nis\na\nnew\nfile")
        settings_gradle()

        runGradleTask(true).assertOutcome(SUCCESS)

        // Clean build dir and run again - should be read from build cache
        resolve("build/").deleteRecursively()
        runGradleTask(true).assertOutcome(FROM_CACHE)
    }

    @Test
    fun `task should read recursive files and multiple dirs should write loc in it2`(tempDir: File) = tempDir.run {
        build_gradle("source", "otherSource")
        src("source/test.xml", "This\nis\na\nnew\nfile")
        src("source/another/test.html", "Another\nfile\nwith\n6\nnew\nlines")
        src("otherSource/test.kt", "Awesome\nnew\nlines")

        runGradleTask()
        assertOutputContains("5", "6", "3")
    }

    @Test
    fun `task run twice with different dirs should be run twice`(tempDir: File) = tempDir.run {
        build_gradle("source")
        src("${"source"}/test.xml", "This\nis\na\nnew\nfile")
        runGradleTask().assertOutcome(SUCCESS)
        assertOutputContains("5")

        build_gradle("otherSource")
        src("${"otherSource"}/test.xml", "This\nis\na\nnew\nfile")
        runGradleTask().assertOutcome(SUCCESS)
        assertOutputContains("5")
    }

}