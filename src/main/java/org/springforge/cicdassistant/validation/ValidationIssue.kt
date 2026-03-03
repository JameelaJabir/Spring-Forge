package org.springforge.cicdassistant.validation

/**
 * Represents a single validation issue found in a CI/CD artifact
 */
data class ValidationIssue(
    /**
     * Unique identifier for the rule that detected this issue
     */
    val ruleId: String,
    
    /**
     * Human-readable rule name
     */
    val ruleName: String,
    
    /**
     * Severity level of the issue
     */
    val severity: IssueSeverity,
    
    /**
     * Detailed message describing the issue
     */
    val message: String,
    
    /**
     * File path where the issue was found
     */
    val filePath: String,
    
    /**
     * Line number where the issue occurs (1-indexed, null if not line-specific)
     */
    val lineNumber: Int? = null,
    
    /**
     * Column number where the issue occurs (1-indexed, null if not column-specific)
     */
    val columnNumber: Int? = null,
    
    /**
     * Additional context or explanation
     */
    val context: String? = null,
    
    /**
     * Documentation URL for more information about this rule
     */
    val documentationUrl: String? = null,
    
    /**
     * Suggested fix for this issue (null if no fix available)
     */
    val autoFix: AutoFix? = null
) {
    /**
     * Returns a formatted location string (e.g., "Dockerfile:12:5")
     */
    fun getLocation(): String {
        return buildString {
            append(filePath)
            lineNumber?.let { 
                append(":$it")
                columnNumber?.let { col -> append(":$col") }
            }
        }
    }
    
    /**
     * Returns a severity symbol for display
     */
    fun getSeveritySymbol(): String = when (severity) {
        IssueSeverity.ERROR -> "❌"
        IssueSeverity.WARNING -> "⚠️"
        IssueSeverity.INFO -> "ℹ️"
    }
}
