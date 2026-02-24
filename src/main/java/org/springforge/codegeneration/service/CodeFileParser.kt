package org.springforge.codegeneration.service

/**
 * Represents a single generated source file parsed from the LLM response.
 */
data class GeneratedFile(
    /** Relative path, e.g. "src/main/java/com/example/demo/entity/User.java" */
    val relativePath: String,
    /** Full Java source code */
    val content: String
)

/**
 * Parses the raw LLM output that uses the ===FILE: ...=== / ===END_FILE=== delimiters
 * into a list of [GeneratedFile] objects.
 */
object CodeFileParser {

    private val FILE_START = Regex("""===FILE:\s*(.+?)\s*===""")
    private const val FILE_END = "===END_FILE==="

    /**
     * Parse raw LLM response text into structured file objects.
     *
     * Expected format:
     * ```
     * ===FILE: src/main/java/com/example/entity/User.java===
     * package com.example.entity;
     * ...
     * ===END_FILE===
     * ```
     *
     * @return list of parsed files; empty list if nothing found
     */
    fun parse(raw: String): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()

        // Normalize line endings
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")

        // Strip any leading/trailing markdown code fences the LLM might add
        val cleaned = text
            .replace(Regex("""^```[\w]*\n""", RegexOption.MULTILINE), "")
            .replace(Regex("""\n```\s*$""", RegexOption.MULTILINE), "")

        var remaining = cleaned
        while (true) {
            val startMatch = FILE_START.find(remaining) ?: break
            val filePath = startMatch.groupValues[1].trim()

            // Content starts after the ===FILE: ...=== line
            val contentStart = startMatch.range.last + 1
            val endIndex = remaining.indexOf(FILE_END, contentStart)

            if (endIndex == -1) {
                // Last file block has no END marker — take everything remaining
                val content = remaining.substring(contentStart).trim()
                if (content.isNotBlank()) {
                    files.add(GeneratedFile(normalizeFilePath(filePath), content))
                }
                break
            }

            val content = remaining.substring(contentStart, endIndex).trim()
            if (content.isNotBlank()) {
                files.add(GeneratedFile(normalizeFilePath(filePath), content))
            }

            remaining = remaining.substring(endIndex + FILE_END.length)
        }

        return files
    }

    /**
     * Normalise path separators and strip any leading ./ or /
     */
    private fun normalizeFilePath(path: String): String {
        return path
            .replace('\\', '/')
            .removePrefix("./")
            .removePrefix("/")
            .trim()
    }
}
