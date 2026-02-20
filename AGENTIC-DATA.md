# ADA Demo — Agentic Search Data & Architecture Guide

This folder contains the full document corpus for the Aircraft Data Analyzer (ADA)
GenAI demo. The agent uses an **agentic search** architecture: rather than a single
retrieve-then-generate pass, the LLM operates as an autonomous agent that plans
multi-step investigations, calls specialized tools, and iteratively assembles
answers from across the corpus.

The corpus is organized into three tiers: real FAA public-domain handbooks,
real Airworthiness Directives harvested from the Federal Register API, and a
synthetic fleet pack for the fictional "Airplane-100" aircraft.

---

## Directory Structure

```
maintenance/
├── AGENTIC-DATA.md                  ← this file
├── DATA.md                          ← original RAG-oriented data guide
├── faa_handbooks/                   ← Tier 1: FAA public-domain PDFs (~397 MB)
│   ├── FAA-H-8083-30B_General.pdf
│   ├── FAA-H-8083-31B_Airframe.pdf
│   ├── FAA-H-8083-32B_Powerplant.pdf
│   └── AC_43-13-1B_Inspection_Repair.pdf
├── airworthiness_directives/        ← Tier 2: Real ADs from Federal Register API (55 PDFs)
│   ├── ad_index.json                ← metadata index for all ADs
│   ├── ATA-21_Air_Conditioning_Press/
│   ├── ATA-24_Electrical_Power/
│   ├── ATA-26_Fire_Protection/
│   ├── ATA-27_Flight_Controls/
│   ├── ATA-28_Fuel_System/
│   ├── ATA-29_Hydraulics/
│   ├── ATA-32_Landing_Gear/
│   ├── ATA-34_Navigation/
│   ├── ATA-35_Oxygen/
│   ├── ATA-49_APU/
│   ├── ATA-52_Doors/
│   └── ATA-72_Engine/
├── synthetic_airplane100/           ← Tier 3: Synthetic demo documents (fictional)
│   ├── SYNTHETIC_DATA_NOTICE.md
│   ├── synthetic_fleet_pack_index.json
│   ├── AP100-MPD-001_Maintenance_Planning_Document.txt
│   ├── ATR-N123AK_Aircraft_Technical_Record.txt
│   ├── AMM-AP100-24-001_IDG_Oil_Inspection_Task_Card.txt
│   ├── AMM-AP100-32-004_Landing_Gear_Functional_Test_Task_Card.txt
│   ├── AMM-AP100-49-001_APU_Oil_Filter_Inspection_Task_Card.txt
│   └── AMM-AP100-52-001_Door_Latch_Inspection_Task_Card.txt
└── scripts/
    └── download_ada_corpus.sh       ← re-run to recreate Tiers 1 & 2 from scratch
```

---

## Why Agentic Search (Not Plain RAG)

The ADA demo scenarios are **multi-hop by design**. A crew reports an IDG
temperature anomaly; answering that properly requires the agent to connect a
PIREP in the Aircraft Technical Record to an open Airworthiness Directive, then
locate the relevant task card procedure, check component life data, compute
remaining flight hours until compliance deadlines, and optionally pull background
knowledge from the FAA handbooks. No single embedding query can retrieve all of
those pieces in one pass.

Agentic search addresses this by giving the LLM a **tool belt** and an
**iterative reasoning loop**. The agent decides what to look up, evaluates the
results, identifies gaps, and issues follow-up tool calls until it has
everything it needs to synthesize a complete answer.

### Key Advantages Over RAG for This Demo

| Capability | RAG | Agentic Search |
|------------|-----|----------------|
| Single-hop factual lookup | Good | Good |
| Multi-document cross-referencing | Weak — depends on top-k luck | Native — agent issues targeted follow-up searches |
| Arithmetic (remaining FH, deadline computation) | Cannot | Agent calls a calculator tool |
| Structured data queries (component tables, AD status) | Requires bespoke chunking | Agent calls a structured query tool |
| Dynamic scope adjustment | Fixed ATA filter at query time | Agent widens/narrows scope mid-investigation |
| Explainability for demo audience | Black box | Agent's chain of thought is visible and compelling |

---

## Tier 1 — FAA AMT Handbooks (`faa_handbooks/`)

These are official FAA publications, freely available from faa.gov. They are in the
public domain and carry no licensing restrictions. Together they form the general
knowledge backbone of the corpus — the agent draws on these for standard
procedures, material specs, system descriptions, and inspection methods that apply
broadly across commercial aviation.

### FAA-H-8083-30B — Aviation Maintenance Technician Handbook: General
**File:** `FAA-H-8083-30B_General.pdf` (~65 MB)

