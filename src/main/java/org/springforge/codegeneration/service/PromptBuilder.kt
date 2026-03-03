package org.springforge.codegeneration.service

import org.springforge.codegeneration.analysis.LayerRole
import org.springforge.codegeneration.analysis.ProjectAnalysisResult
import org.springforge.codegeneration.parser.InputModel

object PromptBuilder {

    // ─────────────────────────────────────────────────────────────────
    //  A "generatable component" — only included if the project has
    //  the corresponding layer / sub-folder on disk.
    // ─────────────────────────────────────────────────────────────────
    private data class GeneratableComponent(
        val classType: String,          // e.g. "JPA Entity"
        val pkg: String,                // resolved package
        val requirementBlock: String    // multi-line generation instruction
    )

    /**
     * CORE RULE: The prompt is built EXCLUSIVELY from the existing
     * folders and sub-folders found in the project.
     *
     * - Only components whose corresponding layer/role EXISTS
     *   in the project will appear in the mapping table and
     *   generation requirements.
     * - We NEVER invent new folders, sub-folders, or class types
     *   that the project structure does not support.
     */
    fun buildPrompt(
        yamlModel: InputModel,
        analysis: ProjectAnalysisResult
    ): String {

        val sb = StringBuilder()
        val basePackage = analysis.basePackage.ifBlank { "com.example.demo" }

        // ── Resolve which components to generate ──
        val components = resolveComponents(analysis, basePackage)

        // ── Determine missing dependencies (based on what we actually generate) ──
        val missingDeps = determineMissingDepsInternal(analysis, components)

        // Helper: is a given class type present?
        fun has(type: String) = components.any { it.classType == type }

        // ════════════════════════════════════════════════════════
        //  BUILD THE PROMPT
        // ════════════════════════════════════════════════════════

        // ── SECTION 1 — SYSTEM INSTRUCTIONS ──
        sb.appendLine("You are a senior Spring Boot code generator.")
        sb.appendLine("You MUST return ONLY the generated Java source files.")
        sb.appendLine("Do NOT include any explanation, commentary, or markdown formatting outside the file blocks.")
        sb.appendLine()

        // ── SECTION 2 — OUTPUT FORMAT ──
        val examplePkg = components.firstOrNull()?.pkg ?: "$basePackage.model"
        sb.appendLine("## OUTPUT FORMAT (MANDATORY)")
        sb.appendLine("Return each file using EXACTLY this delimiter format:")
        sb.appendLine()
        sb.appendLine("===FILE: src/main/java/${pathOf(examplePkg)}/Example.java===")
        sb.appendLine("package $examplePkg;")
        sb.appendLine("// ... full Java code ...")
        sb.appendLine("===END_FILE===")
        sb.appendLine()
        sb.appendLine("Rules for output:")
        sb.appendLine("- Every file MUST start with ===FILE: <relative-path>===")
        sb.appendLine("- Every file MUST end with ===END_FILE===")
        sb.appendLine("- The <relative-path> must start with src/main/java/ and use / separators")
        sb.appendLine("- Each file must contain a complete, compilable Java class")
        sb.appendLine("- Do NOT wrap output in markdown code fences")
        sb.appendLine("- Do NOT add any text before the first ===FILE: or after the last ===END_FILE===")
        sb.appendLine()

        // ── SECTION 3 — PROJECT CONTEXT ──
        sb.appendLine("## PROJECT CONTEXT (Auto-Detected from Existing Project)")
        sb.appendLine("- Base Package     : $basePackage")
        sb.appendLine("- Build Tool       : ${analysis.buildTool}")

        if (analysis.layers.isNotEmpty()) {
            sb.appendLine("- Existing Folders : ${analysis.layers.joinToString(", ")}")
        }
        if (analysis.detectedLayers.isNotEmpty()) {
            sb.appendLine("- Detected Roles   :")
            analysis.detectedLayers.forEach { layer ->
                val subs = if (layer.subFolders.isNotEmpty())
                    "  (sub-folders: ${layer.subFolders.joinToString(", ")})" else ""
                sb.appendLine("    ${layer.folderName} → ${layer.role.name}$subs")
            }
        }

        // Show current dependencies
        if (analysis.dependencies.isNotEmpty()) {
            sb.appendLine("- Current Dependencies:")
            analysis.dependencies.forEach { dep ->
                val ver = dep.version?.let { ":$it" } ?: ""
                val scope = dep.scope?.let { " ($it)" } ?: ""
                sb.appendLine("    ${dep.groupId}:${dep.artifactId}$ver$scope")
            }
        }
        sb.appendLine()

        // ── SECTION 4 — EXACT PACKAGE MAPPING (dynamic) ──
        sb.appendLine("## EXACT PACKAGE MAPPING (YOU MUST FOLLOW THIS)")
        sb.appendLine("These packages are derived ONLY from EXISTING project folders and sub-folders.")
        sb.appendLine("You MUST place each generated class into the EXACT package listed below.")
        sb.appendLine("Do NOT generate any class types not listed in this table.")
        sb.appendLine()
        sb.appendLine("| Class Type               | Package                           | File Path                                         |")
        sb.appendLine("|--------------------------|-----------------------------------|----------------------------------------------------|")
        for (comp in components) {
            sb.appendLine("| ${comp.classType.padEnd(24)} | ${comp.pkg.padEnd(33)} | src/main/java/${pathOf(comp.pkg).padEnd(37)}/ |")
        }
        sb.appendLine()
        sb.appendLine("⚠ DO NOT create ANY new folders or sub-folders that do not already exist.")
        sb.appendLine("⚠ The ONLY allowed top-level folders under $basePackage are: ${analysis.layers.joinToString(", ")}.")
        val existingSubFolders = analysis.detectedLayers
            .filter { it.subFolders.isNotEmpty() }
            .flatMap { layer -> layer.subFolders.map { "${layer.folderName}/$it" } }
        if (existingSubFolders.isNotEmpty()) {
            sb.appendLine("⚠ The ONLY allowed sub-folders are: ${existingSubFolders.joinToString(", ")}.")
        } else {
            sb.appendLine("⚠ There are NO sub-folders. Do NOT create any sub-folders.")
        }
        sb.appendLine("⚠ Do NOT generate class types not listed in the table above (e.g. do NOT generate ${
            listOf("Repository", "DTO", "Exception", "Service Implementation").filter { type ->
                !components.any { c -> c.classType.contains(type, ignoreCase = true) }
            }.joinToString(", ")
        } unless listed above).")
        sb.appendLine()

