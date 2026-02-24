package org.springforge.cicdassistant.validation.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.springforge.cicdassistant.validation.AutoFix
import org.springforge.cicdassistant.validation.IssueSeverity
import org.springforge.cicdassistant.validation.ValidationIssue
import org.springforge.cicdassistant.validation.ValidationResult
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Displays validation results with a file-scoped sidebar and
 * a filtered issue table + detail pane on the right.
 *
 * Layout:
 *   ┌─ summary bar ────────────────────────────────────────────┐
 *   ├─ FILES ──────────┬─ ISSUES (filtered) ── LINE ───────────┤
 *   │ 📋 All Files     │ table rows …                           │
 *   │ 🐳 Dockerfile    ├─ ISSUE DETAILS ─────────────────────── │
 *   │ ⚙️  build.yml    │ details text …                         │
 *   │ 🐋 compose.yml   │                                        │
 *   ├──────────────────┴────────────────────────────────────────┤
 *   │ [Apply Fixes]  [Export Report]  [Dismiss]                 │
 *   └──────────────────────────────────────────────────────────┘
 */
class ValidationResultsPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── Data ──────────────────────────────────────────────────────────────────

    private data class FileEntry(
        val key: String,          // ALL_FILES_KEY or absolute file path
        val displayName: String,
        val icon: String,
        val errorCount: Int,
        val warningCount: Int,
        val infoCount: Int
    )

    companion object {
        private const val ALL_FILES_KEY = "__ALL__"
    }

    private var currentResult: ValidationResult? = null
    private var selectedKey: String = ALL_FILES_KEY

    // ── UI components ─────────────────────────────────────────────────────────

    private val summaryLabel        = JLabel()
    private val fileListModel       = DefaultListModel<FileEntry>()
    private val fileList            = JList<FileEntry>(fileListModel)
    private val tableModel          = ValidationTableModel()
    private val table               = JBTable(tableModel)
    private val detailsArea         = JTextArea()
    private val issuesPanelTitle    = JLabel("ALL FILES")

    private val applyFixesButton    = JButton("Apply Fixes")
    private val exportButton        = JButton("Export Report")
    private val dismissButton       = JButton("Dismiss")

    init {
        setupUI()
        setupListeners()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI construction
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupUI() {
        // ── Summary bar ───────────────────────────────────────────────────────
        val summaryBar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(7, 10)
            )
            add(summaryLabel, BorderLayout.WEST)
        }
        add(summaryBar, BorderLayout.NORTH)

        // ── Left: file list ───────────────────────────────────────────────────
        fileList.apply {
            cellRenderer  = FileListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            border        = JBUI.Borders.empty()
            background    = UIUtil.getPanelBackground()
        }

        val fileListPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(190, 0)
            minimumSize   = Dimension(150, 0)
            border        = BorderFactory.createMatteBorder(0, 0, 0, 1, JBColor.border())
            add(sectionHeader("FILES"),                                                     BorderLayout.NORTH)
            add(JBScrollPane(fileList).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }

        // ── Right top: issues table ───────────────────────────────────────────
        setupTable()
        val tableScroll = JBScrollPane(table).apply { border = JBUI.Borders.empty() }

        val issuesHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(4, 10)
            )
            issuesPanelTitle.font      = JBUI.Fonts.label(10f).asBold()
            issuesPanelTitle.foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
            add(issuesPanelTitle, BorderLayout.WEST)
        }

        val tablePanel = JPanel(BorderLayout()).apply {
            add(issuesHeader, BorderLayout.NORTH)
            add(tableScroll,  BorderLayout.CENTER)
        }

        // ── Right bottom: issue details ───────────────────────────────────────
        detailsArea.apply {
            isEditable   = false
            lineWrap     = true
            wrapStyleWord = true
            font         = Font("Monospaced", Font.PLAIN, 11)
            border       = JBUI.Borders.empty(7)
            background   = UIUtil.getPanelBackground()
        }

        val detailsPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
            add(sectionHeader("ISSUE DETAILS"),                                              BorderLayout.NORTH)
            add(JBScrollPane(detailsArea).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        }

        // ── Right split: table / details ──────────────────────────────────────
        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, detailsPanel).apply {
            resizeWeight = 0.65
            border       = JBUI.Borders.empty()
        }

        // ── Main horizontal split: files | right ──────────────────────────────
        val mainSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileListPanel, rightSplit).apply {
            resizeWeight = 0.0
            border       = JBUI.Borders.empty()
        }
        add(mainSplit, BorderLayout.CENTER)

        // ── Buttons ───────────────────────────────────────────────────────────
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
            for (btn in listOf(applyFixesButton, exportButton, dismissButton)) {
                btn.font          = JBUI.Fonts.label(11f)
                btn.isFocusPainted = false
                btn.border        = JBUI.Borders.empty(5, 10)
            }
            add(applyFixesButton)
            add(exportButton)
            add(dismissButton)
        }
        add(buttonPanel, BorderLayout.SOUTH)

        applyFixesButton.isEnabled = false
        exportButton.isEnabled     = false
    }

    private fun sectionHeader(title: String) = JPanel(BorderLayout()).apply {
        isOpaque = false
        border   = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(4, 8)
        )
        add(JLabel(title).apply {
            font       = JBUI.Fonts.label(10f).asBold()
            foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
        }, BorderLayout.WEST)
    }

    private fun setupTable() {
        table.apply {
            setDefaultRenderer(Any::class.java, ValidationTableCellRenderer())
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode              = JTable.AUTO_RESIZE_ALL_COLUMNS
            rowHeight                   = 24
            tableHeader.reorderingAllowed = false

            columnModel.getColumn(0).apply { preferredWidth = 80;  maxWidth = 100 } // Severity
            columnModel.getColumn(1).apply { preferredWidth = 150 }                  // Rule
            columnModel.getColumn(2).apply { preferredWidth = 300 }                  // Message
            columnModel.getColumn(3).apply { preferredWidth = 50;  maxWidth = 70  } // Line
        }
    }

    private fun setupListeners() {
        fileList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val entry = fileList.selectedValue ?: return@addListSelectionListener
                selectedKey = entry.key
                refreshTable()
                issuesPanelTitle.text = when (entry.key) {
                    ALL_FILES_KEY -> "ALL FILES"
                    else          -> "${entry.icon} ${entry.displayName}"
                }
            }
        }

        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) displayIssueDetails(tableModel.getIssueAt(row))
            }
        }

        applyFixesButton.addActionListener {
            currentResult?.let { r ->
                val fixable = r.getFixableIssues()
                if (fixable.isNotEmpty()) showApplyFixesDialog(fixable)
                else Messages.showInfoMessage(project, "No auto-fixable issues found.", "No Fixes Available")
            }
        }

        exportButton.addActionListener  { currentResult?.let { exportReport(it) } }
        dismissButton.addActionListener { clearResults() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════════════════

    fun displayResults(result: ValidationResult) {
        currentResult = result

        // ── Populate file list ────────────────────────────────────────────────
        fileListModel.clear()
        fileListModel.addElement(FileEntry(
            key          = ALL_FILES_KEY,
            displayName  = "All Files",
            icon         = "📋",
            errorCount   = result.getErrorCount(),
            warningCount = result.getWarningCount(),
            infoCount    = result.getInfoCount()
        ))
        result.getIssuesByFile().entries
            .sortedBy { File(it.key).name }
            .forEach { (filePath, issues) ->
                fileListModel.addElement(FileEntry(
                    key          = filePath,
                    displayName  = File(filePath).name,
                    icon         = iconFor(filePath),
                    errorCount   = issues.count { it.severity == IssueSeverity.ERROR },
                    warningCount = issues.count { it.severity == IssueSeverity.WARNING },
                    infoCount    = issues.count { it.severity == IssueSeverity.INFO }
                ))
            }

        // ── Select "All Files" ────────────────────────────────────────────────
        selectedKey           = ALL_FILES_KEY
        issuesPanelTitle.text = "ALL FILES"
        fileList.selectedIndex = 0

        // ── Summary bar ───────────────────────────────────────────────────────
        summaryLabel.text = buildSummaryHtml(result)

        refreshTable()
        detailsArea.text       = ""
        applyFixesButton.isEnabled = result.getFixableIssues().isNotEmpty()
        exportButton.isEnabled     = true
    }

    fun clearResults() {
        currentResult = null
        tableModel.clearIssues()
        fileListModel.clear()
        summaryLabel.text      = ""
        issuesPanelTitle.text  = "ALL FILES"
        detailsArea.text       = ""
        applyFixesButton.isEnabled = false
        exportButton.isEnabled     = false
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Internals
    // ══════════════════════════════════════════════════════════════════════════

    private fun refreshTable() {
        val result = currentResult ?: return
        val issues = if (selectedKey == ALL_FILES_KEY) {
            result.issues
        } else {
            result.issues.filter { it.filePath == selectedKey }
        }
        tableModel.setIssues(issues)
        detailsArea.text = ""
        if (issues.isNotEmpty()) table.setRowSelectionInterval(0, 0)
    }

    private fun buildSummaryHtml(result: ValidationResult): String = buildString {
        append("<html>")
        append("<b>${result.filesValidated}</b> file${if (result.filesValidated != 1) "s" else ""}")
        if (result.getErrorCount() > 0)
            append("  &nbsp;·&nbsp;  <font color='#D73A49'><b>❌ ${result.getErrorCount()} error${if (result.getErrorCount() != 1) "s" else ""}</b></font>")
        if (result.getWarningCount() > 0)
            append("  &nbsp;·&nbsp;  <font color='#E36209'>⚠️ ${result.getWarningCount()} warning${if (result.getWarningCount() != 1) "s" else ""}</font>")
        if (result.getInfoCount() > 0)
            append("  &nbsp;·&nbsp;  <font color='#005CC5'>ℹ️ ${result.getInfoCount()} info</font>")
        if (result.issues.isEmpty())
            append("  &nbsp;·&nbsp;  <font color='#28A745'><b>✅ No issues</b></font>")
        append("  &nbsp;·&nbsp;  <font color='gray'>${result.durationMs}ms</font>")
        append("</html>")
    }

    private fun displayIssueDetails(issue: ValidationIssue) {
        detailsArea.text = buildString {
            append("Rule:     ${issue.ruleName}  (${issue.ruleId})\n")
            append("Severity: ${issue.getSeveritySymbol()} ${issue.severity}\n")
            append("File:     ${File(issue.filePath).name}")
            issue.lineNumber?.let { append("  —  Line $it") }
            append("\n\n")
            append("Message:\n${issue.message}\n")
            issue.context?.let        { append("\nContext:\n$it\n") }
            issue.documentationUrl?.let { append("\nDocs: $it\n") }
            issue.autoFix?.let { fix ->
                append("\n── Auto-Fix ──────────────────────────────\n")
                append("${fix.description}\n")
                if (fix.isAutoApplicable) append("✅ Can be applied automatically\n")
                else                       append("⚠️  Requires manual review\n")
                if (fix.fixedContent.isNotBlank()) {
                    append("\nSuggested fix:\n${fix.fixedContent}\n")
                }
            }
        }
        detailsArea.caretPosition = 0
    }

    private fun iconFor(filePath: String): String {
        val name = File(filePath).name.lowercase()
        return when {
            name == "dockerfile"                                    -> "🐳"
            name.contains("compose")                               -> "🐋"
            name.endsWith(".yml") || name.endsWith(".yaml")        -> "⚙️"
            else                                                   -> "📄"
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  File list renderer
    // ══════════════════════════════════════════════════════════════════════════

    private inner class FileListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int,
            isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val entry = value as? FileEntry ?: return this
            border = JBUI.Borders.empty(6, 8)

            val badgeParts = buildString {
                if (entry.errorCount   > 0) append("<font color='#D73A49'>❌${entry.errorCount}</font> ")
                if (entry.warningCount > 0) append("<font color='#E36209'>⚠️${entry.warningCount}</font> ")
                if (entry.infoCount    > 0) append("<font color='#005CC5'>ℹ️${entry.infoCount}</font>")
                if (isEmpty())             append("<font color='#28A745'>✅ clean</font>")
            }

            text = "<html><b>${entry.icon} ${entry.displayName}</b><br>" +
                   "<small>$badgeParts</small></html>"
            return this
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Table model
    // ══════════════════════════════════════════════════════════════════════════

    private class ValidationTableModel : AbstractTableModel() {
        private val issues  = mutableListOf<ValidationIssue>()
        private val columns = arrayOf("Severity", "Rule", "Message", "Line")

        fun setIssues(newIssues: List<ValidationIssue>) {
            issues.clear(); issues.addAll(newIssues); fireTableDataChanged()
        }
        fun clearIssues() { issues.clear(); fireTableDataChanged() }
        fun getIssueAt(row: Int): ValidationIssue = issues[row]

        override fun getRowCount()    = issues.size
        override fun getColumnCount() = columns.size
        override fun getColumnName(col: Int) = columns[col]
        override fun getValueAt(row: Int, col: Int): Any {
            val i = issues[row]
            return when (col) {
                0 -> i.severity
                1 -> i.ruleName
                2 -> i.message
                3 -> i.lineNumber?.toString() ?: "—"
                else -> ""
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Table cell renderer
    // ══════════════════════════════════════════════════════════════════════════

    private class ValidationTableCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (column == 0 && value is IssueSeverity) {
                text = when (value) {
                    IssueSeverity.ERROR   -> "❌ ERROR"
                    IssueSeverity.WARNING -> "⚠️  WARN"
                    IssueSeverity.INFO    -> "ℹ️  INFO"
                }
                if (!isSelected) foreground = when (value) {
                    IssueSeverity.ERROR   -> Color(0xD73A49)
                    IssueSeverity.WARNING -> Color(0xE36209)
                    IssueSeverity.INFO    -> Color(0x005CC5)
                }
            }
            return c
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Apply fixes
    // ══════════════════════════════════════════════════════════════════════════

    private fun showApplyFixesDialog(fixableIssues: List<ValidationIssue>) {
        val options   = fixableIssues.map { "${it.getSeveritySymbol()} ${it.ruleName} — ${File(it.filePath).name}" }
            .toTypedArray()
        val selected  = mutableListOf<Int>()
        val checkBoxes = options.mapIndexed { index, option ->
            JCheckBox(option, true).also { cb ->
                cb.addItemListener {
                    if (cb.isSelected) selected.add(index) else selected.remove(index)
                }
                selected.add(index)
            }
        }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Select issues to apply fixes for:"))
            add(Box.createVerticalStrut(10))
            checkBoxes.forEach { add(it) }
        }

        val result = JOptionPane.showConfirmDialog(
            this,
            JBScrollPane(panel).apply { preferredSize = Dimension(500, 300) },
            "Apply Auto-Fixes",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (result == JOptionPane.OK_OPTION && selected.isNotEmpty()) {
            applyFixes(selected.map { fixableIssues[it] })
        }
    }

    private fun applyFixes(issues: List<ValidationIssue>) {
        var successCount = 0
        var failCount    = 0
        val errors       = mutableListOf<String>()

        issues.forEach { issue ->
            try {
                val fix = issue.autoFix ?: return@forEach
                if (fix.isAutoApplicable) {
                    val file = File(fix.filePath)
                    if (file.exists()) {
                        val content = file.readText()
                        val updated = if (fix.originalContent != null)
                            content.replace(fix.originalContent, fix.fixedContent)
                        else
                            content + "\n${fix.fixedContent}\n"
                        file.writeText(updated)
                        successCount++
                    } else {
                        failCount++; errors.add("File not found: ${fix.filePath}")
                    }
                } else {
                    Messages.showInfoMessage(
                        project,
                        "Manual fix required:\n${fix.description}\n\n${fix.fixedContent}",
                        "Manual Fix — ${issue.ruleName}"
                    )
                }
            } catch (e: Exception) {
                failCount++; errors.add("${issue.ruleName}: ${e.message}")
            }
        }

        val msg = buildString {
            append("Applied $successCount fix(es) successfully")
            if (failCount > 0) {
                append("\nFailed: $failCount")
                if (errors.isNotEmpty()) { append("\n\n"); errors.forEach { append("- $it\n") } }
            }
        }
        Messages.showInfoMessage(project, msg, "Apply Fixes Result")
        currentResult?.let { displayResults(it) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Export report
    // ══════════════════════════════════════════════════════════════════════════

    private fun exportReport(result: ValidationResult) {
        val fc = JFileChooser().apply {
            dialogTitle  = "Export Validation Report"
            selectedFile = File("validation-report.txt")
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                fc.selectedFile.writeText(generateReport(result))
                Messages.showInfoMessage(
                    project,
                    "Report exported to:\n${fc.selectedFile.absolutePath}",
                    "Export Successful"
                )
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to export: ${e.message}", "Export Failed")
            }
        }
    }

    private fun generateReport(result: ValidationResult): String = buildString {
        val sep = "=".repeat(80)
        append("$sep\nSPRINGFORGE CI/CD VALIDATION REPORT\n$sep\n\n")
        append("Generated : ${result.timestamp}\n")
        append("Files     : ${result.filesValidated}\n")
        append("Duration  : ${result.durationMs}ms\n")
        append("Status    : ${if (result.isSuccess()) "PASSED" else "FAILED"}\n\n")
        append("Summary   : Errors=${result.getErrorCount()}  Warnings=${result.getWarningCount()}  Info=${result.getInfoCount()}\n\n")

        if (result.issues.isNotEmpty()) {
            append("${"—".repeat(80)}\nISSUES BY FILE\n${"—".repeat(80)}\n\n")
            result.getIssuesByFile().forEach { (filePath, issues) ->
                append("▸ ${File(filePath).name}  ($filePath)\n")
                issues.forEach { issue ->
                    append("  ${issue.getSeveritySymbol()} [${issue.ruleId}] ${issue.ruleName}\n")
                    issue.lineNumber?.let { append("     Line    : $it\n") }
                    append("     Message : ${issue.message}\n")
                    issue.context?.let         { append("     Context : $it\n") }
                    issue.autoFix?.let { fix -> append("     Fix     : ${fix.description}\n") }
                    append("\n")
                }
            }
        }
        append("$sep\nEND OF REPORT\n$sep")
    }
}