The foundational handbook for all AMTs. Covers the regulatory environment
(FARs, airworthiness certificates, maintenance records), shop mathematics and
physics, aircraft materials (metals, composites, hardware), standard tools and
measuring equipment, ground operations and safety, and human factors in
maintenance. The starting point for any question about maintenance regulations,
required documentation, or standard shop practices.

**Agent tool mapping:** Searched via `search_documents` with
`doc_type: "handbook"`. The agent typically reaches for this handbook last,
after establishing the aircraft-specific context, to add regulatory or
procedural background to its answer.

### FAA-H-8083-31B — Aviation Maintenance Technician Handbook: Airframe
**File:** `FAA-H-8083-31B_Airframe.pdf` (~107 MB)

The primary reference for airframe systems maintenance. Covers aircraft
structures, flight control systems, landing gear systems, hydraulic systems,
pneumatic systems, fuel systems, environmental control and pressurization,
ice and rain protection, and cabin doors and windows.

This is the most directly relevant handbook for the ADA demo scenarios —
particularly the IDG/electrical, landing gear, hydraulics, door, and
pressurization scenarios. It also contains the zone numbering conventions
(100-series fuselage, 400-series engine pylon, 700-800 series gear/door)
referenced throughout the synthetic task cards.

**Agent tool mapping:** Searched via `search_documents` with
`doc_type: "handbook"` and `ata_chapter` filter. High-value when the agent
needs to explain *why* a system works a certain way, beyond what the task
card covers.

### FAA-H-8083-32B — Aviation Maintenance Technician Handbook: Powerplant
**File:** `FAA-H-8083-32B_Powerplant.pdf` (~205 MB)

The reference for all engine and APU systems. Covers turbofan construction,
compressor and turbine sections, combustion, engine mounts, fuel systems,
lubrication systems, ignition systems, engine instruments, and engine run
procedures. Also covers APU systems and engine fire protection.

Relevant for ADA demo scenarios involving IDG temperature anomalies (the IDG
is mechanically driven from the engine accessory gearbox), engine borescope
tasks, APU oil condition monitoring, and any engine-related AD interpretation.

**Agent tool mapping:** Searched via `search_documents` with
`doc_type: "handbook"` and `ata_chapter` in `["49", "72", "24"]`.

### AC 43.13-1B — Acceptable Methods, Techniques, and Practices: Aircraft Inspection and Repair
**File:** `AC_43-13-1B_Inspection_Repair.pdf` (~21 MB)

Known as the "repair bible." Provides specific torque tables, material standards,
fastener specifications, NDT procedures, welding standards, and structural repair
criteria. Unlike the handbooks (which explain concepts), AC 43.13-1B provides the
specific numeric values mechanics use at the job.

**Agent tool mapping:** Searched via `search_documents` with
`doc_type: "handbook"`. The agent reaches for this when it needs exact
numeric values (torque specs, material grades, acceptable crack limits) that
task cards reference as "per standard practices."

---

## Tier 2 — Airworthiness Directives (`airworthiness_directives/`)

55 real ADs harvested programmatically from the Federal Register API
(`federalregister.gov/api/v1/documents`). All are final rules (type="Rule"),
not proposed rules. Date range spans 1998–2025, providing historical depth.

ADs are mandatory safety notices — legally enforceable FAA orders that require
operators to inspect or fix specific defects within defined timeframes. Their
structured format (applicability, compliance requirements, part numbers) makes
them ideal for both semantic search and structured metadata queries.

### Metadata Index: `ad_index.json`

Every AD in the corpus is catalogued in `ad_index.json` with the following fields:

- `document_number` — FAA AD number (e.g., "2025-04782")
- `title` — full Federal Register title
- `abstract` — 1–3 sentence summary of the defect and action required
- `publication_date` — date published in Federal Register
- `effective_on` — date the AD became mandatory
- `ata_chapter` — ATA system folder it was placed in
- `html_url` — live Federal Register URL
- `pdf_url` — direct PDF download URL
- `local_path` — relative path to the local PDF file

**Agent tool mapping:** Queried directly via `lookup_ad` for known AD numbers,
or discovered via `search_documents` with `doc_type: "ad"` and ATA filters.

### AD Folders by ATA Chapter

