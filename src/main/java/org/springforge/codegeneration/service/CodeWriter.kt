package org.springforge.codegeneration.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Result of writing generated files to disk.
 */
data class CodeWriteResult(
    val written: List<String>,          // paths successfully written
    val skipped: List<String>,          // paths skipped (already exist)
    val errors: List<Pair<String, String>>  // (path, errorMessage)
)

/**
 * Writes [GeneratedFile] objects into the correct location
 * under the project root, creating directories as needed.
 */
object CodeWriter {

    /**
     * Write all generated files under [projectRoot].
     *
     * Each [GeneratedFile.relativePath] is expected to look like:
     *   src/main/java/com/example/demo/entity/User.java
     *
     * @param projectRoot  absolute path to the project root (where src/ lives)
     * @param files        parsed generated files from the LLM
     * @param overwrite    if true, overwrite existing files; otherwise skip them
     */
    fun writeAll(
        projectRoot: String,
        files: List<GeneratedFile>,
        overwrite: Boolean = false
    ): CodeWriteResult {

        val written = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val errors = mutableListOf<Pair<String, String>>()

        for (gf in files) {
            try {
                val target = File(projectRoot, gf.relativePath)

                if (target.exists() && !overwrite) {
                    skipped.add(gf.relativePath)
                    continue
                }

                // Create parent directories
                target.parentFile?.mkdirs()

                // Write the file
                target.writeText(gf.content)
                written.add(gf.relativePath)

            } catch (ex: Exception) {
                errors.add(gf.relativePath to (ex.message ?: "Unknown error"))
            }
        }

        return CodeWriteResult(written, skipped, errors)
    }

    /**
     * Refresh the IntelliJ VFS so newly created files show up in the project tree.
     * Must be called on the EDT or inside invokeLater.
     */
    fun refreshProjectFiles(project: Project, projectRoot: String) {
        ApplicationManager.getApplication().invokeLater {
            val rootVf: VirtualFile? =
                LocalFileSystem.getInstance().refreshAndFindFileByPath(projectRoot)
            rootVf?.refresh(true, true)
        }
    }
}
