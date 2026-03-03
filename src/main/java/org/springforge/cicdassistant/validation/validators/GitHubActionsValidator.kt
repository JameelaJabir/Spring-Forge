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
 * Validator for GitHub Actions workflow files with security best practices
 */
class GitHubActionsValidator : Validator {
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    
    override fun validate(filePath: String, content: String): ValidationResult {
        val startTime = System.currentTimeMillis()
        val allIssues = mutableListOf<ValidationIssue>()
        
        try {
            val yaml = yamlMapper.readTree(content)
            
            // Run all validation checks
            allIssues.addAll(checkPinnedVersions(filePath, yaml, content))
            allIssues.addAll(checkHardcodedSecrets(filePath, content))
            allIssues.addAll(checkPermissions(filePath, yaml))
            allIssues.addAll(checkSecurityScanning(filePath, yaml))
            allIssues.addAll(checkCheckoutDepth(filePath, yaml))
            allIssues.addAll(checkThirdPartyActions(filePath, yaml, content))
            allIssues.addAll(checkPullRequestTriggers(filePath, yaml))
            allIssues.addAll(checkArtifactSecurity(filePath, yaml))
            allIssues.addAll(checkEnvironmentSecrets(filePath, yaml))
            
        } catch (e: Exception) {
            allIssues.add(
                ValidationIssue(
                    ruleId = "GH_PARSE_ERROR",
                    ruleName = "YAML Parse Error",
                    severity = IssueSeverity.ERROR,
                    message = "Failed to parse GitHub Actions workflow: ${e.message}",
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
    
    override fun getName(): String = "GitHub Actions Validator"
    
    override fun canValidate(filePath: String): Boolean =
        filePath.contains(".github/workflows/") && 
        (filePath.endsWith(".yml") || filePath.endsWith(".yaml"))
    
    /**
     * Check if actions are pinned to specific versions (not branches)
     */
    private fun checkPinnedVersions(filePath: String, yaml: JsonNode, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val lines = content.lines()
        
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("uses:")) {
                val actionRef = trimmedLine.substringAfter("uses:").trim()
                
                // Check if using branch reference (main, master, develop)
                if (actionRef.contains("@main") || 
                    actionRef.contains("@master") || 
                    actionRef.contains("@develop")) {
                    
                    issues.add(
                        ValidationIssue(
                            ruleId = "GH001",
                            ruleName = "Pin Action to SHA or Tag",
                            severity = IssueSeverity.WARNING,
                            message = "Action '$actionRef' is pinned to a branch, not a SHA or tag",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Branch references can change unexpectedly. Pin to a specific SHA or semantic version tag.",
                            documentationUrl = "https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions#using-third-party-actions",
                            autoFix = AutoFix(
                                description = "Pin to a specific version tag",
                                filePath = filePath,
                                lineNumber = index + 1,
                                originalContent = actionRef,
                                fixedContent = "# Replace with: ${actionRef.substringBefore("@")}@v1.2.3 or @<commit-sha>",
                                isAutoApplicable = false
                            )
                        )
                    )
                }
                
                // Check for actions without any version
                if (!actionRef.contains("@") && !actionRef.startsWith(".")) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "GH002",
                            ruleName = "Action Version Required",
                            severity = IssueSeverity.ERROR,
                            message = "Action '$actionRef' has no version specified",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Always specify a version for third-party actions."
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check for hardcoded secrets
     */
    private fun checkHardcodedSecrets(filePath: String, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val secretPatterns = mapOf(
            "ghp_" to "GitHub Personal Access Token",
            "gho_" to "GitHub OAuth Token",
            "ghu_" to "GitHub User Token",
            "ghs_" to "GitHub Server Token",
            "ghr_" to "GitHub Refresh Token",
            "AKIA" to "AWS Access Key",
            "-----BEGIN" to "Private Key",
            "password:" to "Password",
            "token:" to "Token"
        )
        
        val lines = content.lines()
        lines.forEachIndexed { index, line ->
            secretPatterns.forEach { (pattern, name) ->
                if (line.contains(pattern, ignoreCase = true) && 
                    !line.trim().startsWith("#") &&
                    !line.contains("\${{")) {
                    
                    issues.add(
                        ValidationIssue(
                            ruleId = "GH003",
                            ruleName = "No Hardcoded Secrets",
                            severity = IssueSeverity.ERROR,
                            message = "Potential $name found in workflow",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Use GitHub secrets: \${{ secrets.SECRET_NAME }}",
                            documentationUrl = "https://docs.github.com/en/actions/security-guides/encrypted-secrets"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check for explicit permissions
     */
    private fun checkPermissions(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val permissions = yaml.get("permissions")
        
        if (permissions == null) {
            return listOf(
                ValidationIssue(
                    ruleId = "GH004",
                    ruleName = "Explicit Permissions Required",
                    severity = IssueSeverity.WARNING,
                    message = "Workflow uses default permissions instead of explicit ones",
                    filePath = filePath,
                    context = "Define explicit permissions to follow principle of least privilege.",
                    documentationUrl = "https://docs.github.com/en/actions/security-guides/automatic-token-authentication#permissions-for-the-github_token",
                    autoFix = AutoFix(
                        description = "Add explicit permissions block",
                        filePath = filePath,
                        lineNumber = null,
                        originalContent = null,
                        fixedContent = """
                            |permissions:
                            |  contents: read
                            |  pull-requests: read
                        """.trimMargin(),
                        isAutoApplicable = false
                    )
                )
            )
        }
        
        // Check if permissions are too broad
        val issues = mutableListOf<ValidationIssue>()
        if (permissions.isTextual && permissions.asText() == "write-all") {
            issues.add(
                ValidationIssue(
                    ruleId = "GH005",
                    ruleName = "Overly Broad Permissions",
                    severity = IssueSeverity.ERROR,
                    message = "Workflow has 'write-all' permissions",
                    filePath = filePath,
                    context = "Limit permissions to only what's needed for the workflow."
                )
            )
        }
        
        return issues
    }
    
    /**
     * Check for security scanning integration
     */
    private fun checkSecurityScanning(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val jobs = yaml.get("jobs") ?: return emptyList()
        
        var hasSecurityScan = false
        jobs.fields().forEach { (_, jobConfig) ->
            val steps = jobConfig.get("steps")
            if (steps != null && steps.isArray) {
                steps.forEach { step ->
                    val uses = step.get("uses")?.asText() ?: ""
                    if (uses.contains("trivy") || 
                        uses.contains("snyk") ||
                        uses.contains("codeql") ||
                        uses.contains("dependabot") ||
                        uses.contains("security-scan")) {
                        hasSecurityScan = true
                    }
                }
            }
        }
        
        if (!hasSecurityScan && filePath.contains("ci.yml")) {
            issues.add(
                ValidationIssue(
                    ruleId = "GH006",
                    ruleName = "Security Scanning Recommended",
                    severity = IssueSeverity.INFO,
                    message = "CI workflow has no security scanning step",
                    filePath = filePath,
                    context = "Add Trivy, Snyk, or CodeQL for vulnerability scanning.",
                    documentationUrl = "https://docs.github.com/en/code-security"
                )
            )
        }
        
        return issues
    }
    
    /**
     * Check checkout action depth
     */
    private fun checkCheckoutDepth(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val jobs = yaml.get("jobs") ?: return emptyList()
        
        jobs.fields().forEach { (jobName, jobConfig) ->
            val steps = jobConfig.get("steps")
            if (steps != null && steps.isArray) {
                steps.forEachIndexed { stepIndex, step ->
                    val uses = step.get("uses")?.asText() ?: ""
                    if (uses.contains("actions/checkout")) {
                        val with = step.get("with")
                        val fetchDepth = with?.get("fetch-depth")?.asInt()
                        
                        if (fetchDepth == null) {
                            issues.add(
                                ValidationIssue(
                                    ruleId = "GH007",
                                    ruleName = "Checkout Depth Configuration",
                                    severity = IssueSeverity.INFO,
                                    message = "Checkout in job '$jobName' uses default fetch depth",
                                    filePath = filePath,
                                    context = "Consider setting fetch-depth: 1 for faster checkouts if full history isn't needed.",
                                    autoFix = AutoFix(
                                        description = "Set shallow clone fetch depth",
                                        filePath = filePath,
                                        lineNumber = null,
                                        originalContent = null,
                                        fixedContent = """
                                            |with:
                                            |  fetch-depth: 1
                                        """.trimMargin(),
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
     * Check third-party actions for security
     */
    private fun checkThirdPartyActions(filePath: String, yaml: JsonNode, content: String): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val lines = content.lines()
        
        // List of verified publishers
        val verifiedPublishers = setOf(
            "actions", "github", "docker", "aws-actions", "azure",
            "google-github-actions", "hashicorp", "codecov"
        )
        
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("uses:")) {
                val actionRef = trimmedLine.substringAfter("uses:").trim()
                val actionOwner = actionRef.substringBefore("/")
                
                if (!actionOwner.startsWith(".") && 
                    actionOwner !in verifiedPublishers &&
                    !actionRef.startsWith("docker://")) {
                    
                    issues.add(
                        ValidationIssue(
                            ruleId = "GH008",
                            ruleName = "Third-Party Action Review",
                            severity = IssueSeverity.INFO,
                            message = "Using third-party action from '$actionOwner'",
                            filePath = filePath,
                            lineNumber = index + 1,
                            context = "Review third-party actions for security. Consider pinning to SHA.",
                            documentationUrl = "https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions"
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check pull request triggers for security
     */
    private fun checkPullRequestTriggers(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val on = yaml.get("on") ?: return emptyList()
        
        val pullRequest = on.get("pull_request") ?: on.get("pull_request_target")
        if (pullRequest != null) {
            // Check if using pull_request_target
            if (on.has("pull_request_target")) {
                issues.add(
                    ValidationIssue(
                        ruleId = "GH009",
                        ruleName = "Pull Request Target Security",
                        severity = IssueSeverity.WARNING,
                        message = "Workflow uses pull_request_target trigger",
                        filePath = filePath,
                        context = "pull_request_target runs in the context of the base branch and has access to secrets. Be careful with untrusted code.",
                        documentationUrl = "https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#pull_request_target"
                    )
                )
            }
            
            // Check for dangerous permissions with PR triggers
            val permissions = yaml.get("permissions")
            if (permissions != null) {
                val contentsWrite = permissions.get("contents")?.asText() == "write"
                val prWrite = permissions.get("pull-requests")?.asText() == "write"
                
                if (contentsWrite || prWrite) {
                    issues.add(
                        ValidationIssue(
                            ruleId = "GH010",
                            ruleName = "PR Write Permissions",
                            severity = IssueSeverity.WARNING,
                            message = "Workflow has write permissions with PR trigger",
                            filePath = filePath,
                            context = "Minimize write permissions on PR-triggered workflows to prevent malicious PRs from modifying repository."
                        )
                    )
                }
            }
        }
        
        return issues
    }
    
    /**
     * Check artifact upload security
     */
    private fun checkArtifactSecurity(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val jobs = yaml.get("jobs") ?: return emptyList()
        
        jobs.fields().forEach { (jobName, jobConfig) ->
            val steps = jobConfig.get("steps")
            if (steps != null && steps.isArray) {
                steps.forEach { step ->
                    val uses = step.get("uses")?.asText() ?: ""
                    if (uses.contains("actions/upload-artifact")) {
                        val with = step.get("with")
                        val path = with?.get("path")?.asText() ?: ""
                        
                        // Check if uploading sensitive directories
                        val sensitivePaths = listOf(".git", ".env", "credentials", "secrets", "keystore")
                        if (sensitivePaths.any { path.contains(it) }) {
                            issues.add(
                                ValidationIssue(
                                    ruleId = "GH011",
                                    ruleName = "Sensitive Artifact Upload",
                                    severity = IssueSeverity.ERROR,
                                    message = "Job '$jobName' may upload sensitive files as artifacts",
                                    filePath = filePath,
                                    context = "Be careful not to upload secrets or sensitive files as artifacts."
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
     * Check environment-specific secrets
     */
    private fun checkEnvironmentSecrets(filePath: String, yaml: JsonNode): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val jobs = yaml.get("jobs") ?: return emptyList()
        
        var hasProductionJobs = false
        jobs.fields().forEach { (jobName, jobConfig) ->
            val environment = jobConfig.get("environment")?.asText()
            if (environment != null && 
                (environment.contains("prod", ignoreCase = true) || 
                 environment.contains("production", ignoreCase = true))) {
                hasProductionJobs = true
            }
        }
        
        if (hasProductionJobs) {
            // Check if there's approval requirement
            val environments = yaml.get("environments")
            if (environments == null) {
                issues.add(
                    ValidationIssue(
                        ruleId = "GH012",
                        ruleName = "Production Environment Protection",
                        severity = IssueSeverity.WARNING,
                        message = "Workflow has production deployments without environment protection",
                        filePath = filePath,
                        context = "Configure environment protection rules for production deployments.",
                        documentationUrl = "https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment"
                    )
                )
            }
        }
        
        return issues
    }
}
