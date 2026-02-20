# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All commands run from `ada-mcp-tools/`:

```bash
# Build all modules
mvn clean package

# Run individual servers
mvn spring-boot:run -pl ada-mcp-aircraft-status
mvn spring-boot:run -pl ada-mcp-ad-lookup
mvn spring-boot:run -pl ada-mcp-task-card
mvn spring-boot:run -pl ada-mcp-maintenance-schedule

# Document search requires external services
SPRING_AI_OPENAI_API_KEY=sk-... mvn spring-boot:run -pl ada-mcp-document-search
```

**Note:** There are no automated tests in this codebase. Spring Boot Test is available via `spring-boot-starter-test` if tests are added.

## Architecture Overview

Five standalone Spring Boot 3.5.11 microservices expose aviation maintenance data as MCP (Model Context Protocol) tools over HTTP (Streamable protocol at `/mcp`). Two Goose agent skills orchestrate multi-hop investigations across all five servers.

### MCP Servers (Ports 8081–8085)

| Module | Port | Purpose | Data Source |
|--------|------|---------|-------------|
| `ada-mcp-document-search` | 8081 | Semantic search over FAA corpus | PgVector + OpenAI embeddings |
| `ada-mcp-aircraft-status` | 8082 | Aircraft status by tail number | `ATR-N123AK_Aircraft_Technical_Record.txt` |
| `ada-mcp-ad-lookup` | 8083 | FAA Airworthiness Directive lookup | `ad_index.json` (55 real ADs) |
| `ada-mcp-task-card` | 8084 | Maintenance procedure cards | 4 synthetic `.txt` task cards |
| `ada-mcp-maintenance-schedule` | 8085 | Scheduled tasks from MPD | `AP100-MPD-001_Maintenance_Planning_Document.txt` |

### MCP Tool Exposition

Tools are exposed via `@McpTool` / `@McpToolParam` annotations on service methods. All tool logic lives in a single `*Service` class per module. Business data is loaded at startup via `@PostConstruct` from structured text/JSON files and held in memory — there are no runtime database reads (except the document search server which uses PgVector).

### Data Parsing Pattern

Each server parses its domain data at startup:
- **`AircraftRecordParser`** — regex over structured `.txt` format
- **`AdLookupService`** — Jackson deserialization of `ad_index.json`
- **`TaskCardParser`** — text file chunking into `TaskCard` records
- **`MpdParser`** — structured text section parsing

All domain models use Java records (`AircraftRecord`, `AirworthinessDirective`, `TaskCard`, `ScheduledTask`, etc.).

### Goose Agent Skills

`ada-goose-skills/` contains two skills that orchestrate tool calls across all MCP servers:
- **`ada-predictive-maintenance`** — multi-hop anomaly investigation and risk ranking
- **`ada-procedure-lookup`** — procedure retrieval with tail-specific context enrichment

Each skill has a `SKILL.md` documenting its recommended tool call sequence and example prompts.

### Document Corpus (Three Tiers)

| Tier | Location | Content |
|------|----------|---------|
| Real FAA handbooks | `faa_handbooks/` | 4 public-domain PDFs (~397 MB) |
| Real ADs | `airworthiness_directives/` | 55 FAA ADs by ATA chapter + `ad_index.json` |
| Synthetic demo data | `synthetic_airplane100/` | Fictional "Airplane-100" fleet pack |

**All `synthetic_airplane100/` data is fictional demo data** — not for use in actual aircraft maintenance.

### Demo Aircraft

The primary demo aircraft is **N123AK** (Airplane-100-200ER, S/N AP100-2847, 24,872 FH). Known scenario details: IDG-RIGHT temperature PIREP, nose gear shimmy, Door 1 slides overdue, 3 open ADs.

## Key Technical Details

- **Java 21** — use records, lambdas, pattern matching throughout
- **Spring AI 1.1.2** — managed via BOM in parent `pom.xml`
- **`@McpTool` / `@McpToolParam`** annotations come from `org.springaicommunity.mcp.annotation` (via transitive `org.springaicommunity:mcp-annotations:0.8.0`) — not `org.springframework.ai.mcp.server`
- **Document search** requires a PgVector service (CF service named `vector-db`) and `SPRING_AI_OPENAI_API_KEY`; all other servers are standalone with no external dependencies
- **Embedding model:** OpenAI `text-embedding-3-small` (1536 dimensions, HNSW index)
- **Chunk config:** 750 tokens preferred, 350 min, 500 max per document
- **CF deployment:** each module has a `manifest.yml`; see `goose-config-sample.yml` for deployed URLs

## Package Structure

Packages follow Package-by-Feature (not by layer):
```
com.boeing.ada.aircraftstatus    → Aircraft status queries
com.boeing.ada.adlookup          → AD reference data
com.boeing.ada.taskcard          → Maintenance procedures
com.boeing.ada.schedule          → Maintenance scheduling
com.boeing.ada.documentsearch    → Document semantics + ingestion
```
