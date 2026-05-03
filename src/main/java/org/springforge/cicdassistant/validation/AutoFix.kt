package org.springforge.cicdassistant.validation

/**
 * Represents an automatic fix suggestion for a validation issue
 */
data class AutoFix(
    /**
     * Human-readable description of what this fix does
     */
    val description: String,
    
    /**
     * The file path where the fix should be applied
     */
    val filePath: String,
    
    /**
     * Line number where the issue occurs (1-indexed)
     */
    val lineNumber: Int? = null,
    
    /**
     * Original content to be replaced (null for insertions)
     */
    val originalContent: String? = null,
    
    /**
     * New content to insert or replace with
     */
    val fixedContent: String,
    
    /**
     * Whether this fix can be applied automatically
     */
    val isAutoApplicable: Boolean = true
)
