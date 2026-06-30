<div align="center">

<img src="src/main/resources/icons/springforge.png" alt="SpringForge Logo" width="120"/>

# SpringForge

**Architecture-Aware Spring Boot Development Toolkit for IntelliJ IDEA**

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/springforgeecosystem-prog/Spring-Forge)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ-2024.3%2B-orange.svg)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.23-purple.svg)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://openjdk.org/projects/jdk/17/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-Gradle-brightgreen.svg)](build.gradle.kts)

*Year 4 Research Project - Faculty of Computing*
*Sri Lanka Institute of Information Technology (SLIIT), Malabe, Sri Lanka*

[Overview](#overview) • [Modules](#modules) • [Quality Assurance Engine](#quality-assurance-engine) • [Installation](#installation) • [Quick Start](#quick-start) • [Testing](#testing) • [Team](#team)

</div>

---

## 🚀 Overview

SpringForge is a production-grade **IntelliJ IDEA plugin** developed as a Year 4 group research project. It provides an integrated, AI-powered development assistant purpose-built for Spring Boot applications, combining static code analysis, machine learning-based anti-pattern detection, generative AI for CI/CD automation, and runtime diagnostics - all accessible from a unified sidebar tool window inside the IDE.

The plugin targets the full Spring Boot development lifecycle: from **project scaffolding** and **architecture compliance checking**, to **CI/CD pipeline generation** and **runtime debugging**. It integrates with AWS Bedrock (Claude Sonnet), Google Gemini, a custom FastAPI ML microservice, and the GitHub MCP protocol to deliver intelligent, context-aware assistance without leaving the IDE.

### Research Contributions

| Module | Contributor |
|--------|------------|
| Code Generation Engine | Architecture-aware Spring Boot project scaffolding using LLMs |
| **Quality Assurance Engine** | **Jameela Jabir** - ML + LLM pipeline for anti-pattern detection and AI-powered remediation |
| CI/CD Assistant | Bedrock-powered DevOps artifact generation |
| Runtime Analysis | Runtime debugging, error parsing, and performance monitoring |

**Supervisors:** Ms. Thilini Jayalath · Ms. Shashini Kumarasinge

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        IntelliJ IDEA Plugin (SpringForge)                    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   Unified Tool Window (Sidebar)                      │    │
│  │   ┌──────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐ ┌──────────┐  │    │
│  │   │ Code Gen │ │ CI/CD  │ │ Quality  │ │ Runtime │ │  Audit   │  │    │
│  │   │  Panel   │ │ Panel  │ │  Panel   │ │  Panel  │ │Dashboard │  │    │
│  │   └────┬─────┘ └───┬────┘ └────┬─────┘ └────┬────┘ └──────────┘  │    │
│  └────────│────────────│───────────│─────────────│───────────────────┘    │
│           │            │           │             │                          │
│    ┌──────▼──────┐ ┌───▼──────┐ ┌─▼───────────┐ ┌▼─────────────┐        │
│    │   Code      │ │ CI/CD    │ │  Quality     │ │  Runtime     │        │
│    │ Generation  │ │Assistant │ │  Assurance   │ │  Analyzer    │        │
│    │  Module     │ │ Module   │ │  Engine ★    │ │  Module      │        │
│    └──────┬──────┘ └───┬──────┘ └─┬───────────┘ └┬─────────────┘        │
└───────────│────────────│───────────│──────────────│──────────────────────┘
            │            │           │              │
     ┌──────▼──┐  ┌──────▼──┐  ┌────▼──────┐  ┌───▼───────┐
     │ Gemini  │  │   AWS   │  │ FastAPI   │  │PostgreSQL │
     │   LLM   │  │ Bedrock │  │ML Service │  │  Audit DB │
     │  (Code) │  │(Claude) │  │(port 8081)│  │           │
     └─────────┘  └─────────┘  └─────┬─────┘  └───────────┘
                                      │
                              ┌────────▼────────┐
                              │  Trained ML     │
                              │  Anti-Pattern   │
                              │  Classifier     │
                              └─────────────────┘
```

★ Quality Assurance Engine - primary research contribution detailed in this document.

---

## ✨ Modules

### 1. Code Generation Module

Scaffolds production-ready Spring Boot projects from architecture templates using LLM-assisted generation.

**Capabilities:**
- **New Project Creation** - Full project setup with dependency selection, group/artifact metadata, and architecture pattern selection
- **Existing Project Analysis** - ML-powered detection of the current architecture pattern from source code
- **Smart Code Scaffolding** - Generates controllers, services, repositories, and entities with correct layering
- **LLM Prompt Generation** - Parses `input.yml` configuration and builds context for AI-powered code synthesis via Google Gemini

**Supported Architecture Patterns:** Layered, Hexagonal (Ports & Adapters), Clean Architecture, Event-Driven, Microservices

---

### 2. CI/CD Assistant Module

Generates complete DevOps artifacts from project analysis using AWS Bedrock with Claude Sonnet 4.5.

**Capabilities:**
- **Dockerfile** - Multi-stage optimized builds with detected runtime configuration
- **GitHub Actions Workflows** - Full CI/CD pipelines including test, build, and deployment stages
- **Docker Compose** - Multi-service configurations with auto-detected service dependencies
- **Kubernetes Manifests** - Production-grade `Deployment`, `Service`, and `Ingress` resources
- **Explainability Reports** - HTML-to-PDF reports explaining each generated artifact decision
- **Remote Repository Support** - Analyze GitHub repositories via the GitHub MCP (Model Context Protocol) server without cloning locally

**AI Integration:** AWS Bedrock → Claude Sonnet `us.anthropic.claude-sonnet-4-20250514-v1:0`

**Validation Suite:**
- `DockerfileValidator` - Best-practice and security rules
- `GitHubActionsValidator` - Rules GH001–GH003 (secrets detection, job structure, etc.)
- `DockerComposeValidator` - Service and network validation

---

### 3. Quality Assurance Engine ★

> **This is the primary research contribution.** See the [dedicated section below](#quality-assurance-engine) for full technical detail.

ML-powered detection of Spring Boot architectural anti-patterns, combined with Google Gemini-based AI fix suggestions and LLM-driven validations and filtering.

---

### 4. Runtime Analysis Module

Advanced runtime debugging and error diagnostics integrated with the IntelliJ execution listener.

**Capabilities:**
- Runtime error parsing and structured output
- Console error analysis with `AnalyzeErrorAction`
- Memory and performance monitoring hooks
- Request tracing and metrics collection

---

### 5. Unified Tool Window

A persistent sidebar panel (anchored right in IntelliJ) that provides a unified tabbed interface for all modules - inspired by the native Maven, Gradle, and GitHub Copilot panels. Includes an Audit Dashboard tab backed by PostgreSQL for scan history and event tracing.

---

## Quality Assurance Engine

The QA Engine is a multi-layer pipeline that combines **PSI-based static feature extraction**, a **remote ML classification service**, **LLM-powered validations and  filtering**, and **AI-generated code fix suggestions**. It operates entirely within IntelliJ IDEA without requiring any external CLI tools.

### Architecture Overview

```
  User clicks "Analyze Code Quality"
              │
              ▼
  ┌─────────────────────────┐
  │  ArchitectureSelect     │  ← User selects/confirms architecture pattern
  │  Dialog                 │    (layered / mvc / hexagonal / clean_architecture)
  └───────────┬─────────────┘
              │
              ▼
  ┌─────────────────────────┐
  │  PsiFeatureExtractor    │  ← Scans all .java files via IntelliJ PSI API
  │                         │    Extracts 32 static features per file
  │  Output: List<          │
  │    FileFeatureModel>    │
  └───────────┬─────────────┘
              │
              ▼
  ┌─────────────────────────┐
  │  MLServiceClient        │  ← POST /analyze-project-full
  │                         │    to FastAPI service (api.springforge.dev)
  │  Returns:               │
  │    CombinedAnalysis     │
  │    Result               │
  └───────────┬─────────────┘
              │
       ┌──────┴──────┐
       │             │
       ▼             ▼
 [No violations]  [Violations found]
       │             │
       │             ▼
       │   ┌─────────────────────────┐
       │   │  MLServiceClient        │  ← POST /generate-fixes (batch)
       │   │  + Gemini LLM           │    Gemini generates per-violation
       │   │                         │    context-aware fix suggestions
       │   │  Returns: List<         │
       │   │    FixSuggestion>       │
       │   └───────────┬─────────────┘
       │               │
       └──────┬────────┘
              │
              ▼
  ┌─────────────────────────┐
  │  QualityAssurancePanel  │  ← Displays results in tool window:
  │  (Tool Window UI)       │    • Overall score + label
  │                         │    • Per-layer breakdown
  │                         │    • Violation list with severity
  │                         │    • AI fix suggestions inline
  └─────────────────────────┘
              │
              ▼
  ┌─────────────────────────┐
  │  AuditService           │  ← Logs scan event to PostgreSQL
  └─────────────────────────┘
```

---

### Feature Extraction - PsiFeatureExtractor

`PsiFeatureExtractor` uses IntelliJ's **Program Structure Interface (PSI)** to parse Java source files at the AST level - without shelling out to any external tool. For each `.java` file, it produces a `FileFeatureModel` containing **32 static features** across four categories, mirroring exactly the features used during training so that inference-time consistency is guaranteed.

**Extracted Feature Categories:**

| Category | Features |
|----------|----------|
| **Code Size Metrics** | `loc`, `methods` (method count), `avg_cc` (avg cyclomatic complexity), `annotations` |
| **Cross-Layer Dependency Counts** | `controller_deps`, `service_deps`, `repository_deps`, `entity_deps`, `adapter_deps`, `port_deps`, `usecase_deps`, `gateway_deps`, `total_cross_layer_deps` |
| **Behavioural Boolean Flags** | `has_business_logic`, `has_data_access`, `has_http_handling`, `has_validation`, `has_transaction`, `violates_layer_separation` |
| **Architecture-Specific Violation Indicators** | `tight_coupling_new_keyword`, `broad_catch`, architecture pattern & layer fields |
| **Source Code** | `source_code` (optional — sent to LLM for validation, truncated to 300 lines) |

The regression model additionally uses four computed ratio features at inference - `import_ratio`, `annotation_density`, `method_density`, `dep_per_loc` - plus a composite `violation_score`, giving 33 total input features to the XGBoost quality score model.

**Layer Classification** is performed by `LayerClassifier` using a priority hierarchy: Spring/JPA annotation detection (`@RestController`, `@Service`, `@Repository`, `@Entity`), file-path segment analysis (package names containing `port`, `adapter`, `usecase`, `gateway`, `infrastructure`), class-name suffix matching, and a behavioural fallback.

---

### ML Service Integration - MLServiceClient

The ML backend is a FastAPI microservice deployed at `https://api.springforge.dev/quality/`. The `MLServiceClient` communicates over HTTPS using OkHttp3 with Jackson JSON serialization.

The service hosts a **dual-model ML pipeline**:

| Model | Task | Algorithm | Training Data |
|-------|------|-----------|---------------|
| **Anti-Pattern Classifier** | Detects which of 9 anti-pattern classes a file violates | Random Forest (scikit-learn) | 141,228 balanced samples across 120 repositories |
| **Quality Score Regressor** | Predicts a continuous quality score [0–100] | XGBoost (Optuna-tuned) | 10,000 samples balanced across 5 score buckets |

**Dataset Construction:** 120 real Spring Boot repositories were collected from GitHub using the REST Search API with 6 keyword variants across 8 creation-year ranges (2018–2025), filtered to Java, non-forks, and sorted by star count. Architecture labels were assigned by accumulating indicator scores from directory-name analysis, Spring annotation patterns, and file-name suffix matching. The dataset reflects real-world prevalence: Layered 71.9%, Hexagonal 26.9%, MVC 1.1%, Clean Architecture 0.1%.

**API Endpoints:**

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/analyze-project-full` | POST | Full analysis: ML anti-pattern detection + optional LLM validation |
| `/analyze-project` | POST | Anti-pattern detection only (legacy endpoint) |
| `/generate-fixes` | POST | Batch Gemini fix generation for all violations |
| `/generate-fix` | POST | Single violation fix suggestion |

**Request Payload - ProjectAnalysisRequest:**
```json
{
  "files": [ /* List<FileFeatureModel> — one per .java file */ ],
  "architecture_pattern": "layered",
  "include_source_code": true
}
```

**Response - CombinedAnalysisResult:**

| Field | Type | Description |
|-------|------|-------------|
| `architecture_pattern` | String | Confirmed architecture |
| `total_files_analyzed` | Int | Total .java files processed |
| `overall_score` | Double | Quality score 0.0–100.0 |
| `overall_label` | String | `EXCELLENT` / `GOOD` / `FAIR` / `POOR` / `CRITICAL` |
| `overall_display` | String | Emoji + label (e.g., `✅ EXCELLENT`) |
| `layer_scores` | List\<LayerQualitySummary\> | Per-layer aggregated scores |
| `total_violations` | Int | Total detected violations |
| `anti_patterns` | List\<AntiPatternDetail\> | Detailed violation list |
| `clean_files` | List\<String\> | Files with zero violations |
| `files` | List\<FileQualityResult\> | Per-file quality details |
| `avg_loc` | Double | Average lines of code |
| `avg_cross_layer_deps` | Double | Average cross-layer dependency count |
| `files_with_violations` | Int | Count of affected files |
| `projected_score_after_fixes` | Double | Estimated score if all fixes applied |
| `quality_summary` | String | Human-readable quality narrative |
| `violation_summary` | String | Human-readable violation summary |
| `llm_enhanced` | Boolean | Whether LLM validation was performed |
| `false_positives_filtered` | Int | ML predictions removed by LLM validation |
| `fix_suggestions` | List\<FixSuggestion\> | LLM-validated inline fix suggestions |

---

### Anti-Pattern Detection

The Random Forest classifier detects **9 architecture-aware anti-pattern classes** across four architecture paradigms (Layered, MVC, Hexagonal, Clean Architecture). The pipeline uses one-hot encoding for categorical features, `log1p` transformation for dependency counts, `class_weight='balanced'` for imbalance correction, and 5-fold stratified grid search over `n_estimators`, `max_depth`, and `min_samples_split` maximising macro F1.

**Detected Anti-Pattern Classes:**

| Class Label | Description | Architecture Context |
|-------------|-------------|----------------------|
| `layer_skip_in_layered` | Higher layer bypasses lower layer directly | Layered |
| `missing_transaction_in_layered` | Service operations lack `@Transactional` boundary | Layered |
| `business_logic_in_controller` | Domain logic placed in REST controller | Any |
| `framework_dep_in_domain_hexagonal` | Domain layer imports Spring/JPA framework classes | Hexagonal |
| `missing_port_adapter_in_hexagonal` | Direct infrastructure call without port interface | Hexagonal |
| `tight_coupling_new_keyword` | Direct instantiation with `new` instead of DI | Any |
| `broad_catch` | Catches `Exception` or `Throwable`, swallowing errors | Any |
| `no_validation` | Input not validated at REST boundary | Any |
| `clean` | No violation detected | — |

Each `AntiPatternDetail` carries:
- `pattern_type` - one of the 9 class labels above
- `severity` - `CRITICAL`, `HIGH`, `MEDIUM`, or `LOW`
- `confidence` - ML prediction confidence score (0.0–1.0), used to prioritise LLM validation
- `file_path` - affected file
- `description` - human-readable explanation
- `recommendation` - static best-practice guidance

**Model Comparison (5-Fold Cross-Validation on 141,228 samples):**

| Model | Mean Macro F1 | Std (±) |
|-------|--------------|---------|
| **Random Forest** | **0.9498** | **0.0010** |
| XGBoost | 0.8957 | 0.0012 |
| LinearSVC | 0.7793 | 0.0033 |

**Per-Class Classification Report - Random Forest (Test Set, 28,246 samples):**

| Anti-Pattern Class | Precision | Recall | F1 | Support |
|--------------------|-----------|--------|----|---------|
| `broad_catch` | 0.97 | 0.75 | 0.85 | 3,138 |
| `business_logic_in_controller` | 0.91 | 0.99 | 0.95 | 3,138 |
| `clean` (no violation) | 0.97 | 0.89 | 0.93 | 3,139 |
| `framework_dep_in_domain_hexagonal` | 0.95 | 1.00 | 0.97 | 3,139 |
| `layer_skip_in_layered` | 1.00 | 1.00 | **1.00** | 3,139 |
| `missing_port_adapter_in_hexagonal` | 1.00 | 1.00 | **1.00** | 3,138 |
| `missing_transaction_in_layered` | 0.96 | 1.00 | 0.98 | 3,139 |
| `no_validation` | 0.93 | 0.98 | 0.96 | 3,138 |
| `tight_coupling_new_keyword` | 0.91 | 0.96 | 0.93 | 3,138 |
| **Macro Average** | **0.95** | **0.95** | **0.95** | 28,246 |
| **Overall Accuracy** | | | **0.9502** | |

Structural violations with deterministic feature signatures (`layer_skip_in_layered`, `missing_port_adapter_in_hexagonal`) achieved perfect F1 of 1.00. The `broad_catch` class produced the lowest recall (0.75) due to context-sensitivity - broad exception catches can legitimately appear in infrastructure adapters, introducing label ambiguity.

**Quality Score Thresholds (XGBoost Regressor - 5 Buckets):**

| Score Range | Label | Display |
|-------------|-------|---------|
| 90–100 | EXCELLENT | ✅ EXCELLENT |
| 75–90 | GOOD | 🟡 GOOD |
| 60–75 | FAIR | ⚠️ FAIR |
| 40–60 | POOR | ❌ POOR |
| 0–40 | CRITICAL | 🔴 CRITICAL |

**Quality Score Model Performance (XGBoost, Optuna-tuned - 50 trials):**

| Metric | Value | Target |
|--------|-------|--------|
| Test RMSE | 8.051 | < 10.0 |
| Test MAE | 6.518 | < 8.0 |
| Test R² | **0.9038** | 0.75–0.95 |
| Train RMSE | 7.4481 | — |
| Overfit Gap | +0.6101 | < 3.0 |
| CV Mean RMSE (5-fold) | 8.0500 | — |
| CV Std | 0.1224 | < 1.5 |

Optimal hyperparameters: `n_estimators=462`, `max_depth=4`, `learning_rate=0.032`, `subsample=0.60`. The test R² of 0.9038 confirms the model learns genuine quality patterns rather than the deterministic score formula; the overfit gap of 0.61 confirms strong generalisation.

---

### LLM Validation 

Static ML models cannot distinguish genuine anti-patterns from architectural exceptions that are identical in metric space - for example, a Spring integration test injecting a repository directly produces the same feature profile as a legitimate layer-skip violation. The LLM augmentation layer addresses this by grounding every prediction in actual source code.

**Three design principles govern this layer:**
1. **LLM as validator only** - Gemini is applied to ML-predicted candidates, never used for cold detection from scratch
2. **Source-code grounding** - every prompt includes actual Java source truncated to 300 lines
3. **Graceful degradation** - LLM failures retain the ML-only prediction unchanged

**Validation pipeline:**
- Each predicted anti-pattern is dispatched in parallel to Gemini via `ThreadPoolExecutor` (5 workers)
- Structured prompt contains: anti-pattern type, affected file source, architecture pattern, layer, ML confidence, and false-positive guidance
- Gemini responds in strict JSON: `is_valid`, `reasoning`, enriched description, `before_code`, `after_code`
- Predictions returning `is_valid=false` are validations 
- Files predicted clean with confidence below 0.70 receive an additional review prompt to improve recall on marginal predictions

**Result fields:**
- `llm_enhanced: true` indicates validation was performed
- `false_positives_filtered` - count of ML predictions removed after semantic review

This two-stage pipeline (ML for speed, LLM for precision) achieves **35–52% false positive reduction** with negligible impact on recall.

---

### AI-Powered Fix Suggestions - Gemini Integration

When violations are found, `MLServiceClient.generateProjectFixes()` calls the `/generate-fixes` endpoint, which invokes Google Gemini with the actual source code and violation context. Each returned `FixSuggestion` contains:

| Field | Type | Description |
|-------|------|-------------|
| `ai_powered` | Boolean | `true` when Gemini successfully generated the fix |
| `gemini_fix` | String | Context-aware, file-specific fix text from Gemini |
| `before_code` | String | Static ❌ BEFORE code example |
| `after_code` | String | Static ✅ AFTER code example |
| `recommendation` | String | Best-practice guidance note |

Fix suggestions are displayed inline in the `QualityAssurancePanel` alongside each violation, giving developers actionable remediation without leaving the IDE.

### Hybrid Quality Score

The final quality score blends ML regression with LLM severity assessment:

```
S_hybrid = 0.4 × S_ML + 0.6 × S_LLM
```

Where:
- `S_ML` is the XGBoost-predicted quality score
- `S_LLM` is Gemini's assessment of the validated violation set, applying severity penalties from a perfect score of 100: CRITICAL ≈ −15 pts, HIGH ≈ −10 pts, MEDIUM ≈ −5 pts, LOW ≈ −2 pts

The 0.4/0.6 weighting was determined empirically on 20 manually reviewed projects, where the hybrid score correlated more strongly with human quality judgements than either component alone.

---

### Data Model Reference

```
org.springforge.qualityassurance.model/
├── FileFeatureModel.kt          ← 32-field static feature vector per .java file
├── AntiPatternDetail.kt         ← Individual violation with severity + confidence
├── CombinedAnalysisResult.kt    ← Complete ML + LLM analysis response
├── FixSuggestion.kt             ← Gemini-generated remediation per violation
├── FileQualityResult.kt         ← Per-file score and label
├── LayerQualitySummary.kt       ← Aggregated score per architectural layer
├── PredictionResult.kt          ← Raw ML prediction (legacy)
├── EnhancedPredictionResult.kt  ← ML prediction with metadata (legacy)
├── FeatureModel.kt              ← Base feature model
└── ProjectAnalysisRequest.kt    ← API request envelope
```

---

### QA Module File Structure

```
src/main/java/org/springforge/qualityassurance/
├── actions/
│   └── AnalyzeQualityAction.kt      ← Entry point; orchestrates full analysis flow
├── analysis/
│   └── PsiFeatureExtractor.kt       ← PSI-based 32-feature extraction from Java AST
├── extractor/
│   └── LayerClassifier.kt           ← Classifies files into architectural layers
├── model/                           ← All data classes (see above)
├── network/
│   └── MLServiceClient.kt           ← HTTPS client for FastAPI ML service + Gemini
├── toolwindow/                      ← QA tool window components
│   ├── QualityResultsPanel.kt
│   ├── QualityToolWindowFactory.kt
│   ├── QualityToolWindowPanel.kt    ← QualityReportDialog: 6 tabs (Overview,
│   │                                   Violations, Files, AI Fixes, Metrics,
│   │                                   Full Report) with ScoreRing arc component
│   └── QualityToolWindowService.kt
├── ui/
│   └── ArchitectureSelectDialog.kt  ← Pre-analysis architecture selection dialog
└── utils/
    └── JsonUtil.kt                  ← JSON serialization helpers
```

**Two-phase display:** Quantitative ML results (score, violations) appear immediately after analysis. Gemini fix suggestions load asynchronously and update the panel without blocking, ensuring near-zero latency overhead for the primary report.

---

### QA Unit Tests

**Test file:** `src/test/kotlin/org/springforge/qualityassurance/QualityScanTest.kt`

| Test | What It Verifies |
|------|-----------------|
| `AntiPatternDetail severity preservation` | Severity enum values survive serialization |
| `FixSuggestion default values` | `ai_powered` defaults correctly when Gemini is unavailable |
| `FileFeatureModel field persistence` | All 32 static fields are correctly populated and retained |
| `CombinedAnalysisResult violation counting` | `total_violations` matches `anti_patterns` list size |

Run QA tests:
```bash
./gradlew test --tests "org.springforge.qualityassurance.*"
```

---

## 📋 Requirements

### Minimum Requirements

- **IntelliJ IDEA**: 2024.3 or later (Ultimate Edition recommended)
- **Java**: JDK 21 or later
- **Operating System**: Windows, macOS, or Linux

### Required for Full Functionality

- **AWS Account** with Bedrock access (for CI/CD generation)
  - Claude Sonnet 4 model enabled in `us-east-1`
  - IAM credentials with `bedrock:InvokeModel` permission

### Optional (for GitHub Integration)

- **Docker Desktop** (for GitHub MCP Server)
- **GitHub Personal Access Token** (for remote repository analysis)

---

## 🔧 Installation

### Option 1: Install from JetBrains Marketplace (Coming Soon)

1. Open IntelliJ IDEA
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for "SpringForge Tools"
4. Click **Install** and restart IDE

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/springforgeecosystem-prog/Spring-Forge.git
cd Spring-Forge

# Build the plugin
./gradlew buildPlugin

# The plugin will be in build/distributions/
```

Install the plugin:
1. Go to **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk**
2. Select `build/distributions/Spring-Forge-1.0-SNAPSHOT.zip`
3. Restart IntelliJ IDEA

---

## ⚙️ Configuration

### 1. AWS Bedrock Setup (Required for CI/CD)

Enable Claude Sonnet 4 in AWS Bedrock:

```bash
# Verify Bedrock access
aws bedrock list-foundation-models --region us-east-1 \
  --query 'modelSummaries[?contains(modelId, `claude-sonnet-4`)]'
```

Create `.env` file in your project root:

```bash
cp .env.example .env
```

Edit `.env` with your credentials:

```env
# AWS Bedrock Configuration
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=your_access_key_here
AWS_SECRET_ACCESS_KEY=your_secret_access_key_here

# Claude Configuration (optional - uses defaults)
CLAUDE_MODEL_ID=us.anthropic.claude-sonnet-4-20250514-v1:0
CLAUDE_MAX_TOKENS=4000
```

**IAM Policy Required:**
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "bedrock:InvokeModel",
    "Resource": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-sonnet-4*"
  }]
}
```

### 2. GitHub MCP Setup (Optional)

For analyzing remote GitHub repositories:

**Step 1: Install Docker Desktop**
- Download from [docker.com](https://www.docker.com/products/docker-desktop)
- Start Docker Desktop

**Step 2: Create GitHub Personal Access Token**
1. Go to [GitHub Settings → Tokens](https://github.com/settings/tokens)
2. Create token with `repo`, `public_repo`, `read:org` permissions
3. Copy the token

**Step 3: Configure Environment**

Add to `.env`:
```env
GITHUB_PERSONAL_ACCESS_TOKEN=ghp_your_token_here
GITHUB_HOST=https://github.com
GITHUB_READ_ONLY=true
```

**Verify Setup:**
```bash
# Test GitHub MCP connectivity
docker ps  # Ensure Docker is running
```

📖 **Detailed Setup Guide**: [docs/github-mcp-setup.md](docs/github-mcp-setup.md)

---

## 🚀 Quick Start

### Using the Tool Window (Recommended)

1. **Open SpringForge Sidebar**
   - Look for "SpringForge" tab on the right side of IntelliJ
   - Or go to **View** → **Tool Windows** → **SpringForge**

2. **Choose a Module**
   - **Code Gen** - Generate new projects or analyze existing ones
   - **CI/CD** - Generate DevOps artifacts
   - **Quality** - Analyze code quality
   - **Runtime** - Launch runtime debugger

### Analyze Code Quality (QA Engine)

1. Open a Spring Boot project in IntelliJ IDEA
2. Click the **SpringForge** tab in the right sidebar (or **View → Tool Windows → SpringForge**)
3. Switch to the **Quality** tab
4. Click **Analyze Code Quality**
5. Select the architecture pattern when prompted (or accept the auto-detected one)
6. View results:
   - **Overall Score** and quality label
   - **Per-layer breakdown** (controller, service, repository, etc.)
   - **Violation list** with severity, confidence, and affected file
   - **AI fix suggestions** from Gemini for each violation

### Generate CI/CD Artifacts

1. Open the **CI/CD** tab in the SpringForge sidebar
2. Select source: **Local Project** or **GitHub Repository** (enter URL)
3. Select artifacts to generate: Dockerfile, GitHub Actions, Docker Compose, Kubernetes
4. Click **Generate CI/CD Files**
5. Monitor progress in the inline output console

Generated files appear in your project root:
```
your-project/
├── Dockerfile
├── docker-compose.yml
├── .github/workflows/build.yml
└── k8s/deployment.yml
```

### Create a New Spring Boot Project

1. Open the **Code Gen** tab
2. Click **Create New Spring Boot Project**
3. Select architecture pattern, dependencies, and project metadata
4. Click **Generate**

---

## 📚 Documentation

### User Guides

- [Getting Started Guide](docs/getting-started.md) - Complete walkthrough
- [CI/CD Generation Guide](docs/cicd-guide.md) - Detailed CI/CD usage
- [GitHub MCP Setup](docs/github-mcp-setup.md) - Remote repository analysis
- [Sidebar Tool Window Guide](docs/sidebar-tool-window-setup.md) - Using the unified interface
- [Custom Icon Guide](docs/custom-icon-guide.md) - Customize plugin appearance

### Technical Documentation

- [Architecture Overview](docs/architecture.md) - Plugin architecture
- [GitHub MCP Protocol](docs/github-mcp-module-detection.md) - MCP integration details
- [Testing Guide](docs/how-to-test-mcp-integration.md) - Testing MCP features
- [Troubleshooting](docs/aws-credentials-troubleshooting.md) - Common issues

### Feature Documentation

- [Branch Auto-Fetch](docs/branch-auto-fetch-feature.md)
- [Architecture Detection](docs/github-architecture-detection-fix.md)
- [Enhanced Results Display](docs/GITHUB_MCP_IMPLEMENTATION_SUMMARY.md)

---

## 🏗️ Architecture

## Project Structure

```
Spring-Forge/
├── src/
│   ├── main/
│   │   ├── java/org/springforge/
│   │   │   ├── auth/                    # Session management, login dialog
│   │   │   ├── cicdassistant/           # CI/CD generation module
│   │   │   │   ├── actions/             # IntelliJ action classes
│   │   │   │   ├── bedrock/             # AWS Bedrock / Claude client
│   │   │   │   ├── github/              # GitHub MCP integration
│   │   │   │   ├── mcp/                 # MCP protocol models
│   │   │   │   ├── parsers/             # Gradle/Maven build parsers
│   │   │   │   └── services/            # Explainability, audit
│   │   │   ├── codegeneration/          # Project scaffolding module
│   │   │   │   ├── actions/             # Create/analyze actions
│   │   │   │   └── ui/                  # Architecture selection dialogs
│   │   │   ├── feedback/                # User feedback system
│   │   │   ├── icons/                   # Icon provider
│   │   │   ├── qualityassurance/        # ★ Quality Assurance Engine
│   │   │   │   ├── actions/             # AnalyzeQualityAction (entry point)
│   │   │   │   ├── analysis/            # PsiFeatureExtractor
│   │   │   │   ├── extractor/           # LayerClassifier
│   │   │   │   ├── model/               # Data models (10 classes)
│   │   │   │   ├── network/             # MLServiceClient
│   │   │   │   ├── toolwindow/          # Legacy QA UI
│   │   │   │   └── ui/                  # ArchitectureSelectDialog
│   │   │   ├── runtimeanalysis/         # Runtime debugger module
│   │   │   └── toolwindow/              # Unified sidebar panel
│   │   │       ├── panels/              # CodeGen, CICD, Quality, Runtime, Audit tabs
│   │   │       ├── SpringForgeToolWindowFactory.kt
│   │   │       ├── SpringForgeToolWindowPanel.kt
│   │   │       └── SpringForgeToolWindowService.kt
│   │   └── resources/
│   │       ├── META-INF/plugin.xml      # Plugin manifest
│   │       └── icons/                   # SVG and PNG assets
│   └── test/kotlin/org/springforge/     # JUnit 5 test suite
├── docs/                                # Setup and feature guides
├── lambda/bedrock_proxy.py              # AWS Lambda proxy function
├── build.gradle.kts                     # Gradle build configuration
├── settings.gradle.kts                  # Gradle settings
└── .env.example                         # Environment configuration template
```

---

## Technology Stack

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Language** | Kotlin | 1.9.23 | Primary plugin language |
| **Platform** | IntelliJ Platform SDK | IU-2024.3 | IDE integration, PSI, actions |
| **Build** | Gradle + IntelliJ Gradle Plugin | 1.17.0 | Build, packaging, verification |
| **AI (CI/CD)** | AWS Bedrock — Claude Sonnet | 4.5 | CI/CD artifact generation |
| **AI (QA Fixes)** | Google Gemini | — | Anti-pattern fix suggestions + LLM validation |
| **ML Service** | FastAPI | — | Anti-pattern classifier service |
| **Code Analysis** | IntelliJ PSI API | — | AST-level Java source analysis |
| **Code Analysis** | JavaParser | 3.25.7 | Supplementary AST analysis |
| **HTTP Client** | OkHttp3 | 4.11.0 | ML service and API communication |
| **Serialization** | Jackson | 2.16.0 | JSON marshalling |
| **YAML** | SnakeYAML | 2.1 | `input.yml` configuration parsing |
| **Async** | Kotlin Coroutines | 1.7.3 | Background tasks and UI threading |
| **PDF** | OpenHTMLtoPDF | 1.0.10 | CI/CD explainability report generation |
| **Database** | PostgreSQL JDBC | 42.7.3 | Audit event logging |
| **Protocol** | GitHub MCP (JSON-RPC 2.0) | — | Remote repository analysis |
| **Testing** | JUnit 5 (Jupiter) | 5.10.0 | Unit test framework |

---

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/springforgeecosystem-prog/Spring-Forge.git
   cd Spring-Forge
   ```

