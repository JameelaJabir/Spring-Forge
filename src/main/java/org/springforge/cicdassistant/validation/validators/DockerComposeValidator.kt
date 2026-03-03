package org.springforge.cicdassistant.validation.validators

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springforge.cicdassistant.validation.AutoFix
import org.springforge.cicdassistant.validation.IssueSeverity
import org.springforge.cicdassistant.validation.ValidationIssue
import org.springforge.cicdassistant.validation.ValidationResult
import org.springforge.cicdassistant.validation.Validator
import java.time.Instant

/**
 * Validator for Docker Compose files with security best practices
 */
class DockerComposeValidator : Validator {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    
    override fun validate(filePath: String, content: String): ValidationResult {
        val startTime = System.currentTimeMillis()
        val allIssues = mutableListOf<ValidationIssue>()
        
        try {
            val yaml = yamlMapper.readTree(content)
            
            // Run all validation checks
            allIssues.addAll(checkPrivilegedMode(filePath, yaml))
            allIssues.addAll(checkResourceLimits(filePath, yaml))
            allIssues.addAll(checkPortBinding(filePath, yaml))
            allIssues.addAll(checkNetworks(filePath, yaml))
            allIssues.addAll(checkHealthChecks(filePath, yaml))
            allIssues.addAll(checkSecrets(filePath, yaml, content))
            allIssues.addAll(checkVolumeSecurity(filePath, yaml))
            allIssues.addAll(checkSecurityOptions(filePath, yaml))
            allIssues.addAll(checkRestartPolicy(filePath, yaml))
            allIssues.addAll(checkLogging(filePath, yaml))
            
        } catch (e: Exception) {
            allIssues.add(
                ValidationIssue(
                    ruleId = "DC_PARSE_ERROR",
                    ruleName = "YAML Parse Error",
                    severity = IssueSeverity.ERROR,
                    message = "Failed to parse Docker Compose file: ${e.message}",
                    filePath = filePath
                )
            )
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        return ValidationResult(
            issues = allIssues,
            timestamp = Instant.now(),
            filesValidated = 1,
            durationMs = duration
        )
    }
    
    override fun getName(): String = "Docker Compose Validator"
    
    override fun canValidate(filePath: String): Boolean =
        filePath.endsWith("docker-compose.yml") || 
        filePath.endsWith("docker-compose.yaml") ||
        filePath.contains("compose")
    
    /**
     * Check for privileged mode usage
     */
    private fun checkPrivilegedMode(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val privileged = serviceConfig.get("privileged")
            if (privileged?.asBoolean() == true) {
                issues.add(
                    ValidationIssue(
                        ruleId = "DC001",
                        ruleName = "No Privileged Mode",
                        severity = IssueSeverity.ERROR,
                        message = "Service '$serviceName' runs in privileged mode",
                        filePath = filePath,
                        context = "Privileged mode gives the container full access to the host. This is a serious security risk.",
                        documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#privileged",
                        autoFix = AutoFix(
                            description = "Remove privileged mode from service '$serviceName'",
                            filePath = filePath,
                            lineNumber = null,
                            originalContent = "privileged: true",
                            fixedContent = "# privileged: true  # Removed for security",
                            isAutoApplicable = false
                        )
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Check for resource limits
     */
    private fun checkResourceLimits(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val deploy = serviceConfig.get("deploy")
            val resources = deploy?.get("resources")
            val limits = resources?.get("limits")
            
            if (limits == null) {
                issues.add(
                    ValidationIssue(
                        ruleId = "DC002",
                        ruleName = "Resource Limits Required",
                        severity = IssueSeverity.WARNING,
                        message = "Service '$serviceName' has no resource limits defined",
                        filePath = filePath,
                        context = "Define CPU and memory limits to prevent resource exhaustion.",
                        documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#resources",
                        autoFix = AutoFix(
                            description = "Add resource limits to service '$serviceName'",
                            filePath = filePath,
                            lineNumber = null,
                            originalContent = null,
                            fixedContent = """
                                |deploy:
                                |  resources:
                                |    limits:
                                |      cpus: '1'
                                |      memory: 512M
                                |    reservations:
                                |      cpus: '0.25'
                                |      memory: 256M
                            """.trimMargin(),
                            isAutoApplicable = false
                        )
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Check for insecure port bindings
     */
    private fun checkPortBinding(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val ports = serviceConfig.get("ports")
            if (ports != null && ports.isArray) {
                ports.forEach { port ->
                    val portStr = port.asText()
                    // Check for 0.0.0.0 binding
                    if (portStr.startsWith("0.0.0.0:") || (!portStr.contains(":") && portStr.contains("-"))) {
                        issues.add(
                            ValidationIssue(
                                ruleId = "DC003",
                                ruleName = "Secure Port Binding",
                                severity = IssueSeverity.WARNING,
                                message = "Service '$serviceName' binds to all interfaces (0.0.0.0)",
                                filePath = filePath,
                                context = "Binding to 0.0.0.0 exposes the service on all network interfaces. Use 127.0.0.1 for localhost-only access.",
                                documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#ports",
                                autoFix = AutoFix(
                                    description = "Bind to localhost only",
                                    filePath = filePath,
                                    lineNumber = null,
                                    originalContent = portStr,
                                    fixedContent = portStr.replace("0.0.0.0:", "127.0.0.1:"),
                                    isAutoApplicable = false
                                )
                            )
                        )
                    }
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check for custom networks
     */
    private fun checkNetworks(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val networks = yaml.get("networks")
        val services = yaml.get("services") ?: return emptyList()
        
        if (networks == null || networks.isEmpty) {
            // Check if there are multiple services
            if (services.size() > 1) {
                return listOf(
                    ValidationIssue(
                        ruleId = "DC004",
                        ruleName = "Custom Networks Recommended",
                        severity = IssueSeverity.INFO,
                        message = "Multiple services without custom networks use the default bridge",
                        filePath = filePath,
                        context = "Define custom networks for better isolation and security.",
                        documentationUrl = "https://docs.docker.com/compose/networking/",
                        autoFix = AutoFix(
                            description = "Add custom network definition",
                            filePath = filePath,
                            lineNumber = null,
                            originalContent = null,
                            fixedContent = """
                                |networks:
                                |  app-network:
                                |    driver: bridge
                            """.trimMargin(),
                            isAutoApplicable = false
                        )
                    )
                )
            }
        }
        
        return emptyList()
    }
    
    /**
     * Check for health checks
     */
    private fun checkHealthChecks(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val healthcheck = serviceConfig.get("healthcheck")
            
            if (healthcheck == null) {
                issues.add(
                    ValidationIssue(
                        ruleId = "DC005",
                        ruleName = "Health Check Recommended",
                        severity = IssueSeverity.INFO,
                        message = "Service '$serviceName' has no health check defined",
                        filePath = filePath,
                        context = "Health checks help orchestrators monitor service health.",
                        documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#healthcheck"
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Check for hardcoded secrets
     */
    private fun checkSecrets(filePath: String, yaml: JsonNode, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val secretPatterns = listOf(
            "password", "secret", "token", "api_key", "apikey",
            "aws_access_key", "aws_secret_key", "private_key"
        )
        
        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            secretPatterns.forEach { pattern ->
                if (line.lowercase().contains(pattern) && 
                    line.contains("=") && 
                    !line.trim().startsWith("#")) {
                    
                    // Check if it looks like a real secret (not a placeholder)
                    val afterEquals = line.substringAfter("=").trim()
                    if (afterEquals.isNotEmpty() && 
                        !afterEquals.startsWith("\${") &&
                        !afterEquals.contains("changeme", ignoreCase = true) &&
                        !afterEquals.contains("your_", ignoreCase = true)) {
                        
                        issues.add(
                            ValidationIssue(
                                ruleId = "DC006",
                                ruleName = "No Hardcoded Secrets",
                                severity = IssueSeverity.ERROR,
                                message = "Potential hardcoded secret found",
                                filePath = filePath,
                                lineNumber = index + 1,
                                context = "Use Docker secrets or environment variables from external files.",
                                documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#secrets"
                            )
                        )
                    }
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check volume security
     */
    private fun checkVolumeSecurity(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val volumes = serviceConfig.get("volumes")
            if (volumes != null && volumes.isArray) {
                volumes.forEach { volume ->
                    val volumeStr = volume.asText()
                    
                    // Check for root directory mounts
                    if (volumeStr.startsWith("/:/") || volumeStr.startsWith("/:")) {
                        issues.add(
                            ValidationIssue(
                                ruleId = "DC007",
                                ruleName = "Dangerous Volume Mount",
                                severity = IssueSeverity.ERROR,
                                message = "Service '$serviceName' mounts host root directory",
                                filePath = filePath,
                                context = "Mounting the root directory gives the container full access to the host filesystem."
                            )
                        )
                    }
                    
                    // Check for read-write mounts of sensitive directories
                    val sensitivePaths = listOf("/etc", "/var", "/usr", "/bin", "/sbin")
                    sensitivePaths.forEach { path ->
                        if (volumeStr.startsWith("$path:") && !volumeStr.endsWith(":ro")) {
                            issues.add(
                                ValidationIssue(
                                    ruleId = "DC008",
                                    ruleName = "Sensitive Directory Mount",
                                    severity = IssueSeverity.WARNING,
                                    message = "Service '$serviceName' has read-write mount of sensitive directory '$path'",
                                    filePath = filePath,
                                    context = "Consider using read-only mounts (:ro) for sensitive host directories.",
                                    autoFix = AutoFix(
                                        description = "Make volume read-only",
                                        filePath = filePath,
                                        lineNumber = null,
                                        originalContent = volumeStr,
                                        fixedContent = "$volumeStr:ro",
                                        isAutoApplicable = false
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check security options
     */
    private fun checkSecurityOptions(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val securityOpt = serviceConfig.get("security_opt")
            
            if (securityOpt != null && securityOpt.isArray) {
                securityOpt.forEach { opt ->
                    val optStr = opt.asText()
                    // Check for disabling security features
                    if (optStr.contains("apparmor=unconfined") || 
                        optStr.contains("seccomp=unconfined") ||
                        optStr.contains("label=disable")) {
                        issues.add(
                            ValidationIssue(
                                ruleId = "DC009",
                                ruleName = "Security Feature Disabled",
                                severity = IssueSeverity.ERROR,
                                message = "Service '$serviceName' disables security features: $optStr",
                                filePath = filePath,
                                context = "Disabling AppArmor, SELinux, or seccomp reduces container security."
                            )
                        )
                    }
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check restart policy
     */
    private fun checkRestartPolicy(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val restart = serviceConfig.get("restart")?.asText()
            
            if (restart == "always") {
                issues.add(
                    ValidationIssue(
                        ruleId = "DC010",
                        ruleName = "Restart Policy Review",
                        severity = IssueSeverity.INFO,
                        message = "Service '$serviceName' uses 'always' restart policy",
                        filePath = filePath,
                        context = "Consider 'unless-stopped' to prevent auto-restart after manual stops.",
                        documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#restart"
                    )
                )
            }
        }
        
        return issues
    }
    
    /**
     * Check logging configuration
     */
    private fun checkLogging(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val services = yaml.get("services") ?: return emptyList()
        
        services.fields().forEach { (serviceName, serviceConfig) ->
            val logging = serviceConfig.get("logging")
            
            if (logging == null) {
                issues.add(
                    ValidationIssue(
                        ruleId = "DC011",
                        ruleName = "Logging Configuration Recommended",
                        severity = IssueSeverity.INFO,
                        message = "Service '$serviceName' has no logging configuration",
                        filePath = filePath,
                        context = "Configure logging to prevent disk space issues from unbounded logs.",
                        documentationUrl = "https://docs.docker.com/compose/compose-file/compose-file-v3/#logging",
                        autoFix = AutoFix(
                            description = "Add logging configuration with rotation",
                            filePath = filePath,
                            lineNumber = null,
                            originalContent = null,
                            fixedContent = """
                                |logging:
                                |  driver: "json-file"
                                |  options:
                                |    max-size: "10m"
                                |    max-file: "3"
                            """.trimMargin(),
                            isAutoApplicable = false
                        )
                    )
                )
            }
        }
        
        return issues
    }
}