| Folder | System | # ADs | Date Range |
|--------|--------|-------|------------|
| `ATA-21_Air_Conditioning_Press` | Air Conditioning & Pressurization | 5 | 2007–2024 |
| `ATA-24_Electrical_Power` | Electrical Power / IDG | 8 | 2015–2025 |
| `ATA-26_Fire_Protection` | Fire Detection & Suppression | 3 | 1998–2021 |
| `ATA-27_Flight_Controls` | Primary & Secondary Flight Controls | 6 | 2023–2025 |
| `ATA-28_Fuel_System` | Fuel Storage & Distribution | 5 | 2022–2024 |
| `ATA-29_Hydraulics` | Hydraulic Power Systems | 4 | 2023–2025 |
| `ATA-32_Landing_Gear` | Landing Gear & Brakes | 6 | 2023–2025 |
| `ATA-34_Navigation` | Navigation & Avionics | 2 | 2023–2024 |
| `ATA-35_Oxygen` | Oxygen Systems | 3 | 2017–2023 |
| `ATA-49_APU` | Auxiliary Power Unit | 4 | 2012–2021 |
| `ATA-52_Doors` | Doors & Emergency Exits | 3 | 2016–2025 |
| `ATA-72_Engine` | Engine Fan, Compressor & Turbine | 6 | 2022–2025 |

### Notable ADs Cross-Referenced in Synthetic Fleet Pack

These real ADs are explicitly referenced in the N123AK aircraft record and task cards,
creating a bridge between the real corpus and the synthetic demo narrative:

| AD Number | System | Status on N123AK | Demo Scenario |
|-----------|--------|-----------------|---------------|
| 2025-04782 | ATA-24 IDG | **OPEN** — due before 25,500 FH | IDG temperature PIREP → oil sample required |
| 2025-09505 | ATA-52 Doors | **OPEN** — due before 25,600 FH | Door 1 latch cartridge dye penetrant inspection |
| 2023-10332 | ATA-32 Landing Gear | Complied 2024-03-18 | Landing gear retraction test (historical) |
| 2021-25494 | ATA-26 Fire Protection | Complied 2024-03-18 | Fire detection dual-channel check (historical) |
| 2024-20389 | ATA-28 Fuel System | **OPEN** — due before 2027-01-01 | Fuel tank wiring inspection |
| 2023-07741 | ATA-35 Oxygen | Complied 2018-02-28 | O2 generator replacement (historical) |

---

## Tier 3 — Synthetic Airplane-100 Fleet Pack (`synthetic_airplane100/`)

**All documents in this folder are fictional and labeled as synthetic demo data.
Do not use for actual aircraft maintenance.**

These documents give the ADA agent an aircraft-specific identity layer that the
general FAA handbooks cannot provide. They model a specific fictional aircraft type
("Airplane-100"), a specific tail number (N123AK), and several open maintenance
findings designed to drive interesting demo conversations.

### `SYNTHETIC_DATA_NOTICE.md`
Disclaimer document. Tag as `doc_type: notice, exclude_from_retrieval: true` so
the agent never surfaces it in answers.

### `synthetic_fleet_pack_index.json`
Pre-built metadata index for all synthetic documents. Same schema as `ad_index.json`.
Used at ingestion time to attach ATA chapter tags, doc type, key topics, and
tail-number notes to each chunk.

### `AP100-MPD-001_Maintenance_Planning_Document.txt`
**Type:** Maintenance Planning Document (MPD)
**ATA:** All chapters (21, 24, 26, 27, 28, 29, 32, 34, 35, 49, 52, 72)

The master task schedule for the Airplane-100. Defines check intervals (A/B/C/D),
all 55+ scheduled task IDs with their FH/FC/calendar intervals, component hard-time
life limits, and AD compliance tracking requirements.

**Agent tool mapping:** Primary target for `get_maintenance_schedule`. Also
searchable via `search_documents` for free-text questions about check intervals.

### `ATR-N123AK_Aircraft_Technical_Record.txt`
**Type:** Aircraft Technical Record
**Aircraft:** Airplane-100-200ER, Tail N123AK, S/N AP100-2847
**Current hours:** 24,872 FH / 18,441 FC

The single most important document for demo scenarios. Contains:
- Complete component status table with time-since-installation vs. life limits
- Upcoming scheduled tasks within 1,000 FH
- Open AD compliance table (3 open ADs)
- Maintenance PIREPs (IDG-RIGHT temperature anomaly, nose gear shimmy, Door 2L force)

**Agent tool mapping:** Primary target for `get_aircraft_status`. Sections are
also indexed in the vector store for `search_documents` queries.

**Engineered findings for demo drama:**
- IDG-RIGHT at 5,890 FH against 7,500 FH life limit — plus a crew PIREP of +15°C temperature anomaly
- Door 1L and Door 1R emergency slides already past 12-year calendar life limit
- Door 2L and Door 2R slides expiring in 2.6 years
- AD 2025-04782 (IDG oil contamination) open — due in 628 FH
- AD 2025-09505 (Door 1 latch replacement) open — due in 728 FH

