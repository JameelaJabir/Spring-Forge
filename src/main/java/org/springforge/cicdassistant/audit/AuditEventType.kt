package org.springforge.cicdassistant.audit

enum class AuditEventType(val label: String) {
    GENERATION("Generation"),
    VALIDATION("Validation"),
    EXPLAINABILITY("Explainability")
}
