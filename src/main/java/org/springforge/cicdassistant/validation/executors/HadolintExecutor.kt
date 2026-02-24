package org.springforge.cicdassistant.validation.executors

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.validation.AutoFix
import org.springforge.cicdassistant.validation.IssueSeverity
import org.springforge.cicdassistant.validation.ValidationIssue
import org.springforge.cicdassistant.validation.ValidationResult
import java.io.File
import java.nio.file.Files
import java.time.Instant

/**
 * Executor for Hadolint - a Dockerfile linter
 * Documentation: https://github.com/hadolint/hadolint
 */
class HadolintExecutor {
    private val mapper = jacksonObjectMapper()
    private val hadolintCommand = "hadolint"
    
    /**
     * Checks if Hadolint is installed and available
     */
    fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(hadolintCommand, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets the installed Hadolint version
     */
    fun getVersion(): String? {
        return try {
            val process = ProcessBuilder(hadolintCommand, "--version")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validates a Dockerfile using Hadolint
     * 
     * @param dockerfilePath Path to the Dockerfile
     * @param content Content of the Dockerfile
     * @return ValidationResult with issues found by Hadolint
     */
    fun validate(dockerfilePath: String, content: String): ValidationResult {
        if (!isAvailable()) {
            return ValidationResult(
                issues = listOf(
                    ValidationIssue(
                        ruleId = "HADOLINT_NOT_FOUND",
                        ruleName = "Hadolint Not Installed",
                        severity = IssueSeverity.WARNING,
                        message = "Hadolint is not installed. Install it from: https://github.com/hadolint/hadolint",
                        filePath = dockerfilePath,
                        documentationUrl = "https://github.com/hadolint/hadolint#install"
                    )
                ),
                filesValidated = 1
            )
        }
        
        val tempFile = Files.createTempFile("dockerfile", ".tmp").toFile()
        try {
            tempFile.writeText(content)
            
            val startTime = System.currentTimeMillis()
            val result = runHadolint(tempFile.absolutePath, dockerfilePath)
            val duration = System.currentTimeMillis() - startTime
            
            return result.copy(
                timestamp = Instant.now(),
                durationMs = duration
            )
        } finally {
            tempFile.delete()
        }
    }
    
    /**
     * Runs Hadolint CLI and parses the JSON output
     */
    private fun runHadolint(tempFilePath: String, originalPath: String): ValidationResult {
        try {
            val process = ProcessBuilder(
                hadolintCommand,
                "--format", "json",
                "--no-fail",
                tempFilePath
            )
                .redirectErrorStream(false)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val errorOutput = process.errorStream.bufferedReader().readText()
            process.waitFor()
            
            // If there's an error running hadolint
            if (process.exitValue() != 0 && output.isBlank()) {
                return ValidationResult(
                    issues = listOf(
                        ValidationIssue(
                            ruleId = "HADOLINT_ERROR",
                            ruleName = "Hadolint Execution Error",
                            severity = IssueSeverity.ERROR,
                            message = "Failed to run Hadolint: $errorOutput",
                            filePath = originalPath
                        )
                    ),
                    filesValidated = 1
                )
            }
            
            // Parse JSON output
            if (output.isBlank() || output.trim() == "[]") {
                return ValidationResult(
                    issues = emptyList(),
                    filesValidated = 1
                )
            }
            
            val issues = parseHadolintOutput(output, originalPath)
            return ValidationResult(
                issues = issues,
                filesValidated = 1
            )
        } catch (e: Exception) {
            return ValidationResult(
                issues = listOf(
                    ValidationIssue(
                        ruleId = "HADOLINT_EXCEPTION",
                        ruleName = "Hadolint Execution Exception",
                        severity = IssueSeverity.ERROR,
                        message = "Exception while running Hadolint: ${e.message}",
                        filePath = originalPath
                    )
                ),
                filesValidated = 1
            )
        }
    }
    
    /**
     * Parses Hadolint JSON output into ValidationIssue objects
     * 
     * Hadolint JSON format:
     * [
     *   {
     *     "line": 1,
     *     "column": 1,
     *     "level": "error",
     *     "code": "DL3006",
     *     "message": "Always tag the version of an image explicitly",
     *     "file": "/tmp/dockerfile.tmp"
     *   }
     * ]
     */
    private fun parseHadolintOutput(jsonOutput: String, filePath: String): List<ValidationIssue> {
        return try {
            val issues = mapper.readValue<List<JsonNode>>(jsonOutput)
            issues.map { issueNode ->
                val ruleCode = issueNode.get("code")?.asText() ?: "UNKNOWN"
                val level = issueNode.get("level")?.asText()?.uppercase() ?: "ERROR"
                val message = issueNode.get("message")?.asText() ?: "Unknown issue"
                val line = issueNode.get("line")?.asInt()
                val column = issueNode.get("column")?.asInt()
                
                ValidationIssue(
                    ruleId = ruleCode,
                    ruleName = getHadolintRuleName(ruleCode),
                    severity = mapHadolintSeverity(level),
                    message = message,
                    filePath = filePath,
                    lineNumber = line,
                    columnNumber = column,
                    documentationUrl = "https://github.com/hadolint/hadolint/wiki/$ruleCode",
                    autoFix = generateAutoFix(ruleCode, message, filePath, line)
                )
            }
        } catch (e: Exception) {
            listOf(
                ValidationIssue(
                    ruleId = "HADOLINT_PARSE_ERROR",
                    ruleName = "Hadolint Parse Error",
                    severity = IssueSeverity.ERROR,
                    message = "Failed to parse Hadolint output: ${e.message}",
                    filePath = filePath
                )
            )
        }
    }
    
    /**
     * Maps Hadolint severity levels to our IssueSeverity enum
     */
    private fun mapHadolintSeverity(level: String): IssueSeverity {
        return when (level.uppercase()) {
            "ERROR" -> IssueSeverity.ERROR
            "WARNING" -> IssueSeverity.WARNING
            "INFO", "STYLE" -> IssueSeverity.INFO
            else -> IssueSeverity.WARNING
        }
    }
    
    /**
     * Gets a human-readable name for a Hadolint rule
     */
    private fun getHadolintRuleName(ruleCode: String): String {
        return when (ruleCode) {
            "DL3000" -> "Use absolute WORKDIR"
            "DL3001" -> "Delete apt-get lists after install"
            "DL3002" -> "Don't switch users repeatedly"
            "DL3003" -> "Use WORKDIR instead of cd"
            "DL3004" -> "Don't use sudo"
            "DL3006" -> "Pin base image version"
            "DL3007" -> "Use specific tags for base image"
            "DL3008" -> "Pin versions in apt-get install"
            "DL3009" -> "Delete apt-get cache after install"
            "DL3013" -> "Pin versions in pip install"
            "DL3014" -> "Use -y flag with apt-get"
            "DL3015" -> "Avoid additional packages with yum"
            "DL3016" -> "Pin versions in npm install"
            "DL3018" -> "Pin versions in apk add"
            "DL3019" -> "Use --no-cache with apk add"
            "DL3020" -> "Use COPY instead of ADD"
            "DL3025" -> "Use JSON notation for CMD and ENTRYPOINT"
            "DL3045" -> "Use COPY with --chown"
            "DL4006" -> "Set SHELL pipefail option"
            "SC1091" -> "Source or include file not found"
            "SC2046" -> "Quote to prevent word splitting"
            "SC2086" -> "Quote variables to prevent splitting"
            else -> ruleCode
        }
    }
    
    /**
     * Generates auto-fix suggestions for common Hadolint issues
     */
    private fun generateAutoFix(ruleCode: String, message: String, filePath: String, line: Int?): AutoFix? {
        if (line == null) return null
        
        return when (ruleCode) {
            "DL3006", "DL3007" -> AutoFix(
                description = "Pin the base image to a specific version tag",
                filePath = filePath,
                lineNumber = line,
                originalContent = null,
                fixedContent = "# Add a specific version tag to the FROM statement",
                isAutoApplicable = false
            )
            
            "DL3008" -> AutoFix(
                description = "Pin package versions in apt-get install",
                filePath = filePath,
                lineNumber = line,
                originalContent = null,
                fixedContent = "# Add version constraints like: apt-get install -y package=version",
                isAutoApplicable = false
            )
            
            "DL3009" -> AutoFix(
                description = "Clean up apt cache after installation",
                filePath = filePath,
                lineNumber = line,
                originalContent = null,
                fixedContent = "&& rm -rf /var/lib/apt/lists/*",
                isAutoApplicable = false
            )
            
            else -> null
        }
    }
}