### `AMM-AP100-24-001_IDG_Oil_Inspection_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 24-11-01 (Electrical Power — IDG)

Step-by-step procedure for IDG oil level check and condition inspection.
Covers: access and cooling precautions, external inspection, sight glass level check,
replenishment procedure, spectrometric oil sampling (with AD 2025-04782 compliance
steps specific to N123AK), and close-up. Includes metal particle thresholds for
iron, copper, and tin that trigger a maintenance hold.

**Agent tool mapping:** Retrieved by `get_task_card` with ATA 24 or by
`search_documents` when investigating IDG-related queries.

### `AMM-AP100-32-004_Landing_Gear_Functional_Test_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 32-31-01 (Landing Gear — Retraction Test)

Full retraction/extension functional test procedure. Covers: aircraft jacking (3-point),
hydraulic ground cart connection, N123AK-specific shimmy dampener inspection
(referencing nose gear PIREP from 2026-01-22), 3-cycle gear retraction test with
timing requirements, position indication system check, and hydraulic leak inspection.

**Agent tool mapping:** Retrieved by `get_task_card` with ATA 32.

### `AMM-AP100-49-001_APU_Oil_Filter_Inspection_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 49-11-01 (APU — Oil and Filter)

APU oil level and filter replacement procedure. Covers: APU cooldown and lockout,
sight glass level check, replenishment, filter removal and metallic debris inspection
(with hold thresholds), spectrometric oil sampling with trending guidance, and
post-maintenance APU ground run and leak check.

N123AK-specific note: APU is at 24,100 FH against 30,000 FH life limit. Oil trending
is flagged as especially important in the final third of service life.

**Agent tool mapping:** Retrieved by `get_task_card` with ATA 49.

### `AMM-AP100-52-001_Door_Latch_Inspection_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 52-11-01 (Doors — Latch Mechanism)

Door latch inspection and lubrication for all 8 cabin doors. Covers: door disarm
procedure, external visual inspection of latches and rollers, dye penetrant
inspection of Door 1L/1R latch cartridges per AD 2025-09505 (with replacement
P/N called out), lubrication procedure, operation check, and calendar life check
for emergency slides.

N123AK-specific: Door 1 slides flagged as overdue. Door 2 slides scheduled for
next C-check. The 728 FH countdown to AD compliance gives urgency to this task.

**Agent tool mapping:** Retrieved by `get_task_card` with ATA 52.

---

## Agent Tool Definitions

The ADA agent is configured with six tools. Each tool maps to a specific type of
data retrieval or computation that the LLM cannot perform through generation alone.

### Tool 1: `search_documents`

**Purpose:** Semantic search across the full chunked vector store (all tiers).

This is the agent's general-purpose retrieval tool — the equivalent of a mechanic
walking over to the filing cabinet. It performs an embedding-based similarity
search and returns ranked chunks.

```json
{
  "name": "search_documents",
  "description": "Search the maintenance document corpus using natural language. Returns the most relevant document chunks ranked by semantic similarity. Use this for general questions about procedures, systems, regulations, or any topic where you need background information.",
  "parameters": {
    "query": {
      "type": "string",
      "description": "Natural language search query."
    },
    "doc_type": {
      "type": "string",
      "enum": ["handbook", "ad", "mpd", "aircraft_record", "amm_task_card"],
      "description": "Optional. Restrict search to a specific document type."
    },
    "ata_chapter": {
      "type": "string",
      "description": "Optional. ATA chapter number (e.g. '24', '32') to narrow scope."
    },
    "top_k": {
      "type": "integer",
      "default": 5,
      "description": "Number of chunks to return."
    }
  },
  "returns": "Array of { chunk_text, doc_title, doc_type, ata_chapter, score }"
}
```

**When the agent uses it:** First pass on any question. Also used for follow-up
searches when the agent realizes it needs background context (e.g., after
reading a task card step that says "per standard practices," the agent searches
AC 43.13-1B for the specific numeric values).

---

### Tool 2: `get_aircraft_status`

**Purpose:** Structured query against the Aircraft Technical Record for a specific
tail number. Returns parsed, structured data rather than raw text chunks.

```json
{
  "name": "get_aircraft_status",
  "description": "Retrieve the current operational status of a specific aircraft. Returns structured data: total flight hours, flight cycles, component time-since-installation, open PIREPs, open ADs, and upcoming scheduled tasks.",
  "parameters": {
    "tail_number": {
      "type": "string",
      "description": "Aircraft registration (e.g. 'N123AK')."
    },
    "section": {
      "type": "string",
      "enum": ["summary", "components", "open_ads", "pireps", "upcoming_tasks", "all"],
      "default": "all",
      "description": "Optional. Return only a specific section of the record."
    }
  },
  "returns": "Structured JSON with requested aircraft data sections."
}
```

