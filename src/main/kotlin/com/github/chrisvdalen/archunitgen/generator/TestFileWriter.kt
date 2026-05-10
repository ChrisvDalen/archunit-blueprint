package com.github.chrisvdalen.archunitgen.generator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Writes a [GeneratedTestFile] to disk under the project's `src/test/java` tree
 * and notifies IntelliJ's VFS so the file appears in the project view immediately.
 */
class TestFileWriter(private val project: Project) {

    fun write(generatedFile: GeneratedTestFile, overwrite: Boolean = false): WriteResult {
        val projectPath = project.basePath
            ?: return WriteResult.Failure("Project base path is null – is this a valid IntelliJ project?")

        val targetPath: Path = Path.of(projectPath, generatedFile.relativePath)

        return try {
            Files.createDirectories(targetPath.parent)

            if (targetPath.exists() && !overwrite) {
                return WriteResult.AlreadyExists(targetPath.toString())
            }

            targetPath.writeText(generatedFile.code, Charsets.UTF_8)

            // Refresh the VFS off the EDT so IntelliJ picks up the new file
            ApplicationManager.getApplication().invokeLater {
                VfsUtil.markDirtyAndRefresh(true, false, false, targetPath.toFile())
            }

            WriteResult.Success(targetPath.toString())
        } catch (e: Exception) {
            WriteResult.Failure("Could not write file: ${e.message}")
        }
    }
}

sealed class WriteResult {
    data class Success(val path: String) : WriteResult()
    data class AlreadyExists(val path: String) : WriteResult()
    data class Failure(val reason: String) : WriteResult()
}
