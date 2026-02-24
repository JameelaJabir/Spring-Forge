package org.springforge.cicdassistant.validation.validators

import org.springforge.cicdassistant.validation.AutoFix
import org.springforge.cicdassistant.validation.IssueSeverity
import org.springforge.cicdassistant.validation.ValidationIssue
import org.springforge.cicdassistant.validation.ValidationResult
import org.springforge.cicdassistant.validation.Validator
import org.springforge.cicdassistant.validation.executors.HadolintExecutor
import java.time.Instant

/**
 * Validator for Dockerfiles with focus on Spring Boot security best practices
 */
class DockerfileValidator : Validator {
    private val hadolintExecutor = HadolintExecutor()
    
    override fun validate(filePath: String, content: String): ValidationResult {
        val startTime = System.currentTimeMillis()
        val allIssues = mutableListOf<ValidationIssue>()
        
        // Run Hadolint first
        val hadolintResult = hadolintExecutor.validate(filePath, content)
        allIssues.addAll(hadolintResult.issues)
        
        // Run custom Spring Boot security rules
        allIssues.addAll(checkNonRootUser(filePath, content))
        allIssues.addAll(checkHealthCheck(filePath, content))
        allIssues.addAll(checkHardcodedSecrets(filePath, content))
        allIssues.addAll(checkMultiStage(filePath, content))
        allIssues.addAll(checkBaseImageTag(filePath, content))
        allIssues.addAll(checkPortExposure(filePath, content))
        allIssues.addAll(checkSpringBootSpecific(filePath, content))
        allIssues.addAll(checkDockerignore(filePath, content))
        allIssues.addAll(checkLayerOptimization(filePath, content))
        allIssues.addAll(checkSecretManagement(filePath, content))
        
        val duration = System.currentTimeMillis() - startTime
        
        return ValidationResult(
            issues = allIssues,
            timestamp = Instant.now(),
            filesValidated = 1,
            durationMs = duration
        )
    }
    
    override fun getName(): String = "Dockerfile Validator"
    
    override fun canValidate(filePath: String): Boolean =
        filePath.endsWith("Dockerfile") || filePath.contains("Dockerfile")
    
    /**
     * Check if Dockerfile uses a non-root user
     */
    private fun checkNonRootUser(filePath: String, content: String): List<ValidationIssue> {
        val lines = content.lines()
        val hasUserDirective = lines.any { 
            it.trim().startsWith("USER") && !it.contains("USER root")
        }
        
        if (!hasUserDirective) {
            val lastLine = lines.size
            return listOf(
                ValidationIssue(
                    ruleId = "SF001",
                    ruleName = "Non-Root User Required",
                    severity = IssueSeverity.ERROR,
                    message = "Dockerfile should run as non-root user for security",
                    filePath = filePath,
                    lineNumber = lastLine,
                    context = "Running containers as root is a security risk. Create a non-root user with USER directive.",
                    documentationUrl = "https://docs.docker.com/develop/develop-images/dockerfile_best-practices/#user",
                    autoFix = AutoFix(
                        description = "Add non-root user directive",
                        filePath = filePath,
                        lineNumber = lastLine,
                        originalContent = null,
                        fixedContent = """
                            |# Create non-root user
                            |RUN groupadd -r spring && useradd -r -g spring spring
                            |USER spring
                        """.trimMargin(),
                        isAutoApplicable = true
                    )
                )
            )
        }
        return emptyList()
    }
    
