package org.springforge.runtimeanalysis.ui

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Utility for displaying SpringForge analysis results
 * Handles all parsing and error cases automatically
 */
object AnalysisDialogHelper {

    private val log = Logger.getInstance(AnalysisDialogHelper::class.java)

    /**
     * Show analysis result dialog from any response format
     *
     * @param project The IntelliJ project
     * @param response Can be:
     *   - JSON string: "{\"answer\": \"...\", \"retrieved_docs\": [...]}"
     *   - Response object with 'answer' and 'retrieved_docs' fields
     *   - Any object that can be converted to JSON
     */
    fun showAnalysisResult(project: Project, response: Any) {
        try {
            // Convert to JSON string if needed
            val responseJson = when (response) {
                is String -> response
                else -> Gson().toJson(response)
            }

            log.info("Showing analysis dialog")

            // Parse the response
            val parsed = ResponseParser.parse(responseJson)

            // Create and show the dialog
            val dialog = AnalysisResultDialog(
                    project = project,
                    errorSummary = parsed.errorSummary,
                    rootCause = parsed.rootCause,
                    suggestedFix = parsed.suggestedFix,
                    codeSnippet = parsed.codeSnippet,
                    references = parsed.references,
                    notes = parsed.notes
            )

            dialog.show()

        } catch (ex: Exception) {
            log.error("Failed to show analysis dialog", ex)
            handleError(project, response, ex)
        }
    }

    /**
     * Fallback error handling
     */
    private fun handleError(project: Project, response: Any, error: Exception) {
        val errorMessage = buildString {
            appendLine("Failed to parse SpringForge response")
            appendLine()
            appendLine("Error: ${error.message}")
            appendLine()
            appendLine("Response preview:")
            appendLine(response.toString().take(500))
            if (response.toString().length > 500) {
                appendLine("... (truncated)")
            }
        }

        Messages.showErrorDialog(
                project,
                errorMessage,
                "SpringForge Parse Error"
        )
    }

    /**
     * Alternative: Show simple text dialog (fallback for old code)
     */
    fun showSimpleDialog(project: Project, text: String) {
        Messages.showMessageDialog(
                project,
                text,
                "SpringForge Analysis",
                Messages.getInformationIcon()
        )
    }
}