2. **Open in IntelliJ IDEA**
   - File → Open → Select `Spring-Forge` directory
   - Wait for Gradle sync to complete

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Add your AWS credentials
   ```

4. **Run the plugin**
   - Click **Run** → **Run Plugin**
   - New IntelliJ window opens with plugin installed

### Development Workflow

1. **Create a feature branch**
   ```bash
   git checkout -b feat/your-feature-name
   ```

2. **Make changes**
   - Follow Kotlin coding conventions
   - Add tests for new features
   - Update documentation

3. **Test thoroughly**
   ```bash
   ./gradlew test
   ./gradlew runPluginVerifier
   ```

4. **Commit with conventional commits**
   ```bash
   git commit -m "feat: add new feature description"
   ```

   Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

5. **Push and create PR**
   ```bash
   git push origin feat/your-feature-name
   ```

### Test Suite Overview

| Test File | Module | Coverage |
|-----------|--------|---------|
| `QualityScanTest.kt` | Quality Assurance Engine | Data model integrity, severity handling, fix defaults |
| `CicdValidationTest.kt` | CI/CD Assistant | GitHub Actions rules GH001–GH003, secrets detection |
| `AuditTest.kt` | Audit Service | PostgreSQL event persistence, schema creation |
| `CodeGenerationTest.kt` | Code Generation | Template generation, file output |
| `ResponseParserTest.kt` | Runtime Analysis | Error message parsing and structured output |

### Code Standards

- **Kotlin Style**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Documentation**: Add KDoc comments for public APIs
- **Testing**: Write unit tests for business logic
- **Error Handling**: Use proper exception handling and user-friendly messages

### Areas for Contribution

- 🐛 **Bug Fixes** - Check [Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
- ✨ **New Features** - Architecture patterns, generators, analyzers
- 📖 **Documentation** - Improve guides, add examples
- 🧪 **Testing** - Increase test coverage
- 🎨 **UI/UX** - Enhance user interface

---

## 🧪 Testing

### Run Tests

```bash
# Run all tests
./gradlew test

