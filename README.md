# Boeing Maintenance Agent

This repository contains the Aircraft Data Analyzer (ADA) tools for the predictive maintenance demonstration. The ADA MCP servers and Goose skills provide an agentic workflow for investigating aircraft maintenance findings, Airworthiness Directive (AD) compliance, component life limits, and procedures.

## Goose Agent Configuration

To connect Goose to the ADA tools and skills provided in the `ada-mcp-tools` directory, use the following configuration for your `.goose-config.yml` (located at `goose-agent-chat/src/main/resources/.goose-config.yml`):

```yaml
# Goose Configuration for Goose Agent Chat

provider: openai
model: gpt-5.2

extensions:
  developer:
    enabled: true

session:
  max_turns: 100

skills:
  - name: ada-predictive-maintenance
    source: https://github.com/cpage-pivotal/ada-mcp-tools.git
    branch: main
    path: ada-goose-skills/ada-predictive-maintenance
  - name: ada-procedure-lookup
    source: https://github.com/cpage-pivotal/ada-mcp-tools.git
    branch: main
    path: ada-goose-skills/ada-procedure-lookup

mcpServers:
  # ADA Predictive Maintenance MCP Servers
  - name: ada-document-search
    type: streamable_http
    url: "https://ada-document-search.apps.tas-ndc.kuhn-labs.com/mcp"
  - name: ada-aircraft-status
    type: streamable_http
    url: "https://ada-aircraft-status.apps.tas-ndc.kuhn-labs.com/mcp"
  - name: ada-ad-lookup
    type: streamable_http
    url: "https://ada-ad-lookup.apps.tas-ndc.kuhn-labs.com/mcp"
  - name: ada-task-card
    type: streamable_http
    url: "https://ada-task-card.apps.tas-ndc.kuhn-labs.com/mcp"
  - name: ada-maintenance-schedule
    type: streamable_http
    url: "https://ada-maintenance-schedule.apps.tas-ndc.kuhn-labs.com/mcp"
```

## Available MCP Servers

The following standalone Spring Boot MCP Servers are included in `ada-mcp-tools` and communicate via Streamable HTTP:
- **ada-document-search**: Vector search against FAA handbooks, ADs, and synthetic documents using PgVector.
- **ada-aircraft-status**: Access to aircraft technical records, anomalies, and PIREPs.
- **ada-ad-lookup**: Lookups and compliance checks against Airworthiness Directives.
- **ada-task-card**: Aircraft Maintenance Manual (AMM) task card retrieval.
- **ada-maintenance-schedule**: Queries against the Maintenance Planning Document (MPD) for intervals and component life limits.

## Available Agent Skills

The Goose agent skills provide the procedural knowledge required to coordinate multiple tools:
- **ada-predictive-maintenance**: Multi-hop investigation of anomalies, risk assessments, AD deadlines, and component life.
- **ada-procedure-lookup**: Direct retrieval of maintenance procedures with tail-specific context enrichment.

For detailed instructions on building, running, and deploying these tools, see the [ADA MCP Tools README](ada-mcp-tools/README.md).