        // ── SECTION 5 — DOMAIN MODEL ──
        sb.appendLine("## DOMAIN MODEL (from input.yml)")
        sb.appendLine()

        yamlModel.entities.forEach { entity ->
            sb.appendLine("### Entity: ${entity.name}")
            entity.table_name?.let { sb.appendLine("Table name: $it") }

            if (entity.annotations.isNotEmpty()) {
                sb.appendLine("Class-level annotations: ${entity.annotations.joinToString(", ")}")
            }

            sb.appendLine("Fields:")
            entity.fields.forEach { f ->
                val attrs = mutableListOf<String>()
                if (f.primary_key == true) attrs.add("PRIMARY KEY")
                if (f.unique == true) attrs.add("UNIQUE")
                if (f.nullable == false) attrs.add("NOT NULL")
                if (f.annotations.isNotEmpty()) attrs.add("annotations=${f.annotations}")
                if (f.constraints.isNotEmpty()) attrs.add("constraints=${f.constraints}")

                val detail = if (attrs.isNotEmpty()) " [${attrs.joinToString(", ")}]" else ""
                sb.appendLine("  - ${f.name} : ${f.type}$detail")
            }
            sb.appendLine()
        }

        if (yamlModel.relationships.isNotEmpty()) {
            sb.appendLine("### Relationships")
            yamlModel.relationships.forEach { r ->
                val mappedBy = r.mapped_by?.let { " (mappedBy=$it)" } ?: ""
                sb.appendLine("  - ${r.from} → ${r.to} : ${r.type}$mappedBy")
                if (r.annotations.isNotEmpty()) {
                    sb.appendLine("    Annotations: ${r.annotations.joinToString(", ")}")
                }
            }
            sb.appendLine()
        }

