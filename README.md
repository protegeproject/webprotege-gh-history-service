# WebProtégé GitHub History Service

A Spring Boot microservice that analyzes GitHub repositories containing ontology files, extracts their change history, and transforms it into WebProtégé-compatible revision documents stored in cloud storage.

## Overview

The WebProtégé GitHub History Service is a specialized microservice designed to bridge the gap between Git-based ontology development and WebProtégé collaborative editing. It analyzes the evolution of ontology files (OWL, RDF, TTL) within GitHub repositories by tracking axiom-level changes across commit history, then converts this information into WebProtégé's internal revision format for seamless integration.

This service enables teams to import existing ontology projects from GitHub into WebProtégé while preserving the complete development history, making it valuable for collaborative ontology editing, research projects, and ontology maintenance workflows.

## Key Features

- **Git Repository Analysis**: Traverses GitHub repository commit history using the github-commit-navigator library
- **Ontology File Processing**: Loads and parses ontology files using the OWL API
- **Axiom-Level Change Tracking**: Identifies individual axiom additions and removals between consecutive commits
- **WebProtégé Integration**: Converts change history into WebProtégé-compatible revision documents
- **Cloud Storage**: Stores generated revision documents in MinIO-compatible object storage
- **Message-Driven Architecture**: Processes requests via RabbitMQ for scalable async operations

## API Usage Guide

This service exposes a message-based API for importing GitHub repositories containing ontology files into WebProtégé. The process is asynchronous and provides real-time status updates through events.

### Runtime Requirements
- **Java 17 or higher**: Required for modern language features and Spring Boot 3.x
- **Docker**: For containerized deployment (optional but recommended)
- **RabbitMQ**: Message broker for request/response handling
- **MinIO or S3-compatible storage**: For storing generated revision documents

### Quick Start

1. **Send a Request**: Submit a GitHub repository for processing
2. **Receive Confirmation**: Get an operation ID to track progress
3. **Monitor Progress**: Listen for status events during processing
4. **Get Results**: Receive the final document location when complete

### Request and Response

#### Import Request

**Channel:** `webprotege.github.CreateProjectHistory`

Send a `CreateProjectHistoryFromGitHubRepositoryRequest` message to start importing a GitHub repository:

```json
{
  "projectId": "your-webprotege-project-id",
  "branchCoordinates": {
    "repositoryUrl": "https://github.com/owner/repository",
    "branch": "main"
  },
  "rootOntologyPath": "path/to/ontology.owl"
}
```

**Parameters:**
- `projectId`: Your WebProtégé project identifier
- `repositoryUrl`: The GitHub repository URL containing your ontology
- `branch`: The git branch to analyze (e.g., "main", "master", "develop")
- `rootOntologyPath`: Relative path to the main ontology file in the repository

#### Import Response

**Channel:** `webprotege.github.CreateProjectHistory` (response on same channel)

You'll immediately receive a `CreateProjectHistoryFromGitHubRepositoryResponse`:

```json
{
  "projectId": "your-webprotege-project-id",
  "operationId": "unique-operation-id",
  "branchCoordinates": {
    "repositoryUrl": "https://github.com/owner/repository",
    "branch": "main"
  }
}
```

**Use the `operationId` to track the progress of your import.**

### Progress Events

The service sends real-time events to keep you informed about the import progress. All events are published on channels under `webprotege.events.github.*`:

#### 🔄 Repository Cloning Events

**Clone Started** (automatically triggered)
- The service begins downloading your GitHub repository

**Clone Succeeded**

**Channel:** `webprotege.events.github.CloneRepositorySucceeded`

```json
{
  "eventType": "GitHubCloneRepositorySucceeded",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {},
  "repository": "local-repository-reference"
}
```

**Clone Failed**

**Channel:** `webprotege.events.github.CloneRepositoryFailed`
```json
{
  "eventType": "GitHubCloneRepositoryFailed",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {},
  "errorMessage": "Description of what went wrong"
}
```

#### 📊 History Analysis Events

**Import Succeeded**

**Channel:** `webprotege.events.github.GenerateProjectHistorySucceeded`

```json
{
  "eventType": "GitHubProjectHistoryImportSucceeded",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {}
}
```

