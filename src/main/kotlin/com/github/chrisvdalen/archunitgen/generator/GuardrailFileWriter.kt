package com.github.chrisvdalen.archunitgen.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Writes [GuardrailFile]s under the project root according to their [WriteMode],
 * then refreshes IntelliJ's VFS so the files show up in the project view.
 */
class GuardrailFileWriter(private val project: Project) {

    fun writeAll(files: List<GuardrailFile>): List<WriteResult> {
        val projectPath = project.basePath
            ?: return listOf(WriteResult.Failure("Project base path is null – is this a valid IntelliJ project?"))

        val results = files.map { write(Path.of(projectPath, it.relativePath), it) }

        val written = results.filterIsInstance<WriteResult.Success>().map { Path.of(it.path).toFile() }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(true, false, false, *written.toTypedArray())
        }
        return results
    }

    private fun write(target: Path, file: GuardrailFile): WriteResult = try {
        val existing = if (target.exists()) target.readText(Charsets.UTF_8) else null
        val content = file.mode.contentToWrite(existing, file.content)
        if (content == null) {
            WriteResult.AlreadyExists(target.toString())
        } else {
            Files.createDirectories(target.parent)
            target.writeText(content, Charsets.UTF_8)
            if (file.relativePath == AgentGuardrailsGenerator.HOOK_SCRIPT_PATH) target.toFile().setExecutable(true)
            WriteResult.Success(target.toString())
        }
    } catch (e: Exception) {
        WriteResult.Failure("Could not write ${file.relativePath}: ${e.message}")
    }
}