# Run plugin verifier (compatibility check)
./gradlew runPluginVerifier

# Run with coverage
./gradlew test jacocoTestReport
```

### Manual Testing

1. **Test CI/CD Generation**
   ```bash
   # Use test project
   cd test-projects/spring-petclinic
   # Generate via plugin
   ```

2. **Test GitHub Integration**
   ```bash
   # Run MCP verification
   ./test-github-mcp.ps1
   ```

3. **Test Quality Analysis**
   - Open a Spring Boot project
   - Run quality analysis from tool window
   - Verify results are accurate

---

## 📦 Building for Distribution

### Build Plugin ZIP

```bash
./gradlew buildPlugin
```

Output: `build/distributions/Spring-Forge-1.0-SNAPSHOT.zip`

### Build for Marketplace

```bash
# Set version in build.gradle.kts
version = "1.0.0"

# Build
./gradlew buildPlugin

# Verify
./gradlew runPluginVerifier
```

---

## 🐛 Troubleshooting

**Quality analysis returns no results**
- Ensure the FastAPI ML service is reachable at `https://api.springforge.dev/quality/`
- Check that your project contains `.java` source files (Kotlin-only projects are not yet supported)
- Verify `GEMINI_API_KEY` in `.env` for fix suggestions

**AWS credentials not working (CI/CD module)**
- Verify credentials in `.env`
- Confirm IAM policy includes `bedrock:InvokeModel`
- Run: `aws bedrock list-foundation-models --region us-east-1`
- See: [docs/aws-credentials-troubleshooting.md](docs/aws-credentials-troubleshooting.md)