    /**
     * Check if Dockerfile includes HEALTHCHECK
     */
    private fun checkHealthCheck(filePath: String, content: String): List<ValidationIssue> {
        if (!content.contains("HEALTHCHECK")) {
            return listOf(
                ValidationIssue(
                    ruleId = "SF002",
                    ruleName = "Health Check Required",
                    severity = IssueSeverity.WARNING,
                    message = "Dockerfile should include a HEALTHCHECK instruction",
                    filePath = filePath,
                    context = "Health checks allow Docker and orchestrators to monitor container health.",
                    documentationUrl = "https://docs.docker.com/engine/reference/builder/#healthcheck",
                    autoFix = AutoFix(
                        description = "Add Spring Boot actuator health check",
                        filePath = filePath,
                        lineNumber = null,
                        originalContent = null,
                        fixedContent = "HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \\\n  CMD curl -f http://localhost:8080/actuator/health || exit 1",
                        isAutoApplicable = false
                    )
                )
            )
        }
        return emptyList()
    }
    
    /**
     * Check for hardcoded secrets and sensitive data
     */
    private fun checkHardcodedSecrets(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val secretPatterns = mapOf(
            "password=" to "Password",
            "secret=" to "Secret",
            "token=" to "Token",
            "api_key=" to "API Key",
            "apikey=" to "API Key",
            "AWS_ACCESS_KEY_ID=" to "AWS Access Key",
            "AWS_SECRET_ACCESS_KEY=" to "AWS Secret Key"
        )
        
        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            secretPatterns.forEach { (pattern, name) ->
                if (line.contains(pattern, ignoreCase = true) && 
                    !line.trim().startsWith("#") &&
                    line.contains("ENV", ignoreCase = true)) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "SF003",
                            ruleName = "No Hardcoded Secrets",
                            severity = IssueSeverity.ERROR,
                            message = "$name appears to be hardcoded in ENV variable",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Use Docker secrets, build args, or environment variables at runtime instead.",
                            documentationUrl = "https://docs.docker.com/engine/swarm/secrets/"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check if Dockerfile uses multi-stage builds
     */
    private fun checkMultiStage(filePath: String, content: String): List<ValidationIssue> {
        val fromCount = content.lines().count { it.trim().startsWith("FROM") }
        
        if (fromCount < 2) {
            return listOf(
                ValidationIssue(
                    ruleId = "SF004",
                    ruleName = "Multi-Stage Build Recommended",
                    severity = IssueSeverity.INFO,
                    message = "Consider using multi-stage builds to reduce image size",
                    filePath = filePath,
                    context = "Multi-stage builds separate build dependencies from runtime, creating smaller images.",
                    documentationUrl = "https://docs.docker.com/develop/develop-images/multistage-build/"
                )
            )
        }
        return emptyList()
    }
    
    /**
     * Check if base image has a specific version tag
     */
    private fun checkBaseImageTag(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val lines = content.lines()
        
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("FROM")) {
                val imagePart = line.substringAfter("FROM").trim().split(" ")[0]
                if (imagePart.endsWith(":latest") || !imagePart.contains(":")) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "SF005",
                            ruleName = "Pin Base Image Version",
                            severity = IssueSeverity.WARNING,
                            message = "Base image should use a specific version tag, not 'latest'",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Using 'latest' tag can lead to unpredictable builds. Pin to specific versions.",
                            documentationUrl = "https://docs.docker.com/develop/dev-best-practices/"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check port exposure for Spring Boot
     */
    private fun checkPortExposure(filePath: String, content: String): List<ValidationIssue> {
        val hasExposeDirective = content.contains("EXPOSE")
        
        if (!hasExposeDirective) {
            return listOf(
                ValidationIssue(
                    ruleId = "SF006",
                    ruleName = "Port Exposure Required",
                    severity = IssueSeverity.WARNING,
                    message = "Dockerfile should expose the application port with EXPOSE directive",
                    filePath = filePath,
                    context = "Spring Boot typically runs on port 8080. Document this with EXPOSE.",
                    documentationUrl = "https://docs.docker.com/engine/reference/builder/#expose",
                    autoFix = AutoFix(
                        description = "Add EXPOSE directive for Spring Boot default port",
                        filePath = filePath,
                        lineNumber = null,
                        originalContent = null,
                        fixedContent = "EXPOSE 8080",
                        isAutoApplicable = false
                    )
                )
            )
        }
        return emptyList()
    }
    
    /**
     * Spring Boot specific checks
     */
    private fun checkSpringBootSpecific(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        // Check for Java tool options
        if (!content.contains("JAVA_TOOL_OPTIONS") && !content.contains("JAVA_OPTS")) {
            issues.add(
                ValidationIssue(
                    ruleId = "SF007",
                    ruleName = "Java Options Configuration",
                    severity = IssueSeverity.INFO,
                    message = "Consider setting JAVA_TOOL_OPTIONS for memory and GC tuning",
                    filePath = filePath,
                    context = "Configure JVM options like heap size, GC, and other performance settings.",
                    autoFix = AutoFix(
                        description = "Add Java tool options for production",
                        filePath = filePath,
                        lineNumber = null,
                        originalContent = null,
                        fixedContent = "ENV JAVA_TOOL_OPTIONS=\"-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0\"",
                        isAutoApplicable = false
                    )
                )
            )
        }
        
        // Check for Spring profiles
        if (!content.contains("SPRING_PROFILES_ACTIVE")) {
            issues.add(
                ValidationIssue(
                    ruleId = "SF008",
                    ruleName = "Spring Profile Configuration",
                    severity = IssueSeverity.INFO,
                    message = "Consider setting SPRING_PROFILES_ACTIVE for environment-specific configuration",
                    filePath = filePath,
                    context = "Use Spring profiles to manage environment-specific settings."
                )
            )
        }
        
        return issues
    }
    
    /**
     * Check for .dockerignore recommendation
     */
    private fun checkDockerignore(filePath: String, content: String): List<ValidationIssue> {
        // This is a soft check - we'll just recommend it
        return listOf(
            ValidationIssue(
                ruleId = "SF009",
                ruleName = "Dockerignore File Recommended",
                severity = IssueSeverity.INFO,
                message = "Ensure a .dockerignore file exists to exclude unnecessary files",
                filePath = filePath,
                context = "A .dockerignore file reduces build context size and speeds up builds.",
                documentationUrl = "https://docs.docker.com/engine/reference/builder/#dockerignore-file"
            )
        )
    }
    
    /**
     * Check for layer optimization
     */
    private fun checkLayerOptimization(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val lines = content.lines()
        
        // Check for multiple RUN commands that could be combined
        var consecutiveRuns = 0
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("RUN")) {
                consecutiveRuns++
            } else if (line.trim().isNotEmpty() && !line.trim().startsWith("#")) {
                if (consecutiveRuns >= 3) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "SF010",
                            ruleName = "Layer Optimization",
                            severity = IssueSeverity.INFO,
                            message = "Multiple consecutive RUN commands can be combined to reduce layers",
                            filePath = filePath,
                            lineNumber = index,
                            context = "Combine RUN commands with && to reduce image layers and size.",
                            documentationUrl = "https://docs.docker.com/develop/develop-images/dockerfile_best-practices/#minimize-the-number-of-layers"
                        )
                    )
                }
                consecutiveRuns = 0
            }
        }
        
        return issues
    }
    
    /**
     * Check for proper secret management
     */
    private fun checkSecretManagement(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        
        // Check if secrets are copied into the image
        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("COPY") || trimmedLine.startsWith("ADD")) {
                val sensitiveFiles = listOf(
                    ".env", "credentials", "keystore", ".key", ".pem", 
                    "application-prod.yml", "application-prod.properties"
                )
                
                if (sensitiveFiles.any { trimmedLine.contains(it, ignoreCase = true) }) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "SF011",
                            ruleName = "Sensitive File Copy",
                            severity = IssueSeverity.ERROR,
                            message = "Sensitive file appears to be copied into the image",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Avoid copying sensitive files. Use Docker secrets or mount them at runtime.",
                            documentationUrl = "https://docs.docker.com/engine/swarm/secrets/"
                        )
                    )
                }
            }
        }
        
        return issues
    }
}
