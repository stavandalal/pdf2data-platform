# PDF2Data Platform

An enterprise-grade, dual-database backend engine built to streamline PDF data extraction, user authentication, and administrative monitoring.

---

## Tech Stack & Core Architecture

* **Backend Framework:** Java 21 / Spring Boot 3.x
* **Security:** Stateless JSON Web Tokens (JWT) & BCrypt Encryption
* **Relational Database (SQL):** MySQL (Handles User Context, Profiles, Roles, and Logs)
* **NoSQL Database:** MongoDB (Handles complex document schemas and unstructured data extractions)

---

## Project Setup & Installation

### 1. Prerequisites
Ensure you have the following installed locally:
* Java 21 JDK
* MySQL Server (running on port 3306)
* MongoDB Community Server (running on port 27017)
* Maven / IntelliJ IDEA

### 2. Run the Application
1. Clone the repository and navigate to the project directory.
2. Verify database connection credentials in `src/main/resources/application.yml`.
3. Build and launch the system via your IDE or terminal:
   ```bash
   mvn spring-boot:run

---

## AI Document Intelligence Upgrade (v2)

This backend was extended into an enterprise AI Document Intelligence Platform,
per `PDF2Data_AI_Architecture_Blueprint_V1`. All original modules were kept
as-is; new modules were added alongside them.

### New modules
| Module | Package | Purpose |
|---|---|---|
| Document Classifier | `classification` | Classifies a document (INVOICE, RESUME, RECEIPT, etc.) via AI with a keyword fallback |
| Validation | `validation` | Rule-based validation of extracted fields (email, date, phone, amount patterns) |
| Confidence | `confidence` | Per-field and overall confidence scoring, persisted to `field_confidence` |
| Highlight | `highlight` | Maps extracted field values back to their location (offset/page) in the source text |
| Learning Engine | `learning` | Learns **generic OCR/layout patterns only** (never literal user values) and stores them in `learning_patterns` |
| Chat | `chat` | Lets a user ask questions about a processed document; history stored in `chat_history` |
| Reasoning | `reasoning` | Builds a human-readable explanation of classification/validation/confidence decisions |
| Processing Orchestrator | `processing` | Wires all of the above together; logs every pipeline stage to `extraction_logs` |

### New endpoints
* `POST /api/processing/upload` (multipart: `file`, `prompt`) - runs the full pipeline and
  returns `documentType`, `documentTypeConfidence`, `data`, `fieldConfidence`,
  `overallConfidence`, `validationIssues`, `highlights`, and `reasoning`.
* `POST /api/chat/ask` (JSON: `documentId`, `message`) - ask a question about a document.
* `GET /api/chat/history/{documentId}` - retrieve the chat transcript for a document.
* `GET /api/learning/suggestions/{documentType}` - view learned OCR-confusion patterns for a document type.

The original `POST /api/documents/upload` endpoint (and its now-superseded `FileUploadController`)
and the unused async `DocumentProcessingService` were removed - `/api/processing/upload` is now the
single, primary upload/processing entry point for the platform.

### New database tables (auto-created via `ddl-auto: update`)
`learning_patterns`, `chat_history`, `field_confidence`, `extraction_logs`, plus a new
nullable `document_type` column on the existing `documents` table.

### Learning Engine Rule
`LearningService` never stores a literal value or a specific correction (e.g. "Rajesh -> Rakesh").
It only stores generic signatures such as an OCR character-confusion rule (`"0<->O"`) or a
layout signature (`fieldName:PAGE_1`), each tied to a document type - never to a user's data.
