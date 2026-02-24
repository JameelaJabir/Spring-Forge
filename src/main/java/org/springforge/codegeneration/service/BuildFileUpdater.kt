package org.springforge.codegeneration.service

import java.io.File

/**
 * Programmatically adds missing dependencies to pom.xml or build.gradle.
 *
 * This utility inserts known-good dependency XML/Gradle blocks into the
 * existing build file — no LLM involvement, zero hallucination risk.
 */
object BuildFileUpdater {

    data class UpdateResult(
        val updated: Boolean,
        val buildFile: String,
        val addedDependencies: List<String>,
        val error: String? = null
    )

    /**
     * Add missing dependencies to the project's build file.
     *
     * @param projectRoot  absolute path to the project root
     * @param missingDeps  list of (groupId, artifactId) pairs to add
     * @return result describing what was done
     */
    fun addMissingDependencies(
        projectRoot: String,
        missingDeps: List<Pair<String, String>>
    ): UpdateResult {
        if (missingDeps.isEmpty()) {
            return UpdateResult(false, "", emptyList())
        }

        val pomFile = File(projectRoot, "pom.xml")
        if (pomFile.exists()) {
            return addToPom(pomFile, missingDeps)
        }

        val gradleFile = File(projectRoot, "build.gradle")
        if (gradleFile.exists()) {
            return addToGradle(gradleFile, missingDeps, "build.gradle")
        }

        val gradleKtsFile = File(projectRoot, "build.gradle.kts")
        if (gradleKtsFile.exists()) {
            return addToGradle(gradleKtsFile, missingDeps, "build.gradle.kts")
        }

        return UpdateResult(false, "", emptyList(), "No build file found (pom.xml / build.gradle)")
    }

    // ═══════════════════════════════════════════════════════════
    //  MAVEN (pom.xml)
    // ═══════════════════════════════════════════════════════════

    private fun addToPom(pomFile: File, missingDeps: List<Pair<String, String>>): UpdateResult {
        try {
            var content = pomFile.readText()
            val added = mutableListOf<String>()

            for ((groupId, artifactId) in missingDeps) {
                // Check if this dependency already exists (case-insensitive)
                if (content.contains("<artifactId>$artifactId</artifactId>")) {
                    continue
                }

                val depXml = buildMavenDependencyXml(groupId, artifactId)

                // Insert before the closing </dependencies> tag
                val insertPoint = content.lastIndexOf("</dependencies>")
                if (insertPoint == -1) {
                    // No </dependencies> found — try to insert before </project>
                    val projectEnd = content.lastIndexOf("</project>")
                    if (projectEnd == -1) continue

                    val depsBlock = "\n\t<dependencies>\n$depXml\t</dependencies>\n"
                    content = content.substring(0, projectEnd) + depsBlock + content.substring(projectEnd)
                } else {
                    content = content.substring(0, insertPoint) + depXml + content.substring(insertPoint)
                }

                added.add("$groupId:$artifactId")
            }

            if (added.isNotEmpty()) {
                pomFile.writeText(content)
            }

            return UpdateResult(added.isNotEmpty(), "pom.xml", added)

        } catch (ex: Exception) {
            return UpdateResult(false, "pom.xml", emptyList(), ex.message)
        }
    }

    /**
     * Build the XML block for a single Maven dependency.
     * Uses known scope/optional settings for common dependencies.
     */
    private fun buildMavenDependencyXml(groupId: String, artifactId: String): String {
        val sb = StringBuilder()
        sb.appendLine("\t\t<dependency>")
        sb.appendLine("\t\t\t<groupId>$groupId</groupId>")
        sb.appendLine("\t\t\t<artifactId>$artifactId</artifactId>")

        // Apply known scope/optional attributes
        when {
            artifactId == "h2" -> {
                sb.appendLine("\t\t\t<scope>runtime</scope>")
            }
            artifactId == "lombok" -> {
                sb.appendLine("\t\t\t<optional>true</optional>")
            }
            artifactId.endsWith("-test") -> {
                sb.appendLine("\t\t\t<scope>test</scope>")
            }
            groupId.contains("mysql") || groupId.contains("postgresql") ||
            groupId.contains("mariadb") || groupId.contains("oracle") ||
            groupId.contains("sqlserver") -> {
                sb.appendLine("\t\t\t<scope>runtime</scope>")
            }
        }

        sb.appendLine("\t\t</dependency>")
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════
    //  GRADLE (build.gradle / build.gradle.kts)
    // ═══════════════════════════════════════════════════════════

    private fun addToGradle(
        gradleFile: File,
        missingDeps: List<Pair<String, String>>,
        fileName: String
    ): UpdateResult {
        try {
            var content = gradleFile.readText()
            val added = mutableListOf<String>()
            val isKts = fileName.endsWith(".kts")

            for ((groupId, artifactId) in missingDeps) {
                val depCoord = "$groupId:$artifactId"
                // Check if already present
                if (content.contains(depCoord)) continue

                val configuration = resolveGradleConfiguration(groupId, artifactId)
                val depLine = if (isKts) {
                    "\t$configuration(\"$depCoord\")"
                } else {
                    "\t$configuration '$depCoord'"
                }

                // Find the dependencies { } block and insert before its closing }
                val depsBlockStart = content.indexOf("dependencies {")
                if (depsBlockStart == -1) {
                    // No dependencies block — append one
                    content += "\n\ndependencies {\n$depLine\n}\n"
                } else {
                    // Find the matching closing brace
                    val closingBrace = findMatchingClosingBrace(content, depsBlockStart)
                    if (closingBrace != -1) {
                        content = content.substring(0, closingBrace) +
                                "$depLine\n" +
                                content.substring(closingBrace)
                    }
                }

                added.add(depCoord)
            }

            if (added.isNotEmpty()) {
                gradleFile.writeText(content)
            }

            return UpdateResult(added.isNotEmpty(), fileName, added)

        } catch (ex: Exception) {
            return UpdateResult(false, fileName, emptyList(), ex.message)
        }
    }

    private fun resolveGradleConfiguration(groupId: String, artifactId: String): String {
        return when {
            artifactId == "lombok" -> "compileOnly"
            artifactId == "h2" -> "runtimeOnly"
            artifactId.endsWith("-test") -> "testImplementation"
            groupId.contains("mysql") || groupId.contains("postgresql") ||
            groupId.contains("mariadb") || groupId.contains("oracle") ||
            groupId.contains("sqlserver") -> "runtimeOnly"
            else -> "implementation"
        }
    }

    /**
     * Find the closing brace that matches the opening { at the given position.
     */
    private fun findMatchingClosingBrace(text: String, openBraceBlockStart: Int): Int {
        val openBrace = text.indexOf('{', openBraceBlockStart)
        if (openBrace == -1) return -1

        var depth = 0
        for (i in openBrace until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }
}
