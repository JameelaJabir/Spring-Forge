package org.springforge.codegeneration.analysis

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import org.springforge.codegeneration.parser.EntitySpec
import org.springforge.codegeneration.parser.FieldSpec
import org.springforge.codegeneration.parser.RelationshipSpec
import java.io.File

/**
 * Extracts existing JPA entity details from an existing Spring Boot project.
 *
 * Scans the project's `src/main/java` tree for classes annotated with `@Entity`,
 * then uses JavaParser to extract:
 *   - Entity name, table name
 *   - Field names, types, JPA annotations, constraints
 *   - Relationships (@OneToMany, @ManyToOne, @OneToOne, @ManyToMany)
 *
 * The result can be fed into the Entity Designer UI (read-only display)
 * and into the PromptBuilder (so the LLM knows what already exists).
 */
class ExistingEntityExtractor(private val projectBasePath: String) {

    /**
     * Result of scanning existing entities.
     */
    data class ExtractionResult(
        val entities: List<EntitySpec>,
        val relationships: List<RelationshipSpec>
    ) {
        val isEmpty get() = entities.isEmpty()
    }

    companion object {
        /** JPA relationship annotations we scan for */
        private val RELATIONSHIP_ANNOTATIONS = setOf(
            "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
        )

        /** JPA field-level annotations we preserve */
        private val JPA_FIELD_ANNOTATIONS = setOf(
            "Id", "GeneratedValue", "Column", "Lob", "Temporal",
            "Enumerated", "Embedded", "EmbeddedId", "Version",
            "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max",
            "Email", "Pattern", "Positive", "PositiveOrZero",
            "Negative", "NegativeOrZero", "Past", "Future",
            "Transient", "JoinColumn", "JoinTable",
            "OneToOne", "OneToMany", "ManyToOne", "ManyToMany"
        )

        /** Common JPA type mappings for simplifying generic types */
        private val TYPE_SIMPLIFICATIONS = mapOf(
            "List" to "List",
            "Set" to "Set",
            "Collection" to "Collection"
        )
    }

    /**
     * Scan the project for existing @Entity classes and extract their details.
     */
    fun extract(): ExtractionResult {
        val srcMainJava = File(projectBasePath, "src/main/java")
        if (!srcMainJava.exists()) return ExtractionResult(emptyList(), emptyList())

        // Find all Java files that contain @Entity annotation (quick pre-filter)
        val entityFiles = srcMainJava.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .filter { file ->
                try {
                    val text = file.readText()
                    text.contains("@Entity") || text.contains("@javax.persistence.Entity") ||
                            text.contains("@jakarta.persistence.Entity")
                } catch (_: Exception) {
                    false
                }
            }
            .toList()

        if (entityFiles.isEmpty()) return ExtractionResult(emptyList(), emptyList())

        val parser = JavaParser()
        val allEntities = mutableListOf<EntitySpec>()
        val allRelationships = mutableListOf<RelationshipSpec>()

        for (file in entityFiles) {
            try {
                val parseResult = parser.parse(file)
                if (!parseResult.isSuccessful) continue

                val compilationUnit = parseResult.result.orElse(null) ?: continue

                // Find @Entity-annotated classes in this file
                compilationUnit.findAll(ClassOrInterfaceDeclaration::class.java)
                    .filter { cls -> cls.annotations.any { it.nameAsString == "Entity" } }
                    .forEach { cls ->
                        val (entitySpec, relationships) = extractEntityFromClass(cls)
                        allEntities.add(entitySpec)
                        allRelationships.addAll(relationships)
                    }
            } catch (_: Exception) {
                // Skip files that can't be parsed — don't break the whole flow
                continue
            }
        }

        return ExtractionResult(allEntities, allRelationships)
    }

    /**
     * Extract entity details from a parsed @Entity class declaration.
     */
    private fun extractEntityFromClass(
        cls: ClassOrInterfaceDeclaration
    ): Pair<EntitySpec, List<RelationshipSpec>> {
        val entityName = cls.nameAsString
        val relationships = mutableListOf<RelationshipSpec>()

        // Extract @Table(name = "...") if present
        val tableName = cls.annotations
            .firstOrNull { it.nameAsString == "Table" }
            ?.let { tableAnnotation ->
                // Try to get the "name" member
                if (tableAnnotation.isSingleMemberAnnotationExpr) {
                    tableAnnotation.asSingleMemberAnnotationExpr().memberValue.toString()
                        .removeSurrounding("\"")
                } else if (tableAnnotation.isNormalAnnotationExpr) {
                    tableAnnotation.asNormalAnnotationExpr().pairs
                        .firstOrNull { it.nameAsString == "name" }
                        ?.value?.toString()?.removeSurrounding("\"")
                } else null
            }

        // Extract class-level annotations (excluding @Entity and @Table which are implicit)
        val classAnnotations = cls.annotations
            .map { "@${it.nameAsString}" +
                    if (it.isSingleMemberAnnotationExpr || it.isNormalAnnotationExpr)
                        "(${it.childNodes.drop(1).joinToString(", ")})" else ""
            }
            .filter { !it.startsWith("@Entity") && !it.startsWith("@Table") }

        // Extract fields
        val fieldSpecs = mutableListOf<FieldSpec>()
        cls.fields.forEach { field ->
            val (spec, rels) = extractField(field, entityName)
            if (spec != null) {
                fieldSpecs.add(spec)
            }
            relationships.addAll(rels)
        }

        val entitySpec = EntitySpec(
            name = entityName,
            table_name = tableName,
            annotations = classAnnotations,
            fields = fieldSpecs
        )

        return entitySpec to relationships
    }

