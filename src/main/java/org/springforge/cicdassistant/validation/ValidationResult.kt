package org.springforge.cicdassistant.validation

import java.time.Instant

/**
 * Result of validating CI/CD artifacts
 */
data class ValidationResult(
    /**
     * List of all issues found during validation
     */
    val issues: List<ValidationIssue>,
    
    /**
     * Timestamp when validation was performed
     */
    val timestamp: Instant = Instant.now(),
    
    /**
     * Total number of files validated
     */
    val filesValidated: Int = 0,
    
    /**
     * Total time taken for validation in milliseconds
     */
    val durationMs: Long = 0
) {
    /**
     * Returns true if validation passed with no errors
     */
    fun isSuccess(): Boolean = issues.none { it.severity == IssueSeverity.ERROR }
    
    /**
     * Returns count of issues by severity
     */
    fun getErrorCount(): Int = issues.count { it.severity == IssueSeverity.ERROR }
    fun getWarningCount(): Int = issues.count { it.severity == IssueSeverity.WARNING }
    fun getInfoCount(): Int = issues.count { it.severity == IssueSeverity.INFO }
    
    /**
     * Returns issues grouped by file path
     */
    fun getIssuesByFile(): Map<String, List<ValidationIssue>> {
        return issues.groupBy { it.filePath }
    }
    
    /**
     * Returns issues that have auto-fix suggestions
     */
    fun getFixableIssues(): List<ValidationIssue> {
        return issues.filter { it.autoFix != null && it.autoFix.isAutoApplicable }
    }
    
    /**
     * Returns a summary string for display
     */
    fun getSummary(): String {
        val errors = getErrorCount()
        val warnings = getWarningCount()
        val infos = getInfoCount()
        
        return buildString {
            if (errors > 0) append("❌ $errors error${if (errors != 1) "s" else ""}")
            if (warnings > 0) {
                if (isNotEmpty()) append(", ")
                append("⚠️ $warnings warning${if (warnings != 1) "s" else ""}")
            }
            if (infos > 0) {
                if (isNotEmpty()) append(", ")
                append("ℹ️ $infos info")
            }
            if (isEmpty()) append("✅ No issues found")
        }
    }
}
