package org.springforge.codegeneration.analysis

/**
 * Represents a detected role for an existing project folder.
 * E.g., the folder "api" may have role CONTROLLER, "dao" may have role REPOSITORY.
 */
enum class LayerRole {
    CONTROLLER,      // REST controllers (@RestController, @Controller)
    SERVICE,         // Service interfaces or classes (@Service)
    SERVICE_IMPL,    // Service implementation sub-package
    REPOSITORY,      // Data access (@Repository, JpaRepository, DAO)
    ENTITY,          // JPA entities (@Entity)
    DTO,             // DTOs, request/response objects
    MODEL,           // General model classes (entities + DTOs combined)
    CONFIG,          // Configuration classes (@Configuration)
    EXCEPTION,       // Exception / error handling classes
    UNKNOWN          // Could not determine role
}

/**
 * Maps a role (e.g. CONTROLLER) to the actual folder/package name found in the project.
 * Example: CONTROLLER → "api", REPOSITORY → "dao"
 */
data class DetectedLayer(
    val role: LayerRole,
    val folderName: String,             // e.g. "api", "dao", "model"
    val fullPackage: String,            // e.g. "com.example.demo.api"
    val subFolders: List<String> = emptyList()  // e.g. ["impl"] under service
)

/**
 * A dependency found in the project's build file (pom.xml or build.gradle).
 */
data class BuildDependency(
    val groupId: String,
    val artifactId: String,
    val version: String? = null,
    val scope: String? = null
)

data class ProjectAnalysisResult(
    val detectedArchitecture: String,
    val confidence: Double,
    val basePackage: String,
    val layers: List<String>,
    val namingConventions: Map<String, String>,
    /** Role → actual folder mapping, detected by scanning file contents */
    val detectedLayers: List<DetectedLayer> = emptyList(),
    /** Build tool type: "maven", "gradle", or "unknown" */
    val buildTool: String = "unknown",
    /** Dependencies found in pom.xml or build.gradle */
    val dependencies: List<BuildDependency> = emptyList(),
    /** Raw content of the build file for LLM context */
    val buildFileContent: String? = null,
    /** Relative path to the build file (e.g. "pom.xml" or "build.gradle") */
    val buildFileName: String? = null
) {
    companion object {
        fun empty() = ProjectAnalysisResult(
            detectedArchitecture = "unknown",
            confidence = 0.0,
            basePackage = "",
            layers = emptyList(),
            namingConventions = emptyMap(),
            detectedLayers = emptyList()
        )
    }

    /** Find the detected layer for a given role, or null if not detected */
    fun layerFor(role: LayerRole): DetectedLayer? =
        detectedLayers.firstOrNull { it.role == role }

    /** Get the full package for a role, with optional sub-path appended */
    fun packageFor(role: LayerRole, subPath: String? = null): String? {
        val layer = layerFor(role) ?: return null
        return if (subPath != null) "${layer.fullPackage}.$subPath" else layer.fullPackage
    }

    /** Check if a specific dependency artifact is present */
    fun hasDependency(artifactId: String): Boolean =
        dependencies.any { it.artifactId == artifactId }

    /** Check if a specific dependency group:artifact is present */
    fun hasDependency(groupId: String, artifactId: String): Boolean =
        dependencies.any { it.groupId == groupId && it.artifactId == artifactId }
}
