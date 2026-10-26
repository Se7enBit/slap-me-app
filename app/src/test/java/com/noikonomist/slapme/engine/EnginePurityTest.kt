package com.noikonomist.slapme.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** engine/ must stay plain Kotlin so all of its rules are testable on the JVM. */
class EnginePurityTest {

    @Test
    fun engineSourcesDoNotImportAndroid() {
        val dir = File("src/main/java/com/noikonomist/slapme/engine")
        val sources = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("no engine sources found in ${dir.absolutePath}", sources.isNotEmpty())

        val offenders = sources.flatMap { file ->
            file.readLines()
                .filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
                .map { "${file.name}: $it" }
        }
        assertTrue("Android imports in engine/:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