        // ── SECTION 6 — GENERATION REQUIREMENTS (dynamic) ──
        sb.appendLine("## GENERATION REQUIREMENTS")
        sb.appendLine()
        sb.appendLine("For EACH entity defined above, generate the following files.")
        sb.appendLine("Use the EXACT packages from the mapping table above.")
        sb.appendLine("Do NOT generate any file types not listed here.")
        sb.appendLine()

        components.forEachIndexed { index, comp ->
            sb.appendLine("${index + 1}. ${comp.requirementBlock}")
            sb.appendLine()
        }

        // ── SECTION 7 — CONSTRAINTS ──
        sb.appendLine("## CONSTRAINTS")
        sb.appendLine("- Use Spring Boot 3.x / Jakarta EE (jakarta.persistence.*, NOT javax.persistence.*)")
        sb.appendLine("- Every class MUST have a package declaration that EXACTLY matches its file path")
        sb.appendLine("- All imports must be explicit (no wildcard imports)")
        sb.appendLine("- DO NOT create ANY folders or sub-folders that don't already exist in the project")
        sb.appendLine("- The ONLY allowed top-level packages: ${analysis.layers.joinToString(", ")}")
        if (!analysis.hasDependency("org.springframework.boot", "spring-boot-starter-validation")) {
            sb.appendLine("- Do NOT use @Valid, @Validated, or any jakarta.validation.* imports (spring-boot-starter-validation is NOT in the project)")
        }
        sb.appendLine("- Do NOT use any import that is not available from the project's existing dependencies")
        sb.appendLine("- Do NOT generate pom.xml, build.gradle, or any build configuration files. Only generate .java source files.")

        // Dynamic wiring chain based on what exists
        val wiringChain = mutableListOf<String>()
        if (has("REST Controller")) wiringChain.add("Controller")
        if (has("Service Interface")) wiringChain.add("Service Interface")
        if (has("Service Implementation")) wiringChain.add("Service Impl")
        if (has("Repository Interface")) wiringChain.add("Repository")
        if (has("JPA Entity")) wiringChain.add("Entity")
        if (wiringChain.size > 1) {
            sb.appendLine("- Wiring: ${wiringChain.joinToString(" → ")}")
        }

        if (has("JPA Entity")) {
            sb.appendLine("- Handle bidirectional relationships with @JsonBackReference / @JsonManagedReference to prevent infinite recursion")
        }
        if (has("Service Implementation")) {
            sb.appendLine("- Use @Transactional on service implementation methods that modify data")
        }
        sb.appendLine("- Generated code must compile without errors when the project is built")
        sb.appendLine()