**When the agent uses it:** At the start of any tail-specific investigation. This
is how the agent learns that N123AK has an open IDG PIREP, that AD 2025-04782 is
due in 628 FH, or that the Door 1 slides are overdue. It replaces the need for
the RAG pipeline to "get lucky" and retrieve the right Aircraft Technical Record
chunk.

---

### Tool 3: `lookup_ad`

**Purpose:** Direct retrieval of an Airworthiness Directive by its document number.
Bypasses semantic search entirely — this is a precise key-value lookup against
`ad_index.json` plus the full AD text.

```json
{
  "name": "lookup_ad",
  "description": "Retrieve the full text and metadata of a specific Airworthiness Directive by its AD number. Use this when you already know the AD number (e.g., from an aircraft record or task card reference).",
  "parameters": {
    "ad_number": {
      "type": "string",
      "description": "The AD document number (e.g. '2025-04782')."
    }
  },
  "returns": "{ ad_number, title, abstract, effective_on, ata_chapter, full_text, compliance_actions }"
}
```

**When the agent uses it:** After `get_aircraft_status` reveals an open AD, the
agent calls `lookup_ad` to get the full compliance requirements, affected part
numbers, and inspection procedures. This is a deterministic lookup — no
embedding ambiguity.

---

### Tool 4: `get_task_card`

**Purpose:** Retrieve a specific AMM task card by ATA chapter or task ID.

```json
{
  "name": "get_task_card",
  "description": "Retrieve a maintenance task card (AMM procedure) by ATA chapter code or task ID. Returns the full step-by-step procedure with any tail-specific notes.",
  "parameters": {
    "task_id": {
      "type": "string",
      "description": "Optional. Specific task ID (e.g. 'AP100-24-001')."
    },
    "ata_code": {
      "type": "string",
      "description": "Optional. ATA section code (e.g. '24-11-01'). If no task_id is given, returns all task cards matching this ATA code."
    },
    "tail_number": {
      "type": "string",
      "description": "Optional. If provided, the returned task card includes tail-specific notes and PIREP callouts."
    }
  },
  "returns": "{ task_id, title, ata_code, steps[], tail_specific_notes[], referenced_ads[] }"
}
```

**When the agent uses it:** After identifying which system is affected (from the
PIREP or user question) and confirming any open ADs, the agent retrieves the
applicable procedure. Passing the tail number ensures N123AK-specific notes
(e.g., shimmy dampener inspection for nose gear) are included.

---

### Tool 5: `get_maintenance_schedule`

**Purpose:** Query the Maintenance Planning Document for upcoming tasks, check
intervals, and component life limits.

```json
{
  "name": "get_maintenance_schedule",
  "description": "Query the maintenance planning document for scheduled tasks, check intervals, and component life limits. Can filter by check type, ATA chapter, or time horizon.",
  "parameters": {
    "aircraft_type": {
      "type": "string",
      "description": "Aircraft type designator (e.g. 'AP100')."
    },
    "check_type": {
      "type": "string",
      "enum": ["A", "B", "C", "D", "all"],
      "description": "Optional. Filter by maintenance check level."
    },
    "ata_chapter": {
      "type": "string",
      "description": "Optional. Filter tasks by ATA chapter."
    },
    "within_fh": {
      "type": "integer",
      "description": "Optional. Only return tasks due within this many flight hours from current aircraft status."
    }
  },
  "returns": "Array of { task_id, title, ata_chapter, interval_fh, interval_fc, interval_calendar, check_type }"
}
```

**When the agent uses it:** For planning-oriented questions like *"What's due at
the next C-check?"* or *"What are the life limits on the main gear axles?"* The
agent can combine results from this tool with `get_aircraft_status` to compute
exactly when each task comes due for a specific tail number.

---

### Tool 6: `calculate`

**Purpose:** Perform arithmetic and date calculations that the LLM should not
attempt via generation (to avoid hallucinated math).

```json
{
  "name": "calculate",
  "description": "Perform arithmetic or date calculations. Use this instead of mental math for any computation involving flight hours, remaining life, or calendar deadlines.",
  "parameters": {
    "expression": {
      "type": "string",
      "description": "A mathematical expression or date calculation. Examples: '25500 - 24872', 'days_between(2026-02-19, 2027-01-01)', '(7500 - 5890) / 45.2'."
    }
  },
  "returns": "{ result: number | string, expression: string }"
}
```

