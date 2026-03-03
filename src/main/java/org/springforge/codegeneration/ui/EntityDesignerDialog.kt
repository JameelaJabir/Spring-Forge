package org.springforge.codegeneration.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextField
import org.springforge.codegeneration.parser.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

// ═══════════════════════════════════════════════════════════════════
//  DATA MODEL (lives inside the UI — bridges to InputModel)
// ═══════════════════════════════════════════════════════════════════

data class UIEntityField(
    var name: String = "",
    var type: String = "String",
    var primaryKey: Boolean = false,
    var unique: Boolean = false,
    var nullable: Boolean = true,
    var annotations: MutableList<String> = mutableListOf()
)

data class UIRelationship(
    var fromEntity: String = "",
    var toEntity: String = "",
    var type: String = "OneToMany",
    var mappedBy: String = ""
)

data class UIEntity(
    var name: String = "",
    var tableName: String = "",
    var fields: MutableList<UIEntityField> = mutableListOf(),
    var relationships: MutableList<UIRelationship> = mutableListOf()
)

// ═══════════════════════════════════════════════════════════════════
//  MAIN DIALOG
// ═══════════════════════════════════════════════════════════════════

class EntityDesignerDialog(
    private val project: Project,
    private var existingModel: InputModel? = null
) : DialogWrapper(project, true) {

    private val entities = mutableListOf<UIEntity>()
    private val entityTabs = JBTabbedPane()
    private val entityPanels = mutableListOf<EntityPanel>()

    // Global relationship panel
    private val globalRelationships = mutableListOf<UIRelationship>()
    private val relationshipListPanel = JPanel()

    init {
        title = "SpringForge — Entity Designer"
        setSize(820, 680)

        // Pre-populate from existing model if available
        if (existingModel != null) {
            loadFromModel(existingModel!!)
        } else {
            // Start with one empty entity
            addEntity(UIEntity(name = "User", fields = mutableListOf(
                UIEntityField("id", "Long", primaryKey = true),
                UIEntityField("name", "String"),
                UIEntityField("email", "String", unique = true)
            )))
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 8))
        root.preferredSize = Dimension(800, 620)

        // ── TOP: Entity management bar ──
        val topBar = JPanel(BorderLayout(5, 0))
        topBar.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)

        val addEntityBtn = JButton("+ Add Entity")
        addEntityBtn.addActionListener { addNewEmptyEntity() }

        val removeEntityBtn = JButton("- Remove Current")
        removeEntityBtn.addActionListener { removeCurrentEntity() }

        val btnPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        btnPanel.add(addEntityBtn)
        btnPanel.add(removeEntityBtn)

        val headerLabel = JBLabel("Define your entities, fields, and relationships:")
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 13f)

        topBar.add(headerLabel, BorderLayout.NORTH)
        topBar.add(btnPanel, BorderLayout.SOUTH)

        root.add(topBar, BorderLayout.NORTH)

        // ── CENTER: Tabbed entity editors + relationships ──
        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        mainSplit.resizeWeight = 0.72
        mainSplit.dividerSize = 6

        // Entity tabs (top)
        entityTabs.tabPlacement = SwingConstants.TOP
        rebuildTabs()
        mainSplit.topComponent = entityTabs

        // Relationships (bottom)
        val relPanel = createRelationshipPanel()
        mainSplit.bottomComponent = relPanel

        root.add(mainSplit, BorderLayout.CENTER)

        return root
    }

    // ─── Entity Tab Management ───────────────────────────────────────

    private fun addNewEmptyEntity() {
        val idx = entities.size + 1
        val entity = UIEntity(name = "Entity$idx", fields = mutableListOf(
            UIEntityField("id", "Long", primaryKey = true)
        ))
        addEntity(entity)
        rebuildTabs()
        entityTabs.selectedIndex = entities.size - 1
    }

    private fun addEntity(entity: UIEntity) {
        entities.add(entity)
        val panel = EntityPanel(entity)
        entityPanels.add(panel)
    }

    private fun removeCurrentEntity() {
        val idx = entityTabs.selectedIndex
        if (idx < 0 || entities.size <= 1) return // keep at least 1

        entities.removeAt(idx)
        entityPanels.removeAt(idx)
        rebuildTabs()

        if (entities.isNotEmpty()) {
            entityTabs.selectedIndex = (idx - 1).coerceAtLeast(0)
        }
    }

    private fun rebuildTabs() {
        entityTabs.removeAll()
        for (i in entities.indices) {
            entityPanels[i].syncFromUI() // save in-progress edits
            entityPanels[i] = EntityPanel(entities[i]) // rebuild fresh
            entityTabs.addTab(entities[i].name.ifBlank { "Entity ${i + 1}" }, entityPanels[i])
        }
    }

    // ─── Relationship Panel ──────────────────────────────────────────

    private fun createRelationshipPanel(): JPanel {
        val wrapper = JPanel(BorderLayout(5, 5))
        wrapper.border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Entity Relationships"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )

        val addRelBtn = JButton("+ Add Relationship")
        addRelBtn.addActionListener { addRelationshipRow(); refreshRelationshipList() }
        wrapper.add(addRelBtn, BorderLayout.NORTH)

        relationshipListPanel.layout = BoxLayout(relationshipListPanel, BoxLayout.Y_AXIS)
        refreshRelationshipList()

        wrapper.add(JBScrollPane(relationshipListPanel), BorderLayout.CENTER)
        return wrapper
    }

    private fun addRelationshipRow() {
        globalRelationships.add(UIRelationship())
    }

    private fun refreshRelationshipList() {
        relationshipListPanel.removeAll()

        for (i in globalRelationships.indices) {
            val rel = globalRelationships[i]
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))

            val fromField = JBTextField(rel.fromEntity, 10)
            val toField = JBTextField(rel.toEntity, 10)
            val typeCombo = ComboBox(arrayOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany"))
            typeCombo.selectedItem = rel.type
            val mappedByField = JBTextField(rel.mappedBy, 10)

            val removeBtn = JButton("\u2716")
            removeBtn.toolTipText = "Remove this relationship"
            removeBtn.markerForIndex = i

            row.add(JLabel("From:"))
            row.add(fromField)
            row.add(JLabel("To:"))
            row.add(toField)
            row.add(JLabel("Type:"))
            row.add(typeCombo)
            row.add(JLabel("MappedBy:"))
            row.add(mappedByField)
            row.add(removeBtn)

            // Sync on change
            val syncAction = {
                rel.fromEntity = fromField.text.trim()
                rel.toEntity = toField.text.trim()
                rel.type = typeCombo.selectedItem?.toString() ?: "OneToMany"
                rel.mappedBy = mappedByField.text.trim()
            }

            fromField.document.addDocumentListener(SimpleDocListener { syncAction() })
            toField.document.addDocumentListener(SimpleDocListener { syncAction() })
            mappedByField.document.addDocumentListener(SimpleDocListener { syncAction() })
            typeCombo.addActionListener { syncAction() }

            val capturedIndex = i
            removeBtn.addActionListener {
                globalRelationships.removeAt(capturedIndex)
                refreshRelationshipList()
            }

            relationshipListPanel.add(row)
        }

        relationshipListPanel.revalidate()
        relationshipListPanel.repaint()
    }

    // Helper property to tag buttons (simpler than a map)
    private var JButton.markerForIndex: Int
        get() = (getClientProperty("idx") as? Int) ?: -1
        set(value) { putClientProperty("idx", value) }

    // ─── Validation ──────────────────────────────────────────────────

    override fun doValidate(): ValidationInfo? {
        // Sync all panels before validating
        entityPanels.forEach { it.syncFromUI() }

        for (entity in entities) {
            if (entity.name.isBlank()) {
                return ValidationInfo("Every entity must have a name.")
            }
            if (entity.fields.isEmpty()) {
                return ValidationInfo("Entity '${entity.name}' must have at least one field.")
            }
            for (f in entity.fields) {
                if (f.name.isBlank()) {
                    return ValidationInfo("All fields in '${entity.name}' must have a name.")
                }
            }
        }
        return null
    }

    // ─── Result Conversion ───────────────────────────────────────────

    fun toInputModel(): InputModel {
        entityPanels.forEach { it.syncFromUI() }

        val entitySpecs = entities.map { ue ->
            val fields = ue.fields.map { uf ->
                val annotations = uf.annotations.toMutableList()

                // Auto-add JPA annotations based on flags
                if (uf.primaryKey && annotations.none { it.contains("@Id") }) {
                    annotations.add(0, "@Id")
                    if (annotations.none { it.contains("@GeneratedValue") }) {
                        annotations.add(1, "@GeneratedValue")
                    }
                }
                if (uf.unique && annotations.none { it.contains("unique") }) {
                    annotations.add("@Column(unique=true)")
                }

                FieldSpec(
                    name = uf.name,
                    type = uf.type,
                    primary_key = if (uf.primaryKey) true else null,
                    unique = if (uf.unique) true else null,
                    nullable = if (!uf.nullable) false else null,
                    annotations = annotations,
                    constraints = buildMap {
                        if (uf.primaryKey) put("primary_key", "true")
                        if (uf.unique) put("unique", "true")
                        if (!uf.nullable) put("nullable", "false")
                    }
                )
            }

            EntitySpec(
                name = ue.name,
                table_name = ue.tableName.ifBlank { null },
                annotations = emptyList(),
                fields = fields
            )
        }

        val relSpecs = globalRelationships
            .filter { it.fromEntity.isNotBlank() && it.toEntity.isNotBlank() }
            .map { r ->
                RelationshipSpec(
                    from = r.fromEntity,
                    to = r.toEntity,
                    type = r.type,
                    mapped_by = r.mappedBy.ifBlank { null },
                    annotations = listOf("@${r.type}")
                )
            }

        return InputModel(
            entities = entitySpecs,
            relationships = relSpecs
        )
    }

    // ─── Load from existing InputModel ───────────────────────────────

    private fun loadFromModel(model: InputModel) {
        for (es in model.entities) {
            val uiFields = es.fields.map { fs ->
                UIEntityField(
                    name = fs.name,
                    type = fs.type,
                    primaryKey = fs.primary_key == true || fs.annotations.any { it.contains("@Id") },
                    unique = fs.unique == true || fs.constraints["unique"] == "true",
                    nullable = fs.nullable != false,
                    annotations = fs.annotations.toMutableList()
                )
            }.toMutableList()

            val entity = UIEntity(
                name = es.name,
                tableName = es.table_name ?: "",
                fields = uiFields
            )
            addEntity(entity)
        }

        for (rs in model.relationships) {
            globalRelationships.add(UIRelationship(
                fromEntity = rs.from,
                toEntity = rs.to,
                type = rs.type,
                mappedBy = rs.mapped_by ?: ""
            ))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ENTITY PANEL — one tab per entity
// ═══════════════════════════════════════════════════════════════════

class EntityPanel(private val entity: UIEntity) : JPanel(BorderLayout(5, 5)) {

    private val entityNameField = JBTextField(entity.name, 20)
    private val tableNameField = JBTextField(entity.tableName, 20)
    private val fieldRows = mutableListOf<FieldRow>()
    private val fieldsContainer = JPanel()

    init {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        setupUI()
    }

    private fun setupUI() {
        // ── Top: entity name + table name ──
        val topPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(3, 3, 3, 3)
        gbc.fill = GridBagConstraints.HORIZONTAL

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        topPanel.add(JLabel("Entity Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 0.5
        topPanel.add(entityNameField, gbc)

        gbc.gridx = 2; gbc.weightx = 0.0
        topPanel.add(JLabel("Table Name (optional):"), gbc)
        gbc.gridx = 3; gbc.weightx = 0.5
        topPanel.add(tableNameField, gbc)

        add(topPanel, BorderLayout.NORTH)

        // ── Center: fields list ──
        fieldsContainer.layout = BoxLayout(fieldsContainer, BoxLayout.Y_AXIS)
        fieldsContainer.border = BorderFactory.createTitledBorder("Fields")

        // Add existing fields
        for (f in entity.fields) {
            addFieldRow(f)
        }

        val addFieldBtn = JButton("+ Add Field")
        addFieldBtn.addActionListener {
            val newField = UIEntityField()
            entity.fields.add(newField)
            addFieldRow(newField)
            fieldsContainer.revalidate()
            fieldsContainer.repaint()
        }

        val fieldWrapper = JPanel(BorderLayout())
        fieldWrapper.add(JBScrollPane(fieldsContainer), BorderLayout.CENTER)
        fieldWrapper.add(addFieldBtn, BorderLayout.SOUTH)

        add(fieldWrapper, BorderLayout.CENTER)
    }

    private fun addFieldRow(field: UIEntityField) {
        val row = FieldRow(field) { removeFieldRow(it) }
        fieldRows.add(row)
        fieldsContainer.add(row)
    }

    private fun removeFieldRow(row: FieldRow) {
        val idx = fieldRows.indexOf(row)
        if (idx >= 0) {
            entity.fields.removeAt(idx)
            fieldRows.removeAt(idx)
            fieldsContainer.remove(row)
            fieldsContainer.revalidate()
            fieldsContainer.repaint()
        }
    }

    /**
     * Syncs the current widget values back into the UIEntity model.
     * Called before tab switches and before converting to InputModel.
     */
    fun syncFromUI() {
        entity.name = entityNameField.text.trim()
        entity.tableName = tableNameField.text.trim()
        fieldRows.forEach { it.syncToModel() }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  FIELD ROW — one row per entity field
// ═══════════════════════════════════════════════════════════════════

class FieldRow(
    private val field: UIEntityField,
    private val onRemove: (FieldRow) -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 4, 3)) {

    private val nameField = JBTextField(field.name, 12)
    private val typeCombo = ComboBox(arrayOf(
        "Long", "Integer", "String", "Double", "Float", "Boolean",
        "BigDecimal", "LocalDate", "LocalDateTime", "Date", "UUID"
    ))
    private val pkCheck = JCheckBox("PK", field.primaryKey)
    private val uniqueCheck = JCheckBox("Unique", field.unique)
    private val nullableCheck = JCheckBox("Nullable", field.nullable)
    private val annotationsField = JBTextField(field.annotations.joinToString(", "), 18)

    init {
        typeCombo.isEditable = true // allow custom types
        typeCombo.selectedItem = field.type

        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(2, 0, 2, 0)
        )

        add(JLabel("Name:"))
        add(nameField)
        add(JLabel("Type:"))
        add(typeCombo)
        add(pkCheck)
        add(uniqueCheck)
        add(nullableCheck)
        add(JLabel("Annotations:"))
        add(annotationsField)

        val removeBtn = JButton("\u2716")
        removeBtn.toolTipText = "Remove field"
        removeBtn.foreground = JBColor.RED
        removeBtn.addActionListener { onRemove(this) }
        add(removeBtn)

        annotationsField.toolTipText = "Comma-separated, e.g.: @Column(length=50), @NotBlank"
    }

    fun syncToModel() {
        field.name = nameField.text.trim()
        field.type = typeCombo.selectedItem?.toString()?.trim() ?: "String"
        field.primaryKey = pkCheck.isSelected
        field.unique = uniqueCheck.isSelected
        field.nullable = nullableCheck.isSelected
        field.annotations = annotationsField.text
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
    }
}

// ═══════════════════════════════════════════════════════════════════
//  UTILITY
// ═══════════════════════════════════════════════════════════════════

/** Simple DocumentListener that calls a single lambda on every change */
class SimpleDocListener(private val onChange: () -> Unit) : javax.swing.event.DocumentListener {
    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
}