        sb.appendLine("Generate ALL files now using the ===FILE: ...=== / ===END_FILE=== format.")

        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════
    //  DYNAMIC COMPONENT RESOLUTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Determine WHICH components to generate based on EXISTING project layers.
     * Only layers that actually exist produce components. No invention.
     *
     * Logic:
     * - ENTITY/MODEL/DOMAIN layer exists  → generate JPA Entity
     * - REPOSITORY layer or repo sub-folder exists  → generate Repository Interface
     * - SERVICE layer exists  → generate Service Interface
     * - SERVICE_IMPL layer or "impl" sub-folder exists → generate Service Implementation (separate row)
     *   OTHERWISE if SERVICE exists → Service Implementation shares the service package
     * - CONTROLLER layer exists → generate REST Controller
     * - DTO layer or "dto" sub-folder exists → generate Request/Response DTO
     * - EXCEPTION layer or "exception" sub-folder exists → generate Exception Classes
     */
    private fun resolveComponents(
        analysis: ProjectAnalysisResult,
        basePackage: String
    ): List<GeneratableComponent> {
        val components = mutableListOf<GeneratableComponent>()
        val roles = analysis.detectedLayers.map { it.role }.toSet()

        // ── Helper: check if a role or named sub-folder exists ──
        fun hasRole(vararg targets: LayerRole) = targets.any { it in roles }
        fun hasSubFolder(parentRoles: List<LayerRole>, subNames: List<String>): Boolean {
            for (role in parentRoles) {
                val layer = analysis.layerFor(role) ?: continue
                if (layer.subFolders.any { it.lowercase() in subNames }) return true
            }
            return false
        }
        fun subFolderPackage(parentRoles: List<LayerRole>, subNames: List<String>): String? {
            for (role in parentRoles) {
                val layer = analysis.layerFor(role) ?: continue
                val match = layer.subFolders.firstOrNull { it.lowercase() in subNames }
                if (match != null) return "${layer.fullPackage}.$match"
            }
            return null
        }

        // ── 1. ENTITY ──
        val hasEntityLayer = hasRole(LayerRole.ENTITY, LayerRole.MODEL)
        if (hasEntityLayer) {
            val entityPkg = analysis.layerFor(LayerRole.ENTITY)?.fullPackage
                ?: analysis.layerFor(LayerRole.MODEL)?.fullPackage
                ?: "$basePackage.model"
            components.add(GeneratableComponent(
                classType = "JPA Entity",
                pkg = entityPkg,
                requirementBlock = """**JPA Entity** in `$entityPkg`
   - @Entity, @Table, @Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
   - Map ALL fields with correct Java types and JPA annotations from the YAML
   - Map relationships using @OneToMany, @ManyToOne, @OneToOne, @ManyToMany
   - Use Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor, @Builder"""
            ))
        }

        // ── 2. REPOSITORY (ALWAYS generated alongside entities — essential for Spring Data JPA) ──
        val hasRepoLayer = hasRole(LayerRole.REPOSITORY)
        val hasRepoSubFolder = hasSubFolder(
            listOf(LayerRole.ENTITY, LayerRole.MODEL),
            listOf("repository", "repo", "dao", "datasource", "persistence")
        )
        if (hasEntityLayer) {
            val entityPkgForRepo = analysis.layerFor(LayerRole.ENTITY)?.fullPackage
                ?: analysis.layerFor(LayerRole.MODEL)?.fullPackage
                ?: "$basePackage.model"
            val repoPkg = when {
                hasRepoLayer -> analysis.layerFor(LayerRole.REPOSITORY)!!.fullPackage
                hasRepoSubFolder -> subFolderPackage(
                    listOf(LayerRole.ENTITY, LayerRole.MODEL),
                    listOf("repository", "repo", "dao", "datasource", "persistence")
                )!!
                else -> entityPkgForRepo  // place alongside entities — no new folders
            }
            components.add(GeneratableComponent(
                classType = "Repository Interface",
                pkg = repoPkg,
                requirementBlock = """**Repository Interface** in `$repoPkg`
   - Extend JpaRepository<EntityName, Long>
   - @Repository annotation
   - Add useful derived query methods (e.g. findByEmail for User)"""
            ))
        }

        // ── 3. SERVICE INTERFACE ──
        val hasServiceLayer = hasRole(LayerRole.SERVICE)
        if (hasServiceLayer) {
            val servicePkg = analysis.layerFor(LayerRole.SERVICE)!!.fullPackage

            // Determine what the service methods should accept/return
            val hasDtos = hasRole(LayerRole.DTO) || hasSubFolder(
                listOf(LayerRole.ENTITY, LayerRole.MODEL),
                listOf("dto", "dtos", "payload")
            )
            val paramStyle = if (hasDtos) "create(RequestDTO), update(Long, RequestDTO)" else "create(Entity), update(Long, Entity)"
            val returnNote = if (hasDtos) "Return ResponseDTOs where applicable" else "Return Entity objects directly"

            components.add(GeneratableComponent(
                classType = "Service Interface",
                pkg = servicePkg,
                requirementBlock = """**Service Interface** in `$servicePkg`
   - CRUD method signatures: findAll(), findById(Long), $paramStyle, delete(Long)
   - $returnNote"""
            ))

            // ── 4. SERVICE IMPLEMENTATION ──
            val hasImplLayer = hasRole(LayerRole.SERVICE_IMPL)
            val hasImplSubFolder = hasSubFolder(listOf(LayerRole.SERVICE), listOf("impl"))
            val serviceImplPkg = when {
                hasImplLayer -> analysis.layerFor(LayerRole.SERVICE_IMPL)!!.fullPackage
                hasImplSubFolder -> subFolderPackage(listOf(LayerRole.SERVICE), listOf("impl"))!!
                else -> servicePkg  // same package
            }

            val injectLine = "Inject the Repository via constructor injection"
            val convertLine = if (hasDtos) "Convert between Entity ↔ DTO in the service layer"
                else "Work directly with Entity objects"
            val exceptionLine = if (hasRole(LayerRole.EXCEPTION) || hasSubFolder(
                    analysis.detectedLayers.map { it.role },
                    listOf("exception", "exceptions", "error", "errors")
                )) "Throw ResourceNotFoundException (RuntimeException) for not-found cases"
                else "Throw RuntimeException with descriptive message for not-found cases"

            components.add(GeneratableComponent(
                classType = "Service Implementation",
                pkg = serviceImplPkg,
                requirementBlock = """**Service Implementation** in `$serviceImplPkg`
   - @Service annotation
   - $injectLine
   - Implement all interface methods with proper exception handling
   - $exceptionLine
   - $convertLine"""
            ))
        }

        // ── 5. DTO ──
        val hasDtoLayer = hasRole(LayerRole.DTO)
        val hasDtoSubFolder = hasSubFolder(
            listOf(LayerRole.ENTITY, LayerRole.MODEL),
            listOf("dto", "dtos", "payload")
        )
        if (hasDtoLayer || hasDtoSubFolder) {
            val dtoPkg = when {
                hasDtoLayer -> analysis.layerFor(LayerRole.DTO)!!.fullPackage
                else -> subFolderPackage(
                    listOf(LayerRole.ENTITY, LayerRole.MODEL),
                    listOf("dto", "dtos", "payload")
                )!!
            }
            components.add(GeneratableComponent(
                classType = "Request/Response DTO",
                pkg = dtoPkg,
                requirementBlock = """**Request DTO & Response DTO** in `$dtoPkg`
   - Separate RequestDTO (no id field) and ResponseDTO (includes id) for each entity
   - Use Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor, @Builder"""
            ))
        }

        // ── 6. CONTROLLER ──
        val hasControllerLayer = hasRole(LayerRole.CONTROLLER)
        if (hasControllerLayer) {
            val controllerPkg = analysis.layerFor(LayerRole.CONTROLLER)!!.fullPackage
            val hasDtos = hasDtoLayer || hasDtoSubFolder
            val acceptReturn = if (hasDtos) {
                """   - Accept RequestDTOs, return ResponseDTOs"""
            } else {
                """   - Accept and return Entity objects directly (no separate DTO layer exists)"""
            }

            // Only include @Valid if validation dependency is present
            val hasValidation = analysis.hasDependency("org.springframework.boot", "spring-boot-starter-validation")
            val annotationsLine = if (hasValidation) {
                "Use @ResponseStatus, @PathVariable, @RequestBody, @Valid"
            } else {
                "Use @ResponseStatus, @PathVariable, @RequestBody. Do NOT use @Valid (validation dependency is not present)"
            }

            components.add(GeneratableComponent(
                classType = "REST Controller",
                pkg = controllerPkg,
                requirementBlock = """**REST Controller** in `$controllerPkg`
   - @RestController, @RequestMapping("/api/<entity-name-lowercase-plural>")
   - Inject the Service Interface via constructor injection
   - Endpoints: GET / (all), GET /{id}, POST /, PUT /{id}, DELETE /{id}
   - $annotationsLine
$acceptReturn"""
            ))
        }

        // ── 7. EXCEPTION ──
        val hasExceptionLayer = hasRole(LayerRole.EXCEPTION)
        val hasExceptionSubFolder = hasSubFolder(
            analysis.detectedLayers.map { it.role },
            listOf("exception", "exceptions", "error", "errors")
        )
        if (hasExceptionLayer || hasExceptionSubFolder) {
            val exceptionPkg = when {
                hasExceptionLayer -> analysis.layerFor(LayerRole.EXCEPTION)!!.fullPackage
                else -> subFolderPackage(
                    analysis.detectedLayers.map { it.role },
                    listOf("exception", "exceptions", "error", "errors")
                )!!
            }
            components.add(GeneratableComponent(
                classType = "Exception Classes",
                pkg = exceptionPkg,
                requirementBlock = """**ResourceNotFoundException** in `$exceptionPkg`
   - extends RuntimeException
   - @ResponseStatus(HttpStatus.NOT_FOUND)
   - Constructor accepting a message String"""
            ))
        }

        return components
    }