**When the agent uses it:** After retrieving numeric data from `get_aircraft_status`
or `get_maintenance_schedule`, the agent calls `calculate` to determine remaining
flight hours, project calendar deadlines based on utilization rates, or compare
thresholds. This prevents the LLM from silently getting arithmetic wrong.

---

## Agent Reasoning Loop

The agent follows a **ReAct-style** (Reason + Act) loop:

```
1. THINK   — Analyze the user query. What information is needed? Which tools?
2. ACT     — Call one or more tools.
3. OBSERVE — Examine the tool results. Is anything missing? Do results
             reference other documents the agent should retrieve?
4. THINK   — Are there cross-references to follow? Calculations to perform?
             If yes, go to step 2. If the answer is complete, go to step 5.
5. ANSWER  — Synthesize all gathered evidence into a final response.
```

The loop is capped at a configurable maximum number of iterations (recommended: 8)
to prevent runaway chains. The agent is instructed to cite its sources by
document number and section in every answer.

---

## Example Queries and Agent Flows

### Example 1: IDG Temperature Anomaly

**User query:** *"The crew reported the right IDG is running 15°C above normal on
N123AK. What should we do?"*

**Agent flow:**

| Step | Action | Tool Call | Result Summary |
|------|--------|-----------|----------------|
| 1 | THINK | — | User reports IDG temperature anomaly on N123AK. I need: (a) current aircraft status and any related PIREPs, (b) open ADs for ATA-24, (c) the applicable task card, (d) background on IDG temperature behavior. |
| 2 | ACT | `get_aircraft_status("N123AK", section="pireps")` | PIREP 2026-01-15: IDG-RIGHT running +15°C above baseline. Also shows IDG-RIGHT TSI = 5,890 FH against 7,500 FH life limit. |
| 3 | ACT | `get_aircraft_status("N123AK", section="open_ads")` | AD 2025-04782 is OPEN, due before 25,500 FH — requires spectrometric oil sampling for metal contamination on IDG units with temperature anomalies. |
| 4 | ACT | `lookup_ad("2025-04782")` | Full AD text: mandates oil sample within 100 FH of temperature exceedance report. If Fe > 50 ppm or Cu > 30 ppm, IDG must be removed. |
| 5 | ACT | `get_task_card(ata_code="24-11-01", tail_number="N123AK")` | AMM-AP100-24-001: Full oil inspection procedure with AD 2025-04782 compliance steps. Metal particle thresholds: Fe ≤ 50 ppm, Cu ≤ 30 ppm, Sn ≤ 20 ppm. |
| 6 | ACT | `calculate("25500 - 24872")` | 628 FH remaining until AD compliance deadline. |
| 7 | ACT | `calculate("7500 - 5890")` | 1,610 FH remaining on IDG life limit. |
| 8 | ACT | `search_documents("IDG temperature increase causes turbofan accessory gearbox", doc_type="handbook")` | FAA-H-8083-32B §6-28: IDG temperature rise may indicate bearing wear, oil degradation, or accessory gearbox misalignment. Sustained +15°C is a significant deviation. |
| 9 | ANSWER | — | Synthesizes: the PIREP is consistent with the AD scenario, oil sample is required within 100 FH per AD 2025-04782, here is the step-by-step procedure, here are the pass/fail thresholds, IDG life limit is 78% consumed so early replacement should be considered, and the AD deadline is 628 FH away. |

---

### Example 2: Pre-B-Check Planning

**User query:** *"What do we need to worry about before the next B-check on N123AK?"*

**Agent flow:**

| Step | Action | Tool Call | Result Summary |
|------|--------|-----------|----------------|
| 1 | THINK | — | Need to determine what's coming due. Pull aircraft status, upcoming tasks, and open items. |
| 2 | ACT | `get_aircraft_status("N123AK", section="all")` | 24,872 FH / 18,441 FC. Three open ADs. Three active PIREPs. Component status shows IDG at 78% life, APU at 80% life, Door 1 slides overdue. |
| 3 | ACT | `get_maintenance_schedule("AP100", check_type="B")` | B-check interval: 6,000 FH. B-check task list: 18 tasks across ATA 24, 27, 29, 32, 49, 52. |
| 4 | ACT | `get_aircraft_status("N123AK", section="upcoming_tasks")` | Tasks coming due within 1,000 FH: IDG oil inspection, landing gear functional test, door latch inspection, APU oil filter. |
| 5 | THINK | — | Cross-referencing: open AD 2025-04782 (628 FH) and AD 2025-09505 (728 FH) both fall within the B-check window. The nose gear PIREP should be addressed during landing gear functional test. Door 1 slides are overdue now. |
| 6 | ACT | `lookup_ad("2025-09505")` | Door 1 latch cartridge dye penetrant inspection required. Replacement P/N specified if cracks found. |
| 7 | ACT | `calculate("25500 - 24872")` | AD 2025-04782 due in 628 FH. |
| 8 | ACT | `calculate("25600 - 24872")` | AD 2025-09505 due in 728 FH. |
| 9 | ANSWER | — | Prioritized list: (1) Door 1 emergency slides are overdue — replace immediately, (2) AD 2025-04782 IDG oil sample due in 628 FH — schedule with next IDG task card, (3) AD 2025-09505 door latch DPI due in 728 FH, (4) nose gear shimmy PIREP to be investigated during landing gear functional test, (5) APU entering final third of service life — oil trending is critical. |

