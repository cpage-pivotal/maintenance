# ADA MCP Tools — Aircraft Data Analyzer

Agentic MCP tool servers and Goose skills for the Aircraft Data Analyzer (ADA)
predictive maintenance demo. The agent investigates maintenance findings,
Airworthiness Directive compliance, and component life status for the fictional
Airplane-100 fleet.

**All aircraft and maintenance data in this project is synthetic demo data.
Do not use for actual aircraft maintenance.**

---

## Project Deliverables

### Five MCP Servers

Each server is a standalone Spring Boot application that exposes tools over the
MCP Streamable HTTP protocol. They are deployed independently to Cloud Foundry
and connected to the Goose agent as extensions.

| Server | Port | Tools | Data Source |
|--------|------|-------|-------------|
| **ada-mcp-document-search** | 8081 | `searchDocuments` | PgVector (FAA handbooks, ADs, synthetic docs) |
| **ada-mcp-aircraft-status** | 8082 | `getAircraftStatus` | `ATR-N123AK_Aircraft_Technical_Record.txt` |
| **ada-mcp-ad-lookup** | 8083 | `lookupAd`, `listAds` | `ad_index.json` (55 real FAA ADs) |
| **ada-mcp-task-card** | 8084 | `getTaskCard` | 4 AMM task card `.txt` files |
| **ada-mcp-maintenance-schedule** | 8085 | `getMaintenanceSchedule` | `AP100-MPD-001_Maintenance_Planning_Document.txt` |

### Two Goose Agent Skills

Skills give the Goose agent procedural knowledge about how to coordinate the
tools to answer complex maintenance queries.

| Skill | Purpose |
|-------|---------|
| **ada-predictive-maintenance** | Multi-hop investigation of anomalies, risk assessments, AD deadlines, and component life |
| **ada-procedure-lookup** | Direct retrieval of maintenance procedures with tail-specific context enrichment |

### Document Ingestion Endpoint

The `ada-mcp-document-search` server includes a `POST /admin/ingest` endpoint
that accepts a directory path, extracts text from PDFs via Apache Tika, chunks
with `TokenTextSplitter`, and loads embeddings into PgVector. A multipart upload
endpoint is also available at `POST /admin/ingest/upload`.

### Sample Goose Configuration

`goose-config-sample.yml` provides a ready-to-use configuration for
goose-agent-chat with all five MCP servers and both skills pre-configured.

---

## Supported Queries

### Anomaly Investigation

The agent traces a reported anomaly through the full maintenance data chain.

> "The crew reported the right IDG is running 15°C above normal on N123AK. What should we do?"

The agent retrieves the PIREP from the aircraft record, discovers the open AD
requiring oil sampling, pulls the task card procedure with metal particle
thresholds, calculates remaining flight hours until the AD deadline, and
recommends next steps.

> "There's a nose gear shimmy on N123AK. What do we know?"

### AD Compliance Deadlines

The agent computes remaining time against compliance deadlines.

> "How much time do we have left on the open ADs for N123AK?"

> "What does AD 2025-04782 require and when is it due?"

### Pre-Check Planning

The agent cross-references scheduled tasks, open ADs, active PIREPs, and
component life data to produce a prioritized work list.

> "What do we need to worry about before the next B-check on N123AK?"

> "What's due at the next A-check?"

### Risk Assessment

The agent surveys the full aircraft status and ranks findings by severity.

> "Give me a risk summary for N123AK. What are the top concerns?"

> "Which components are closest to their life limits?"

### Procedure Lookup

The agent retrieves step-by-step maintenance procedures and enriches them with
tail-specific context.

> "Walk me through the IDG oil inspection procedure."

> "What's the APU oil filter inspection procedure? Are there any concerns for N123AK?"

> "Show me the door latch inspection steps for Door 1."

### Schedule and Interval Queries

The agent queries the Maintenance Planning Document for task intervals, check
definitions, and component life limits.

> "How often does the IDG get replaced?"

> "What are the life limits on the main gear axles?"

> "What tasks are included in a C-check?"

### Handbook and Regulatory Lookup