    // ═══════════════════════════════════════════════════════════
    //  DEPENDENCY ANALYSIS
    // ═══════════════════════════════════════════════════════════

    /**
     * Determine which Spring Boot starters and libraries are NEEDED by the
     * actually generated components but MISSING from the project's build file.
     * Returns list of (groupId, artifactId) pairs.
     */
    /**
     * Determine which Spring Boot starters and libraries are NEEDED by the
     * generated code but MISSING from the project's build file.
     * Public so the pipeline can use this to programmatically update the build file.
     */
    fun determineMissingDependencies(
        analysis: ProjectAnalysisResult
    ): List<Pair<String, String>> {
        val components = resolveComponents(analysis, analysis.basePackage.ifBlank { "com.example.demo" })
        return determineMissingDepsInternal(analysis, components)
    }

    private fun determineMissingDepsInternal(
        analysis: ProjectAnalysisResult,
        components: List<GeneratableComponent>
    ): List<Pair<String, String>> {
        val missing = mutableListOf<Pair<String, String>>()

        fun has(type: String) = components.any { it.classType == type }

        // Spring Web — needed if we generate controllers
        if (has("REST Controller")) {
            if (!analysis.hasDependency("org.springframework.boot", "spring-boot-starter-web") &&
                !analysis.hasDependency("org.springframework.boot", "spring-boot-starter-webmvc")) {
                missing.add("org.springframework.boot" to "spring-boot-starter-web")
            }
        }

        // Spring Data JPA — needed if we generate repositories OR entities with JPA annotations
        if (has("Repository Interface") || has("JPA Entity")) {
            if (!analysis.hasDependency("org.springframework.boot", "spring-boot-starter-data-jpa")) {
                missing.add("org.springframework.boot" to "spring-boot-starter-data-jpa")
            }
        }

        // Lombok — needed if any component uses it
        if (has("JPA Entity") || has("Request/Response DTO")) {
            if (!analysis.hasDependency("org.projectlombok", "lombok")) {
                missing.add("org.projectlombok" to "lombok")
            }
        }

        // Database driver — only needed if we use JPA (repos or entities)
        if (has("Repository Interface") || has("JPA Entity")) {
            val dbDrivers = listOf(
                "com.h2database" to "h2",
                "com.mysql" to "mysql-connector-j",
                "mysql" to "mysql-connector-java",
                "org.postgresql" to "postgresql",
                "org.mariadb.jdbc" to "mariadb-java-client",
                "com.microsoft.sqlserver" to "mssql-jdbc",
                "com.oracle.database.jdbc" to "ojdbc11"
            )
            val hasAnyDbDriver = dbDrivers.any { (g, a) -> analysis.hasDependency(g, a) }
            if (!hasAnyDbDriver) {
                missing.add("com.h2database" to "h2")
            }
        }

        // NOTE: We do NOT add spring-boot-starter-validation as a missing dep.
        // Instead, the prompt instructs the LLM to avoid @Valid when it's absent.
        // This prevents generating code that requires dependencies the user hasn't chosen.

        return missing
    }

    /** Convert a package name to a file-system path */
    private fun pathOf(pkg: String): String = pkg.replace('.', '/')
}
