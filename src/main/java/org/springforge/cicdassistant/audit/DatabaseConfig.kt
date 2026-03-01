package org.springforge.cicdassistant.audit

import org.springforge.cicdassistant.config.EnvironmentConfig

/**
 * Reads PostgreSQL connection settings from the same .env file used for AWS/GitHub.
 *
 * Required keys (all must be non-blank for isConfigured() to return true):
 *   POSTGRES_HOST     — e.g. localhost
 *   POSTGRES_PORT     — e.g. 5432
 *   POSTGRES_DB       — e.g. springforge_audit
 *   POSTGRES_USER     — e.g. springforge
 *   POSTGRES_PASSWORD — e.g. changeme
 *
 * If any key is missing the audit service silently skips logging.
 */
class DatabaseConfig {

    private val host     = EnvironmentConfig.Postgres.host
    private val port     = EnvironmentConfig.Postgres.port
    private val db       = EnvironmentConfig.Postgres.database
    private val user     = EnvironmentConfig.Postgres.user
    private val password = EnvironmentConfig.Postgres.password

    val jdbcUrl: String get() = "jdbc:postgresql://$host:$port/$db"
    val jdbcUser: String get() = user ?: ""
    val jdbcPassword: String get() = password ?: ""

    fun isConfigured(): Boolean =
        !host.isNullOrBlank() && !db.isNullOrBlank() &&
        !user.isNullOrBlank() && !password.isNullOrBlank()
}
