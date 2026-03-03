package org.springforge.cicdassistant.audit

import java.time.Instant

data class AuditEvent(
    val id: Long = 0,
    val eventType: AuditEventType,
    val projectPath: String? = null,
    val sourceType: String? = null,       // "LOCAL" | "GITHUB" | null
    val artifacts: String? = null,        // comma-separated: "dockerfile,compose,workflow"
    val filesCount: Int = 0,
    val success: Boolean = true,
    val errorMsg: String? = null,
    val issuesError: Int = 0,      // VALIDATION only
    val issuesWarn: Int = 0,
    val issuesInfo: Int = 0,
    val insightCount: Int = 0,     // EXPLAINABILITY only
    val durationMs: Long = 0,
    val createdAt: Instant = Instant.now()
)
