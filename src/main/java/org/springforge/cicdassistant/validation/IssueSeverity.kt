package org.springforge.cicdassistant.validation

/**
 * Severity levels for validation issues
 */
enum class IssueSeverity {
    /**
     * Critical security or functionality issues that must be fixed
     */
    ERROR,
    
    /**
     * Important issues that should be addressed
     */
    WARNING,
    
    /**
     * Suggestions for best practices and improvements
     */
    INFO
}