**Import Failed**

**Channel:** `webprotege.events.github.GenerateProjectHistoryFailed`
```json
{
  "eventType": "GitHubProjectHistoryImportFailed",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {},
  "errorMessage": "Description of what went wrong"
}
```

#### 💾 Storage Events

**Store Succeeded**

**Channel:** `webprotege.events.github.StoreProjectHistorySucceeded`

```json
{
  "eventType": "GitHubProjectHistoryStoreSucceeded",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {}
}
```

**Store Failed**

**Channel:** `webprotege.events.github.StoreProjectHistoryFailed`
```json
{
  "eventType": "GitHubProjectHistoryStoreFailed",
  "projectId": "your-project-id",
  "operationId": "your-operation-id",
  "branchCoordinates": {},
  "errorMessage": "Description of what went wrong"
}
```

#### ✅ Final Completion Events

**Overall Success**

**Channel:** `webprotege.events.github.CreateProjectHistorySucceeded`

```json
{
  "eventType": "CreateProjectHistoryFromGitHubRepositorySucceeded",
  "operationId": "your-operation-id",
  "projectId": "your-project-id",
  "documentLocation": {
    "bucket": "storage-bucket-name",
    "name": "path/to/generated/document.json"
  }
}
```

**Overall Failure**

**Channel:** `webprotege.events.github.CreateProjectHistoryFailed`
```json
{
  "eventType": "CreateProjectHistoryFromGitHubRepositoryFailed",
  "operationId": "your-operation-id",
  "projectId": "your-project-id",
  "errorMessage": "Description of what went wrong"
}
```

### Typical Workflow

1. **Submit Request** → Receive operation ID
2. **Repository Cloning** → `GitHubCloneRepositorySucceeded` event
3. **History Analysis** → `GitHubProjectHistoryImportSucceeded` event
4. **Document Storage** → `GitHubProjectHistoryStoreSucceeded` event
5. **Completion** → `CreateProjectHistoryFromGitHubRepositorySucceeded` with document location

### Tips for Success

- **File Formats**: Supports `.owl`, `.obo`, `.ofn`, and `.ttl` ontology files
- **Repository Access**: Ensure the GitHub repository is publicly accessible
- **File Paths**: Use forward slashes (`/`) in file paths, even on Windows
- **Branch Names**: Use the exact branch name as it appears in GitHub
- **Large Repositories**: Processing time increases with repository size and commit history

## Development Commands

### Build Requirements
- **Maven 3.6 or higher**: For building and dependency management
- **Docker** (optional): For building container images

### Building and Testing
```bash
# Clean and compile
mvn clean compile

# Run all tests (unit + integration)
mvn test

# Run only unit tests (excludes integration tests)
mvn test -Dtest="!*Integration*"

# Build the application
mvn clean package

# Build with unit tests only (CI/CD pattern)
mvn --batch-mode clean package -Dtest="!*Integration*"

# Skip tests during build
mvn clean package -DskipTests
```

### Code Quality and Formatting
```bash
# Auto-format code with Google Java Style
mvn spotless:apply

# Check code formatting
mvn spotless:check

# Run static analysis with SpotBugs
mvn spotbugs:check
```

### Running the Application
```bash
# Run with Maven
mvn spring-boot:run
```

### Docker Operations
```bash
# Build Docker image (automatically done during package phase)
mvn clean package

# Manual Docker build
docker build -f Dockerfile --build-arg JAR_FILE=webprotege-gh-history-service-1.0.0.jar -t protegeproject/webprotege-gh-history-service:1.0.0 .
```

### Testing Strategy

- **Unit Tests**: Fast, isolated tests that run in CI/CD pipeline
- **Integration Tests**: Slower tests that require external dependencies (Docker containers)
  - Excluded from CI/CD using `-Dtest="!*Integration*"` pattern
  - Should be run locally during development: `mvn test`

### Local Development vs CI/CD

| Environment | Command | Tests Run | Duration |
|-------------|---------|-----------|----------|
| Local Dev | `mvn test` | All (unit + integration) | ~30-60 seconds |
| CI/CD | `mvn test -Dtest="!*Integration*"` | Unit tests only | ~10-15 seconds |