package org.springforge.codegeneration.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Holds the result of the most recent code generation run
 * so that the sidebar panel can display it.
 */
data class GenerationResult(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val totalFromLLM: Int = 0,
    val written: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val errors: List<Pair<String, String>> = emptyList(),
    val addedDependencies: List<String> = emptyList(),
    val depError: String? = null,
    val buildFile: String = ""
) {
    fun formattedTimestamp(): String =
        timestamp.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss"))
}

@Service(Service.Level.PROJECT)
class GenerationResultService {

    private var latestResult: GenerationResult? = null
    private val listeners = mutableListOf<() -> Unit>()

    fun getLatestResult(): GenerationResult? = latestResult

    fun publish(result: GenerationResult) {
        latestResult = result
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    companion object {
        fun getInstance(project: Project): GenerationResultService =
            project.getService(GenerationResultService::class.java)
    }
}
