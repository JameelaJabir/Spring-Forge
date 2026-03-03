package org.springforge.toolwindow.panels

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.springforge.codegeneration.actions.CreateNewProjectAction
import org.springforge.codegeneration.actions.ExistingProjectAction
import org.springforge.codegeneration.actions.GenerateCodeAction
import org.springforge.codegeneration.actions.GeneratePromptAction
import org.springforge.codegeneration.parser.InputModel
import org.springforge.codegeneration.parser.YamlParser
import org.springforge.codegeneration.parser.YamlWriter
import org.springforge.codegeneration.ui.EntityDesignerDialog
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*

/**
 * Code Generation Panel for SpringForge Tool Window
 */
class CodeGenerationPanel(private val project: Project) : JPanel() {

    init {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        setupUI()
    }

    private fun setupUI() {
        val contentPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0

        // Title
        val titleLabel = JBLabel("Code Generation Tools")
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)
        contentPanel.add(titleLabel, gbc)

        gbc.gridy++
        contentPanel.add(createSeparator(), gbc)

        // Description
        gbc.gridy++
        val descLabel = JBLabel(
            "<html>Generate Spring Boot projects with<br>" +
            "architecture-aware scaffolding and<br>" +
            "intelligent code templates.</html>"
        )
        descLabel.foreground = JBColor.GRAY
        contentPanel.add(descLabel, gbc)

        gbc.gridy++
        contentPanel.add(Box.createVerticalStrut(15), gbc)

        // ★ GENERATE CODE — primary action with Entity Designer UI
        gbc.gridy++
        val generateCodeButton = createActionButton(
            "\uD83D\uDE80 Generate Code (Entity Designer)",
            "Design entities visually, save to input.yml, and generate Spring Boot code with AI",
            "icons/generate.png",
            primary = true
        ) {
            openEntityDesignerAndGenerate()
        }
        contentPanel.add(generateCodeButton, gbc)

        gbc.gridy++
        contentPanel.add(Box.createVerticalStrut(8), gbc)

        // Create New Project button
        gbc.gridy++
        val createProjectButton = createActionButton(
            "Create New Spring Boot Project",
            "Create and initialize a new Spring Boot project with architecture template",
            "icons/new-project.png"
        ) {
            val action = CreateNewProjectAction()
            val event = createActionEvent()
            action.actionPerformed(event)
        }
        contentPanel.add(createProjectButton, gbc)

        // Analyze Existing Project button
        gbc.gridy++
        val analyzeProjectButton = createActionButton(
            "Analyze Existing Project",
            "Detect architecture pattern using ML and generate scaffolding",
            "icons/analyze.png"
        ) {
            val action = ExistingProjectAction()
            val event = createActionEvent()
            action.actionPerformed(event)
        }
        contentPanel.add(analyzeProjectButton, gbc)

        // Generate Prompt button
        gbc.gridy++
        val generatePromptButton = createActionButton(
            "Generate LLM Prompt",
            "Parse input.yml, analyze project, and build LLM prompt for code generation",
            "icons/prompt.png"
        ) {
            val action = GeneratePromptAction()
            val event = createActionEvent()
            action.actionPerformed(event)
        }
        contentPanel.add(generatePromptButton, gbc)

        // Add filler to push content to top
        gbc.gridy++
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        contentPanel.add(Box.createVerticalGlue(), gbc)

        // Info panel at bottom
        gbc.gridy++
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        contentPanel.add(createInfoPanel(), gbc)

        // Wrap in scroll pane
        val scrollPane = JBScrollPane(contentPanel)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createSeparator(): JSeparator {
        return JSeparator()
    }

    private fun createActionButton(
        title: String,
        description: String,
        iconPath: String?,
        primary: Boolean = false,
        action: () -> Unit
    ): JPanel {
        val panel = JPanel(BorderLayout(10, 5))

        val borderColor = if (primary)
            JBColor(java.awt.Color(76, 175, 80), java.awt.Color(76, 175, 80))
        else
            JBColor.border()

        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, if (primary) 2 else 1),
            BorderFactory.createEmptyBorder(if (primary) 12 else 10, 10, if (primary) 12 else 10, 10)
        )

        // Button content
        val contentPanel = JPanel(BorderLayout(5, 2))

        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD, if (primary) 13f else 12f)

        val descLabel = JBLabel("<html><small>$description</small></html>")
        descLabel.foreground = JBColor.GRAY

        contentPanel.add(titleLabel, BorderLayout.NORTH)
        contentPanel.add(descLabel, BorderLayout.CENTER)

        // Action button
        val button = JButton(if (primary) "Open Designer" else "Run")
        button.addActionListener { action() }

        panel.add(contentPanel, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)

        // Make panel clickable
        panel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        panel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                action()
            }

            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                panel.background = JBColor.background().brighter()
            }

            override fun mouseExited(e: java.awt.event.MouseEvent) {
                panel.background = null
            }
        })

        return panel
    }

    // ─── Entity Designer → save YAML → trigger code generation ─────

    private fun openEntityDesignerAndGenerate() {
        val baseDir = project.basePath ?: return

        // Try to load existing input.yml so the dialog pre-populates
        val yamlFile = File(baseDir, "input.yml")
        var existingModel: InputModel? = null
        if (yamlFile.exists()) {
            try {
                val result = YamlParser.parse(yamlFile.readText())
                if (result.isValid) existingModel = result.data
            } catch (_: Exception) { /* ignore, start fresh */ }
        }

        val dialog = EntityDesignerDialog(project, existingModel)
        if (!dialog.showAndGet()) return  // user cancelled

        val model = dialog.toInputModel()

        // Save to input.yml for future use / manual review
        try {
            val yaml = YamlWriter.write(model)
            yamlFile.writeText(yaml)
            Notifications.Bus.notify(
                Notification("SpringForge", "Entity Designer",
                    "Saved ${model.entities.size} entities to input.yml",
                    NotificationType.INFORMATION),
                project
            )
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("SpringForge", "Entity Designer",
                    "Failed to save input.yml: ${ex.message}",
                    NotificationType.ERROR),
                project
            )
            return
        }

        // Trigger code generation (existing action reads input.yml)
        val action = GenerateCodeAction()
        val event = createActionEvent()
        action.actionPerformed(event)
    }

    private fun createInfoPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(10, 5, 5, 5)
        )

        val infoLabel = JBLabel(
            "<html><small>" +
            "<b>Architecture Patterns Supported:</b><br>" +
            "• Layered Architecture<br>" +
            "• Clean Architecture<br>" +
            "• MVC<br>" +
            "</small></html>"
        )
        infoLabel.foreground = JBColor.GRAY

        panel.add(infoLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createActionEvent(): com.intellij.openapi.actionSystem.AnActionEvent {
        val dataContext = com.intellij.openapi.actionSystem.DataContext { dataId ->
            when (dataId) {
                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        return com.intellij.openapi.actionSystem.AnActionEvent(
            null,
            dataContext,
            com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
            com.intellij.openapi.actionSystem.Presentation(),
            com.intellij.openapi.actionSystem.ActionManager.getInstance(),
            0
        )
    }
}
