# ADA MCP Document Search

Semantic search over the ADA maintenance document corpus via PgVector and OpenAI embeddings. Exposes one MCP tool for natural-language querying and two REST admin endpoints for ingesting new documents.

**Port:** 8081
**MCP endpoint:** `POST /mcp` (Streamable protocol)

## Prerequisites

- PostgreSQL with the `pgvector` extension (CF service named `vector-db`)
- OpenAI API key for `text-embedding-3-small` embeddings

## Running

```bash
SPRING_AI_OPENAI_API_KEY=sk-... mvn spring-boot:run -pl ada-mcp-document-search
```

The schema is auto-initialized on first start (`initialize-schema: true`). A 1536-dimension HNSW index is created automatically.

## MCP Tool

### `searchDocuments`

Search the maintenance document corpus using natural language. Returns chunks ranked by semantic similarity.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | string | yes | Natural language search query |
| `docType` | string | no | Filter: `handbook`, `ad`, `mpd`, `aircraft_record`, `amm_task_card` |
| `ataChapter` | string | no | ATA chapter number, e.g. `24`, `32` |
| `topK` | integer | no | Number of chunks to return (default `5`, max `20`) |

**Response** — list of `DocumentChunk`:

```json
[
  {
    "text": "...",
    "docTitle": "faa-h-8083-30.pdf",
    "docType": "handbook",
    "ataChapter": "24",
    "source": "real",
    "score": 0.87
  }
]
```

## Admin REST API

### Bulk ingest from directory

```
POST /admin/ingest
Content-Type: application/json

{
  "directoryPath": "/path/to/documents",
  "defaultAtaChapter": "32"
}
```

Walks the directory recursively, ingests all `.pdf` and `.txt` files. Metadata (`doc_type`, `source`, `ata_chapter`) is inferred automatically from filenames and parent directory names (see [Metadata Inference](#metadata-inference) below).

**Response:**
```json
{ "documentsProcessed": 12, "chunksCreated": 847, "error": null }
```

### Upload single file

```
POST /admin/ingest/upload
Content-Type: multipart/form-data

file=<binary>
docType=handbook          # optional, default: handbook
ataChapter=24             # optional
source=real               # optional, default: real
```

Returns `202 Accepted` immediately; ingestion runs in the background.

## Metadata Inference

During bulk ingest, metadata is inferred from the file path:

| Condition | `doc_type` | `source` |
|-----------|------------|----------|
| Filename contains `faa-h-8083` or `ac_43` | `handbook` | `real` |
| Parent dir matches `ATA-##` pattern | `ad` | `real` |
| Filename contains `mpd` | `mpd` | `synthetic` |
| Filename contains `atr-` | `aircraft_record` | `synthetic` |
| Filename contains `amm-` | `amm_task_card` | `synthetic` |

For `ATA-##` directories, `ata_chapter` is extracted from the directory name automatically.

## Document Corpus

| Tier | Location | Content |
|------|----------|---------|
| Real FAA handbooks | `../../faa_handbooks/` | 4 public-domain PDFs |
| Real ADs | `../../airworthiness_directives/` | 55 FAA ADs organized by ATA chapter |
| Synthetic demo data | `../../synthetic_airplane100/` | Fictional Airplane-100 fleet documents |

> **Note:** All `synthetic_airplane100/` data is fictional demo data — not for use in actual aircraft maintenance.

## Chunk Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `ada.ingestion.chunk-size` | `750` | Preferred token count per chunk |
| `ada.ingestion.min-chunk-size` | `350` | Minimum token count per chunk |
| `ada.ingestion.max-chunks-per-document` | `10000` | Maximum chunks per document |

## Cloud Foundry Deployment

```bash
mvn clean package -pl ada-mcp-document-search
cf push -f ada-mcp-document-search/manifest.yml
```

The manifest binds the `vector-db` service and expects `OPENAI-API-KEY` from a CF credential store (`((OPENAI-API-KEY))`). Memory is set to 1G to accommodate large PDF parsing.
