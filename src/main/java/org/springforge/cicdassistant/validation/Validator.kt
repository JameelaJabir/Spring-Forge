package org.springforge.cicdassistant.validation

/**
 * Base interface for all validators
 */
interface Validator {
    /**
     * Validates the given file content
     * 
     * @param filePath Path to the file being validated
     * @param content Content of the file
     * @return ValidationResult containing any issues found
     */
    fun validate(filePath: String, content: String): ValidationResult
    
    /**
     * Returns the name of this validator
     */
    fun getName(): String
    
    /**
     * Returns true if this validator can validate the given file
     */
    fun canValidate(filePath: String): Boolean
}