    /**
     * Extract field details from a FieldDeclaration.
     * Returns the FieldSpec and any relationship specs found on this field.
     */
    private fun extractField(
        field: FieldDeclaration,
        ownerEntityName: String
    ): Pair<FieldSpec?, List<RelationshipSpec>> {
        val relationships = mutableListOf<RelationshipSpec>()

        // A FieldDeclaration can have multiple variables, take the first
        val variable = field.variables.firstOrNull() ?: return null to emptyList()
        val fieldName = variable.nameAsString
        val rawType = variable.type.asString()

        // Collect all annotations on this field
        val annotations = field.annotations.map { anno ->
            val name = anno.nameAsString
            val args = when {
                anno.isSingleMemberAnnotationExpr ->
                    "(${anno.asSingleMemberAnnotationExpr().memberValue})"
                anno.isNormalAnnotationExpr ->
                    "(${anno.asNormalAnnotationExpr().pairs.joinToString(", ") { "${it.name}=${it.value}" }})"
                else -> ""
            }
            "@$name$args"
        }

        // Filter to JPA/validation annotations we care about
        val relevantAnnotations = annotations.filter { annotation ->
            val annotName = annotation.removePrefix("@").substringBefore("(")
            annotName in JPA_FIELD_ANNOTATIONS
        }

        // Detect constraints
        val isPrimaryKey = annotations.any { it.startsWith("@Id") }
        val isUnique = annotations.any { it.contains("unique") && it.contains("true") }
        val isNullable = !annotations.any {
            (it.contains("nullable") && it.contains("false")) ||
                    it.startsWith("@NotNull") || it.startsWith("@NotBlank") || it.startsWith("@NotEmpty")
        }

        // Detect relationships
        for (anno in field.annotations) {
            val annoName = anno.nameAsString
            if (annoName in RELATIONSHIP_ANNOTATIONS) {
                // Determine target entity from the field type
                val targetEntity = extractTargetEntity(rawType)
                val mappedBy = extractMappedBy(anno)

                if (targetEntity != null) {
                    relationships.add(
                        RelationshipSpec(
                            from = ownerEntityName,
                            to = targetEntity,
                            type = annoName,
                            mapped_by = mappedBy,
                            annotations = listOf("@$annoName")
                        )
                    )
                }
            }
        }

        // Simplify the type for display (e.g., List<Order> → List<Order>, but keep it readable)
        val displayType = simplifyType(rawType)

        val constraints = buildMap {
            if (isPrimaryKey) put("primary_key", "true")
            if (isUnique) put("unique", "true")
            if (!isNullable) put("nullable", "false")
        }

        val fieldSpec = FieldSpec(
            name = fieldName,
            type = displayType,
            primary_key = if (isPrimaryKey) true else null,
            unique = if (isUnique) true else null,
            nullable = if (!isNullable) false else null,
            annotations = relevantAnnotations,
            constraints = constraints
        )

        return fieldSpec to relationships
    }

    /**
     * Extract the target entity name from a relationship field type.
     * E.g., `List<Order>` → "Order", `Set<Product>` → "Product", `User` → "User"
     */
    private fun extractTargetEntity(rawType: String): String? {
        // Handle generic types like List<Order>, Set<Product>
        val genericMatch = Regex("""(?:List|Set|Collection)<\s*(\w+)\s*>""").find(rawType)
        if (genericMatch != null) {
            return genericMatch.groupValues[1]
        }
        // Handle direct entity references
        return rawType.takeIf { it.isNotBlank() && it[0].isUpperCase() }
    }

    /**
     * Extract the `mappedBy` value from a relationship annotation.
     */
    private fun extractMappedBy(annotation: com.github.javaparser.ast.expr.AnnotationExpr): String? {
        if (annotation.isNormalAnnotationExpr) {
            return annotation.asNormalAnnotationExpr().pairs
                .firstOrNull { it.nameAsString == "mappedBy" }
                ?.value?.toString()?.removeSurrounding("\"")
        }
        return null
    }

    /**
     * Simplify Java types for display.
     * Keeps generics intact but ensures readability.
     */
    private fun simplifyType(rawType: String): String {
        // Remove fully-qualified names, keeping only the simple name
        return rawType
            .replace(Regex("""java\.util\."""), "")
            .replace(Regex("""java\.lang\."""), "")
            .replace(Regex("""java\.time\."""), "")
            .replace(Regex("""java\.math\."""), "")
            .replace(Regex("""jakarta\.persistence\."""), "")
            .replace(Regex("""javax\.persistence\."""), "")
    }
}
