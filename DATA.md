# ADA Demo — RAG Corpus Data Guide

This folder contains the full document corpus for the Aircraft Data Analyzer (ADA)
GenAI demo. It is organized into three tiers: real FAA public-domain handbooks,
real Airworthiness Directives harvested from the Federal Register API, and a
synthetic fleet pack for the fictional "Airplane-100" aircraft.

---

## Directory Structure

```
maintenance/
├── DATA.md                          ← this file
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

## Tier 1 — FAA AMT Handbooks (`faa_handbooks/`)

These are official FAA publications, freely available from faa.gov. They are in the
public domain and carry no licensing restrictions. Together they form the general
knowledge backbone of the RAG corpus — the agent draws on these for standard
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

Key topics for RAG: maintenance record requirements, material specifications,
hardware standards, torque values, non-destructive testing methods, welding,
aircraft weight and balance principles.

### FAA-H-8083-31B — Aviation Maintenance Technician Handbook: Airframe
**File:** `FAA-H-8083-31B_Airframe.pdf` (~107 MB)

The primary reference for airframe systems maintenance. Covers aircraft
structures (fuselage, wings, empennage, pressurized structures), aircraft
covering materials, flight control systems (primary and secondary controls,
trim, autopilot interfaces), landing gear systems, hydraulic systems,
pneumatic systems, fuel systems, environmental control and pressurization,
ice and rain protection, and cabin doors and windows.

This is the most directly relevant handbook for the ADA demo scenarios —
particularly the IDG/electrical, landing gear, hydraulics, door, and
pressurization scenarios. It also contains the zone numbering conventions
(100-series fuselage, 400-series engine pylon, 700-800 series gear/door)
referenced throughout the synthetic task cards.

Key topics for RAG: structural repair, flight control rigging, landing gear
operation, hydraulic system troubleshooting, pressurization system components,
door latch mechanisms, ATA zone references.

### FAA-H-8083-32B — Aviation Maintenance Technician Handbook: Powerplant
**File:** `FAA-H-8083-32B_Powerplant.pdf` (~205 MB)

The reference for all engine and APU systems. Covers reciprocating engines
(for context) and turbine engines in depth: turbofan construction, compressor
and turbine sections, combustion, engine mounts, fuel systems, lubrication
systems, ignition systems, engine instruments, and engine run procedures.
Also covers APU systems, propellers, and engine fire protection.

Relevant for ADA demo scenarios involving IDG temperature anomalies (the IDG
is mechanically driven from the engine accessory gearbox), engine borescope
tasks, APU oil condition monitoring, and any engine-related AD interpretation.

Key topics for RAG: turbofan engine systems, oil system specifications,
engine run procedures, APU operation, fuel control, fire protection,
accessory gearbox (where IDG mounts).

### AC 43.13-1B — Acceptable Methods, Techniques, and Practices: Aircraft Inspection and Repair
**File:** `AC_43-13-1B_Inspection_Repair.pdf` (~21 MB)

Known as the "repair bible." This Advisory Circular is the practitioner's
reference for acceptable inspection and repair methods across all aircraft
types. It provides specific torque tables, material standards, fastener
specifications, NDT procedures, welding standards, and structural repair
criteria that mechanics cite when an AMM says "repair per standard practices."

Unlike the handbooks (which explain concepts), AC 43.13-1B provides the
specific numeric values mechanics use at the job: exact torque specs, material
grades, acceptable crack limits, paint standards, and so on. It also includes
Change 1 content.

Key topics for RAG: torque specifications, structural repair limits, fastener
standards, safety wire specifications, dye penetrant procedures, corrosion
treatment, tube/pipe bending, composite repair.

---

## Tier 2 — Airworthiness Directives (`airworthiness_directives/`)

55 real ADs harvested programmatically from the Federal Register API
(`federalregister.gov/api/v1/documents`). All are final rules (type="Rule"),
not proposed rules. Date range spans 1998–2025, providing historical depth.

ADs are mandatory safety notices — legally enforceable FAA orders that require
operators to inspect or fix specific defects within defined timeframes. They are
ideal RAG documents: short, precisely structured, with clear compliance language
and technical part references.

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

Use this index to attach metadata at ingestion time rather than re-parsing PDFs.

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
Disclaimer document. Should be included in RAG ingestion metadata but filtered
from retrieval results (tag as `doc_type: notice, exclude_from_retrieval: true`).

### `synthetic_fleet_pack_index.json`
Pre-built metadata index for all synthetic documents. Same schema as `ad_index.json`.
Use this at ingestion time to attach ATA chapter tags, doc type, key topics, and
tail-number notes to each chunk without re-parsing documents.

### `AP100-MPD-001_Maintenance_Planning_Document.txt`
**Type:** Maintenance Planning Document (MPD)
**ATA:** All chapters (21, 24, 26, 27, 28, 29, 32, 34, 35, 49, 52, 72)

The master task schedule for the Airplane-100. Defines check intervals (A/B/C/D),
all 55+ scheduled task IDs with their FH/FC/calendar intervals, component hard-time
life limits, and AD compliance tracking requirements.

The agent uses this document to answer: *"What's due at the next C-check?"*,
*"How often does the IDG get replaced?"*, *"What are the life limits on the
main gear axles?"*

### `ATR-N123AK_Aircraft_Technical_Record.txt`
**Type:** Aircraft Technical Record
**Aircraft:** Airplane-100-200ER, Tail N123AK, S/N AP100-2847
**Current hours:** 24,872 FH / 18,441 FC

The single most important document for demo scenarios. Contains:
- Complete component status table with time-since-installation vs. life limits
- Upcoming scheduled tasks within 1,000 FH
- Open AD compliance table (3 open ADs)
- Maintenance PIREPs (IDG-RIGHT temperature anomaly, nose gear shimmy, Door 2L force)

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

Drives the demo scenario: *"The IDG is running 15°C hot — what does that mean
and what do we do?"* → agent connects PIREP to open AD to this task card.

### `AMM-AP100-32-004_Landing_Gear_Functional_Test_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 32-31-01 (Landing Gear — Retraction Test)

Full retraction/extension functional test procedure. Covers: aircraft jacking (3-point),
hydraulic ground cart connection, N123AK-specific shimmy dampener inspection
(referencing nose gear PIREP from 2026-01-22), 3-cycle gear retraction test with
timing requirements, position indication system check, and hydraulic leak inspection.

Drives the demo scenario: *"Walk me through the landing gear check before the next
B-check"* → agent produces the procedure with tail-specific PIREP callouts.

### `AMM-AP100-49-001_APU_Oil_Filter_Inspection_Task_Card.txt`
**Type:** AMM Task Card
**ATA:** 49-11-01 (APU — Oil and Filter)

APU oil level and filter replacement procedure. Covers: APU cooldown and lockout,
sight glass level check, replenishment, filter removal and metallic debris inspection
(with hold thresholds), spectrometric oil sampling with trending guidance, and
post-maintenance APU ground run and leak check.

N123AK-specific note: APU is at 24,100 FH against 30,000 FH life limit. Oil trending
is flagged as especially important in the final third of service life. Fe threshold
for borescope escalation is called out explicitly.

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

---

## RAG Ingestion Guidance

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

### ATA Chapter Filter

When the agent identifies a system from user query (e.g., "IDG" → ATA-24,
"landing gear" → ATA-32), filter retrieval to matching `ata_chapter` values
before semantic search. This prevents irrelevant cross-system noise and keeps
context windows tight.

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
synthetic fleet pack or this DATA.md file.

---

*Last updated: 2026-02-19 | ADA Demo Corpus v1.0*