---

### Example 3: Specific Procedure Lookup

**User query:** *"Walk me through the APU oil filter inspection procedure."*

**Agent flow:**

| Step | Action | Tool Call | Result Summary |
|------|--------|-----------|----------------|
| 1 | THINK | — | Straightforward procedure request. ATA 49. |
| 2 | ACT | `get_task_card(ata_code="49-11-01")` | AMM-AP100-49-001: Full procedure — cooldown/lockout, sight glass check, replenishment, filter removal, metallic debris inspection, oil sampling, APU ground run. |
| 3 | THINK | — | Task card is self-contained. No open ADs for ATA-49 on this aircraft, but I should note the APU life status if a tail number is contextually known. |
| 4 | ACT | `get_aircraft_status("N123AK", section="components")` | APU at 24,100 FH against 30,000 FH life limit (80%). Oil trending flagged as critical in final third. |
| 5 | ANSWER | — | Delivers the step-by-step procedure, highlights that N123AK's APU is at 80% life so the Fe threshold for borescope escalation is especially important to watch. |

This example shows how even a "simple" lookup benefits from the agentic approach:
the agent enriches the procedure with tail-specific context the user didn't
explicitly ask for but needs to know.

---

### Example 4: AD Compliance Deadline Query

**User query:** *"How much time do we have left on the open ADs for N123AK?"*

**Agent flow:**

| Step | Action | Tool Call | Result Summary |
|------|--------|-----------|----------------|
| 1 | ACT | `get_aircraft_status("N123AK", section="open_ads")` | Three open ADs: 2025-04782 (due 25,500 FH), 2025-09505 (due 25,600 FH), 2024-20389 (due 2027-01-01). |
| 2 | ACT | `calculate("25500 - 24872")` | AD 2025-04782: 628 FH remaining. |
| 3 | ACT | `calculate("25600 - 24872")` | AD 2025-09505: 728 FH remaining. |
| 4 | ACT | `calculate("days_between(2026-02-19, 2027-01-01)")` | AD 2024-20389: 316 days remaining. |
| 5 | THINK | — | Let me get the details on each so the answer includes what each AD actually requires. |
| 6 | ACT | `lookup_ad("2025-04782")` | IDG oil contamination inspection. |
| 7 | ACT | `lookup_ad("2025-09505")` | Door 1 latch cartridge DPI. |
| 8 | ACT | `lookup_ad("2024-20389")` | Fuel tank wiring inspection. |
| 9 | ANSWER | — | Table: AD number, system, remaining time, required action. Flags 2025-04782 as most urgent (628 FH). Notes that the IDG PIREP may further accelerate the compliance timeline per AD language ("within 100 FH of temperature exceedance"). |

---

### Example 5: Cross-System Risk Assessment

**User query:** *"Give me a risk summary for N123AK. What are the top concerns?"*

**Agent flow:**

| Step | Action | Tool Call | Result Summary |
|------|--------|-----------|----------------|
| 1 | ACT | `get_aircraft_status("N123AK", section="all")` | Full picture: hours, components, ADs, PIREPs, upcoming tasks. |
| 2 | THINK | — | Multiple findings across systems. I need to rank by severity and urgency. Door 1 slides are overdue (past calendar limit). IDG has both a PIREP and an open AD. Nose gear shimmy is unresolved. APU is aging. |
| 3 | ACT | `calculate("7500 - 5890")` | IDG remaining life: 1,610 FH. |
| 4 | ACT | `calculate("30000 - 24100")` | APU remaining life: 5,900 FH. |
| 5 | ACT | `get_maintenance_schedule("AP100", within_fh=1000)` | Tasks due within 1,000 FH of current position. |
| 6 | ANSWER | — | Risk-ranked summary: **(CRITICAL)** Door 1L/1R emergency slides past 12-year calendar life — regulatory non-compliance, ground aircraft until replaced. **(HIGH)** AD 2025-04782 IDG oil sample — 628 FH remaining, PIREP suggests accelerated timeline. **(MEDIUM)** AD 2025-09505 door latch DPI — 728 FH. Nose gear shimmy — investigate at next gear functional test. **(MONITOR)** APU at 80% life — increase oil trending frequency. AD 2024-20389 fuel wiring — 316 calendar days, schedule during next heavy check. |