The agent searches FAA handbooks for background knowledge, standard practices,
torque specs, and regulatory context.

> "What are the FAA requirements for maintenance record keeping?"

> "What does AC 43.13-1B say about dye penetrant inspection procedures?"

---

## Technology Stack

- **Java 21** with modern language constructs (records, lambdas, pattern matching)
- **Spring Boot 3.5.11** with Spring AI 1.1.2
- **MCP Streamable HTTP** via `spring-ai-starter-mcp-server-webmvc`
- **`@McpTool` / `@McpToolParam`** annotations for tool exposure
- **PgVector** on Cloud Foundry for vector storage (document-search only)
- **Apache Tika** for PDF text extraction
- **OpenAI `text-embedding-3-small`** for embeddings
- **Goose Agent Skills** (agentskills.io format) for agent orchestration

---

## Building

```bash
cd ada-mcp-tools
./mvnw clean package
```

This produces a fat JAR in each module's `target/` directory.

---

## Deploying to Cloud Foundry

### Prerequisites

1. A PgVector service instance named `ada-pgvector`
2. An OpenAI API key for embeddings

### Deploy

```bash
# Create the PgVector service (adjust plan name for your foundation)
cf create-service postgres on-demand-postgres-db ada-pgvector

# Build all modules
./mvnw clean package -DskipTests

# Push each server
cf push -f ada-mcp-document-search/manifest.yml
cf push -f ada-mcp-aircraft-status/manifest.yml
cf push -f ada-mcp-ad-lookup/manifest.yml
cf push -f ada-mcp-task-card/manifest.yml
cf push -f ada-mcp-maintenance-schedule/manifest.yml

# Set the OpenAI API key for the document search server
cf set-env ada-document-search SPRING_AI_OPENAI_API_KEY "sk-..."
cf restage ada-document-search
```

### Ingest the Document Corpus

After deploying `ada-document-search`, load the FAA handbooks and synthetic data:

```bash
# Upload PDFs and text files to the app's temp directory, then trigger ingestion.
# Alternatively, use the multipart upload endpoint for individual files:
curl -X POST https://ada-document-search.apps.tas-ndc.kuhn-labs.com/admin/ingest/upload \
  -F "file=@FAA-H-8083-30B_General.pdf" \
  -F "docType=handbook"
```

### Configure Goose

Copy `goose-config-sample.yml` into your goose-agent-chat deployment as
`.goose-config.yml`, or merge its `mcpServers` and `skills` entries into your
existing configuration. Update the server URLs to match your CF routes.

---

## Running Locally

Start each server on its configured port. The four file-based servers have no
external dependencies. The document-search server requires a PostgreSQL instance
with the `pgvector` extension.

```bash
# Start a local PgVector instance
docker run -d --name pgvector -p 5432:5432 \
  -e POSTGRES_PASSWORD=postgres \
  pgvector/pgvector:pg16

# Start each server (in separate terminals)
./mvnw spring-boot:run -pl ada-mcp-aircraft-status
./mvnw spring-boot:run -pl ada-mcp-ad-lookup
./mvnw spring-boot:run -pl ada-mcp-task-card
./mvnw spring-boot:run -pl ada-mcp-maintenance-schedule

# Document search requires PgVector and OpenAI key
SPRING_AI_OPENAI_API_KEY=sk-... ./mvnw spring-boot:run -pl ada-mcp-document-search
```

---

## Project Structure

```
ada-mcp-tools/
├── pom.xml                              Parent POM
├── README.md                            This file
├── goose-config-sample.yml              Sample Goose configuration
├── ada-mcp-document-search/             Semantic search + ingestion (PgVector)
├── ada-mcp-aircraft-status/             Aircraft Technical Record queries
├── ada-mcp-ad-lookup/                   Airworthiness Directive lookup
├── ada-mcp-task-card/                   AMM task card procedures
├── ada-mcp-maintenance-schedule/        MPD schedule and life limits
└── ada-goose-skills/                    Goose agent skills
    ├── ada-predictive-maintenance/      Multi-hop investigation skill
    └── ada-procedure-lookup/            Procedure retrieval skill
```
