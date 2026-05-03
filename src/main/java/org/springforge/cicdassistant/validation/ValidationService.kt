package org.springforge.cicdassistant.validation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.springforge.cicdassistant.validation.validators.DockerComposeValidator
import org.springforge.cicdassistant.validation.validators.DockerfileValidator
import org.springforge.cicdassistant.validation.validators.GitHubActionsValidator
import java.time.Instant

/**
 * Service that orchestrates validation of CI/CD artifacts
 */
@Service(Service.Level.PROJECT)
class ValidationService(private val project: Project) {
    
    private val validators: List<Validator> = listOf(
        DockerfileValidator(),
        DockerComposeValidator(),
        GitHubActionsValidator()
    )
    
    /**
     * Validates a single file
     */
    fun validateFile(filePath: String, content: String): ValidationResult {
        val validator = validators.find { it.canValidate(filePath) }
            ?: return ValidationResult(
                issues = listOf(
                    ValidationIssue(
                        ruleId = "NO_VALIDATOR",
                        ruleName = "No Validator Available",
                        severity = IssueSeverity.INFO,
                        message = "No validator available for file: $filePath",
                        filePath = filePath
                    )
                ),
                filesValidated = 1
            )
        
        return validator.validate(filePath, content)
    }
    
    /**
     * Validates multiple files and combines results
     */
    fun validateFiles(files: Map<String, String>): ValidationResult {
        val startTime = Instant.now()
        val allIssues = mutableListOf<ValidationIssue>()
        var totalDuration = 0L
        
        files.forEach { (filePath, content) ->
            if (content.isNotBlank()) {
                val result = validateFile(filePath, content)
                allIssues.addAll(result.issues)
                totalDuration += result.durationMs
            }
        }
        
        return ValidationResult(
            issues = allIssues,
            timestamp = startTime,
            filesValidated = files.size,
            durationMs = totalDuration
        )
    }
    
    /**
     * Validates generated CI/CD artifacts
     */
    fun validateGeneratedArtifacts(
        dockerfile: String?,
        dockerCompose: String?,
        githubWorkflow: String?,
        dockerfilePath: String? = null,
        dockerComposePath: String? = null,
        githubWorkflowPath: String? = null
    ): ValidationResult {
        val files = mutableMapOf<String, String>()
        
        dockerfile?.let { 
            files[dockerfilePath ?: "Dockerfile"] = it 
        }
        dockerCompose?.let { 
            files[dockerComposePath ?: "docker-compose.yml"] = it 
        }
        githubWorkflow?.let { 
            files[githubWorkflowPath ?: ".github/workflows/ci.yml"] = it 
        }
        
        return validateFiles(files)
    }
    
    /**
     * Gets all available validators
     */
    fun getValidators(): List<Validator> = validators
    
    /**
     * Checks if a validator is available for the given file
     */
    fun hasValidatorFor(filePath: String): Boolean {
        return validators.any { it.canValidate(filePath) }
    }
    
    companion object {
        fun getInstance(project: Project): ValidationService {
            return project.getService(ValidationService::class.java)
        }
    }
}