---

## Chunking and Indexing Strategy (Vector Store)

Even with structured tools, the agent still needs a vector store for
`search_documents` — the semantic search tool that handles free-text questions
about procedures, regulations, and system theory.

### Recommended Chunking Strategy

| Doc Type | Chunk Boundary | Chunk Size |
|----------|---------------|------------|
| FAA Handbooks | Section heading (ATA chapter or numbered section) | 500–1,000 tokens |
| Airworthiness Directives | Full document (ADs are short by nature) | 300–600 tokens |
| MPD | One row per task (table row = one chunk) | 100–200 tokens |
| Aircraft Technical Record | One section per chunk (identity, component status, task status, ADs, PIREPs) | 200–400 tokens |
| AMM Task Cards | One step group per chunk (Precautions, each numbered procedure section, Sign-off) | 200–400 tokens |

### Recommended Metadata Fields (per chunk)

```json
{
  "doc_type": "handbook | ad | mpd | aircraft_record | amm_task_card | notice",
  "source": "real | synthetic",
  "ata_chapter": ["24", "32"],
  "tail_number": "N123AK",
  "doc_number": "AP100-24-001",
  "title": "IDG Oil Level Check and Condition Inspection",
  "publication_date": "2024-08-01",
  "ad_number": "2025-04782",
  "compliance_status": "open | complied | not_applicable",
  "exclude_from_retrieval": false
}
```

### Difference from Pure RAG

In a RAG architecture, the vector store is the *only* retrieval mechanism, so
chunking quality is everything. In the agentic architecture, the vector store
is one tool among six — and it handles only the "fuzzy" questions where semantic
similarity is the right retrieval strategy (system theory, procedure background,
regulatory interpretation). Structured queries (aircraft status, AD lookups,
schedule queries, arithmetic) bypass the vector store entirely via dedicated tools.

This means the vector store can be **optimized for quality over coverage**: fewer,
higher-quality chunks from the handbooks, rather than exhaustively chunking every
page to ensure nothing is missed.

---

## Implementation Notes

### Agent Framework

The agent loop can be implemented with any ReAct-capable framework:
LangGraph, Semantic Kernel, AutoGen, or a hand-rolled loop with the OpenAI / Azure
OpenAI function-calling API. The tool schemas above are designed to map directly
to OpenAI-style function definitions.

### Streaming and Observability

For demo purposes, stream the agent's intermediate THINK steps and tool calls
to the UI. This transforms latency into a feature — the audience watches the
agent reason through the problem in real time rather than staring at a spinner.

### Guardrails

- **Max iterations:** Cap the reasoning loop at 8 tool calls per query.
- **Synthetic data disclaimer:** The agent's system prompt should note that
  Airplane-100 and N123AK are fictional. Answers must never imply these are
  real aircraft.
- **No write actions:** All tools are read-only. The agent cannot modify
  aircraft records, sign off tasks, or update AD compliance status.
- **Source citation:** The agent must cite document numbers and sections in
  every answer so users can verify independently.

### Latency Budget

| Component | Target | Notes |
|-----------|--------|-------|
| Single tool call (vector search) | < 500 ms | Embedding + ANN lookup |
| Single tool call (structured query) | < 100 ms | JSON index lookup |
| Single LLM reasoning step | 1–3 s | Depends on model and context length |
| End-to-end simple query (2 steps) | 3–5 s | Procedure lookup |
| End-to-end complex query (6–8 steps) | 12–20 s | Multi-hop investigation |

---

## Re-downloading the Corpus

If the FAA handbooks or ADs need to be re-downloaded (e.g., on a new machine),
run the included shell script:

```bash
cd /Users/corby/Projects/boeing/maintenance
bash scripts/download_ada_corpus.sh
```

This will re-fetch all 4 FAA handbooks directly from faa.gov and re-harvest
the AD corpus from the Federal Register API. It will not overwrite the
synthetic fleet pack or this file.

---

*Last updated: 2026-02-19 | ADA Demo Corpus v1.0 — Agentic Search Architecture*
