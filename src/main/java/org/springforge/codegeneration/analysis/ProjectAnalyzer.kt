package org.springforge.codegeneration.analysis

import com.intellij.openapi.project.Project
import java.io.File

class ProjectAnalyzer(private val project: Project) {

    fun analyze(): ProjectAnalysisResult {
        val projectPath = project.basePath ?: return ProjectAnalysisResult.empty()
        val srcMainJava = File(projectPath, "src/main/java")
        if (!srcMainJava.exists()) return ProjectAnalysisResult.empty()

        val applicationFile = srcMainJava.walkTopDown()
            .firstOrNull {
                it.isFile &&
                        it.name.endsWith(".java") &&
                        it.readText().contains("@SpringBootApplication")
            } ?: return ProjectAnalysisResult.empty()

        val basePackageDir = applicationFile.parentFile
        val basePackage = basePackageDir
            .relativeTo(srcMainJava)
            .path.replace(File.separator, ".")

        // ── Collect top-level layer folder names ──
        val layerDirs = basePackageDir.listFiles()
            ?.filter { it.isDirectory }
            ?: emptyList()

        val layers = layerDirs.map { it.name.lowercase() }

        // ── Detect naming conventions ──
        val namingConventions = mutableMapOf<String, String>()
        basePackageDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .forEach { file ->
                when {
                    file.name.endsWith("Controller.java") ->
                        namingConventions["controller"] = "Controller suffix"
                    file.name.endsWith("ServiceImpl.java") ->
                        namingConventions["service_impl"] = "ServiceImpl suffix"
                    file.name.endsWith("DaoImpl.java") ->
                        namingConventions["dao_impl"] = "DaoImpl suffix"
                    file.name.endsWith("Repository.java") ->
                        namingConventions["repository"] = "Repository suffix"
                    file.name.endsWith("DTO.java") || file.name.endsWith("Dto.java") ->
                        namingConventions["dto"] = "DTO suffix"
                }
            }

        // ── Detect the ROLE of each layer folder by scanning its contents ──
        // Also recursively collect ALL existing sub-folders
        val detectedLayers = layerDirs.map { dir ->
            detectLayerRole(dir, basePackage)
        }

        // ── Scan build file (pom.xml or build.gradle) ──
        val buildInfo = scanBuildFile(File(projectPath))

        return ProjectAnalysisResult(
            detectedArchitecture = "unknown",  // ML model fills this later
            confidence = 0.0,
            basePackage = basePackage,
            layers = layers,
            namingConventions = namingConventions,
            detectedLayers = detectedLayers,
            buildTool = buildInfo.buildTool,
            dependencies = buildInfo.dependencies,
            buildFileContent = buildInfo.buildFileContent,
            buildFileName = buildInfo.buildFileName
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  LAYER ROLE DETECTION
    // ═══════════════════════════════════════════════════════════

    /**
     * Scan the Java files inside a layer folder and determine its role
     * based on annotations, class names, superclass patterns, and folder name.
     * Recursively collects ALL existing sub-folders (not just immediate children).
     */
    private fun detectLayerRole(dir: File, basePackage: String): DetectedLayer {
        val folderName = dir.name.lowercase()
        val fullPackage = "$basePackage.$folderName"

        // Recursively collect ALL existing sub-folders at every depth
        val subFolders = collectSubFolders(dir, "")

        // Scan all Java files recursively in this folder
        val javaFiles = dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .toList()

        // Count signals from file contents
        var controllerSignals = 0
        var serviceSignals = 0
        var repositorySignals = 0
        var entitySignals = 0
        var dtoSignals = 0
        var configSignals = 0
        var exceptionSignals = 0

        for (file in javaFiles) {
            val content = try { file.readText() } catch (_: Exception) { continue }
            val fileName = file.name

            // ── Annotation-based detection ──
            if (content.contains("@RestController") || content.contains("@Controller"))
                controllerSignals += 3
            if (content.contains("@Service"))
                serviceSignals += 3
            if (content.contains("@Repository") || content.contains("JpaRepository") ||
                content.contains("CrudRepository") || content.contains("JpaSpecificationExecutor"))
                repositorySignals += 3
            if (content.contains("@Entity") || content.contains("@Table"))
                entitySignals += 3
            if (content.contains("@Configuration") || content.contains("@Bean"))
                configSignals += 3

            // ── Filename-based detection ──
            if (fileName.endsWith("Controller.java")) controllerSignals += 2
            if (fileName.endsWith("Service.java") || fileName.endsWith("ServiceImpl.java")) serviceSignals += 2
            if (fileName.endsWith("Repository.java") || fileName.endsWith("Repo.java")) repositorySignals += 2
            if (fileName.endsWith("Dao.java") || fileName.endsWith("DaoImpl.java")) repositorySignals += 2
            if (fileName.endsWith("DTO.java") || fileName.endsWith("Dto.java") ||
                fileName.endsWith("Request.java") || fileName.endsWith("Response.java"))
                dtoSignals += 2
            if (fileName.contains("Exception") || fileName.contains("Error")) exceptionSignals += 2

            // ── Content pattern detection ──
            if (content.contains("@RequestMapping") || content.contains("@GetMapping") ||
                content.contains("@PostMapping") || content.contains("@PutMapping") ||
                content.contains("@DeleteMapping"))
                controllerSignals += 2
            if (content.contains("extends RuntimeException") || content.contains("extends Exception"))
                exceptionSignals += 2
        }

        // ── Folder-name based heuristic (lower weight, acts as tiebreaker) ──
        when (folderName) {
            "controller", "controllers", "api", "rest", "web", "endpoint", "endpoints" -> controllerSignals += 1
            "service", "services", "usecase", "usecases" -> serviceSignals += 1
            "repository", "repositories", "repo", "dao", "daos", "datasource", "persistence" -> repositorySignals += 1
            "entity", "entities", "domain", "model", "models" -> entitySignals += 1
            "dto", "dtos", "request", "response", "payload" -> dtoSignals += 1
            "config", "configuration", "configs" -> configSignals += 1
            "exception", "exceptions", "error", "errors" -> exceptionSignals += 1
        }

        // ── Pick the role with the highest signal score ──
        val scores = mapOf(
            LayerRole.CONTROLLER to controllerSignals,
            LayerRole.SERVICE to serviceSignals,
            LayerRole.REPOSITORY to repositorySignals,
            LayerRole.ENTITY to entitySignals,
            LayerRole.DTO to dtoSignals,
            LayerRole.CONFIG to configSignals,
            LayerRole.EXCEPTION to exceptionSignals
        )

        val bestRole = scores.maxByOrNull { it.value }
        val role = if (bestRole != null && bestRole.value > 0) bestRole.key else LayerRole.UNKNOWN

        // ── Special case: if entity and DTO signals are both high in same folder,
        //    it's a combined MODEL folder ──
        val finalRole = if (role == LayerRole.ENTITY && dtoSignals > 0) {
            LayerRole.MODEL
        } else if (role == LayerRole.DTO && entitySignals > 0) {
            LayerRole.MODEL
        } else {
            role
        }

        return DetectedLayer(
            role = finalRole,
            folderName = folderName,
            fullPackage = fullPackage,
            subFolders = subFolders
        )
    }

    /**
     * Recursively collect all sub-folder paths relative to the given root dir.
     * E.g., for a directory with structure: service/impl/cache → returns ["impl", "impl/cache"]
     */
    private fun collectSubFolders(dir: File, prefix: String): List<String> {
        val result = mutableListOf<String>()
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return result
        for (child in children) {
            val relativePath = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            result.add(relativePath)
            result.addAll(collectSubFolders(child, relativePath))
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  BUILD FILE SCANNING (pom.xml / build.gradle)
    // ═══════════════════════════════════════════════════════════

    private data class BuildInfo(
        val buildTool: String,
        val dependencies: List<BuildDependency>,
        val buildFileContent: String?,
        val buildFileName: String?
    )

    private fun scanBuildFile(projectRoot: File): BuildInfo {
        // Try pom.xml first (Maven)
        val pomFile = File(projectRoot, "pom.xml")
        if (pomFile.exists()) {
            return parsePomXml(pomFile)
        }

        // Try build.gradle (Gradle Groovy)
        val gradleFile = File(projectRoot, "build.gradle")
        if (gradleFile.exists()) {
            return parseGradleFile(gradleFile, "build.gradle")
        }

        // Try build.gradle.kts (Gradle Kotlin)
        val gradleKtsFile = File(projectRoot, "build.gradle.kts")
        if (gradleKtsFile.exists()) {
            return parseGradleFile(gradleKtsFile, "build.gradle.kts")
        }

        return BuildInfo("unknown", emptyList(), null, null)
    }

    /**
     * Parse pom.xml to extract dependencies.
     * Uses simple regex/text parsing — no XML library needed.
     */
    private fun parsePomXml(pomFile: File): BuildInfo {
        val content = pomFile.readText()
        val dependencies = mutableListOf<BuildDependency>()

        // Match <dependency> blocks
        val depBlockRegex = Regex(
            "<dependency>\\s*(.*?)\\s*</dependency>",
            RegexOption.DOT_MATCHES_ALL
        )
        val groupRegex = Regex("<groupId>\\s*(.+?)\\s*</groupId>")
        val artifactRegex = Regex("<artifactId>\\s*(.+?)\\s*</artifactId>")
        val versionRegex = Regex("<version>\\s*(.+?)\\s*</version>")
        val scopeRegex = Regex("<scope>\\s*(.+?)\\s*</scope>")

        for (match in depBlockRegex.findAll(content)) {
            val block = match.groupValues[1]
            val groupId = groupRegex.find(block)?.groupValues?.get(1) ?: continue
            val artifactId = artifactRegex.find(block)?.groupValues?.get(1) ?: continue
            val version = versionRegex.find(block)?.groupValues?.get(1)
            val scope = scopeRegex.find(block)?.groupValues?.get(1)

            dependencies.add(BuildDependency(groupId, artifactId, version, scope))
        }

        return BuildInfo("maven", dependencies, content, "pom.xml")
    }

    /**
     * Parse build.gradle or build.gradle.kts to extract dependencies.
     * Uses regex to find implementation/api/compile declarations.
     */
    private fun parseGradleFile(gradleFile: File, fileName: String): BuildInfo {
        val content = gradleFile.readText()
        val dependencies = mutableListOf<BuildDependency>()

        // Match patterns like: implementation 'group:artifact:version'
        // or implementation("group:artifact:version")
        val depRegex = Regex(
            """(?:implementation|api|compile|runtimeOnly|compileOnly|testImplementation)\s*[\('"]+([^:'"]+):([^:'"]+)(?::([^'")\s]+))?['")\s]"""
        )

        for (match in depRegex.findAll(content)) {
            dependencies.add(
                BuildDependency(
                    groupId = match.groupValues[1],
                    artifactId = match.groupValues[2],
                    version = match.groupValues.getOrNull(3)?.ifBlank { null }
                )
            )
        }

        return BuildInfo("gradle", dependencies, content, fileName)
    }
}