**GitHub MCP connection failed**
- Confirm Docker Desktop is running: `docker ps`
- Verify `GITHUB_PERSONAL_ACCESS_TOKEN` is set in `.env` with `repo`, `public_repo` permissions
- See: [docs/github-mcp-setup.md](docs/github-mcp-setup.md)

**SpringForge tab not visible in sidebar**
- Go to **View → Tool Windows → SpringForge**
- Or restart IntelliJ IDEA
- Confirm the plugin is enabled under **Settings → Plugins**

**Plugin fails to build**
```bash
./gradlew clean buildPlugin
```

### Getting Help

1. Check [Documentation](docs/)
2. Search [Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
3. Create new issue with:
   - Plugin version
   - IntelliJ version
   - Error logs from **Help** → **Show Log in Explorer**

---

## 🗺️ Roadmap

### v1.1.0
- [ ] JetBrains Marketplace release
- [ ] Kotlin source file support in QA Engine
- [ ] CQRS and Event Sourcing architecture patterns
- [ ] Terraform/CloudFormation generation in CI/CD module
- [ ] GitLab CI workflow support

### v1.2.0
- [ ] SonarQube integration for combined quality reporting
- [ ] Multi-module Maven/Gradle project support
- [ ] Custom anti-pattern training via plugin settings
- [ ] Database migration script generation

### v2.0.0
- [ ] VS Code extension port
- [ ] CLI tool for CI/CD pipeline integration
- [ ] Team-level quality dashboards with trend tracking

---

## 📝 Changelog

### Version 1.0.0 (2026-01-03)

#### ✨ Features
- **Unified Tool Window** - New sidebar interface with 4 modules
- **GitHub MCP Integration** - Analyze remote repositories
- **Enhanced CI/CD Generation** - AWS Bedrock Claude Sonnet 4.5 integration
- **Architecture Detection** - ML-powered pattern recognition
- **Background Task Progress** - Real-time progress tracking

#### 🐛 Bug Fixes
- Fixed GitHub branch auto-detection
- Resolved Docker line-ending issues
- Improved error handling for MCP protocol

#### 📖 Documentation
- Added comprehensive setup guides
- Created troubleshooting documentation
- Added API documentation

See [full changelog](CHANGELOG.md) for complete history.

---

## 🙏 Acknowledgments

- **AWS Bedrock** - Claude AI integration
- **Anthropic** - Claude Sonnet 4.5 model
- **JetBrains** - IntelliJ Platform SDK
- **Spring Framework** - Architecture patterns and best practices
- **JavaParser** - Java code analysis
- **Model Context Protocol (MCP)** - GitHub integration protocol

---

## 📞 Contact & Support

- **Issues**: [GitHub Issues](https://github.com/springforgeecosystem-prog/Spring-Forge/issues)
- **Discussions**: [GitHub Discussions](https://github.com/springforgeecosystem-prog/Spring-Forge/discussions)
- **Email**: springforgeecosystem@gmail.com

---

## Team

**Sri Lanka Institute of Information Technology (SLIIT)**
*Faculty of Computing — Year 4 Research Project, 2025–2026*

| Name | Student ID | Role |
|------|-----------|------|
| Jameela Jabir | IT22060662 | Quality Assurance Engine |
| Udula Thathsaridu | IT22056320 | CI/CD Assistant Module |
| Tharindu Mahindarathna | IT22076052 | CI/CD Assistant Module |
| Madhini Ariyasena | IT22562524 | Runtime Analysis Module |

**Supervisors:** Ms. Thilini Jayalath · Ms. Shashini Kumarasinge

### Published Research

This project is accompanied by a peer-reviewed research paper:

> J. Jabir, U. Thathsaridu, T. Mahindarathna, M. Ariyasena, T. Jayalath, and S. Kumarasinge, **"AI-Driven Code Quality Spring Boot Assurance Engine As A Plugin,"** Faculty of Computing, Sri Lanka Institute of Information Technology, 2026.

---

<div align="center">

**Made with ❤️ by the SpringForge Team**
**SpringForge — Year 4 Research Project**

*Faculty of Computing · 2025–2026*
⭐ Star us on GitHub — it helps!


[Back to Top](#springforge)

</div>
