# ADA Agent Flow Examples

These are worked examples showing the multi-hop investigation pattern.

## Example 1: IDG Temperature Anomaly

**User:** "The crew reported the right IDG is running 15°C above normal on N123AK. What should we do?"

**Flow:**

1. `getAircraftStatus("N123AK", "pireps")` — Confirms PIREP 2026-02-08: IDG-RIGHT +15°C above baseline.
2. `getAircraftStatus("N123AK", "open_ads")` — AD 2025-04782 is OPEN, due before 25,500 FH.
3. `lookupAd("2025-04782")` — Mandates spectrometric oil sampling for IDG units with temperature anomalies.
4. `getTaskCard(taskId: "AP100-24-001", tailNumber: "N123AK")` — Full oil inspection procedure with AD compliance steps. Thresholds: Fe ≤ 50 ppm, Cu ≤ 30 ppm, Sn ≤ 15 ppm.
5. Calculate: 25,500 - 24,872 = **628 FH remaining** until AD deadline. 7,500 - 5,890 = **1,610 FH remaining** on IDG life.
6. `searchDocuments("IDG temperature increase causes", docType: "handbook")` — Background on bearing wear and oil degradation indicators.
7. **Synthesize**: PIREP aligns with AD scenario. Oil sample required. Procedure is AP100-24-001 Step 6.5. IDG at 78% life — consider early replacement.

## Example 2: Pre-B-Check Planning

**User:** "What do we need to worry about before the next B-check on N123AK?"

**Flow:**

1. `getAircraftStatus("N123AK", "all")` — 24,872 FH / 18,441 FC. Three open ADs. Three PIREPs. Door 1 slides overdue.
2. `getMaintenanceSchedule("AP100", checkType: "B")` — B-check tasks across ATA 24, 27, 29, 32, 49, 52.
3. `getAircraftStatus("N123AK", "upcoming_tasks")` — Tasks due within 1,000 FH.
4. `lookupAd("2025-04782")` — IDG oil, 628 FH remaining.
5. `lookupAd("2025-09505")` — Door 1 latch DPI, 728 FH remaining.
6. Calculate deadlines for each open AD.
7. **Synthesize**: Prioritized list: (1) Door 1 slides OVERDUE, (2) AD 2025-04782 in 628 FH, (3) AD 2025-09505 in 728 FH, (4) Nose gear shimmy investigation, (5) APU oil trending.

## Example 3: Specific Procedure Lookup

**User:** "Walk me through the APU oil filter inspection."

**Flow:**

1. `getTaskCard(ataCode: "49-11-01")` — Full APU oil filter procedure.
2. `getAircraftStatus("N123AK", "components")` — APU at 24,100 FH / 30,000 FH limit (80%).
3. **Synthesize**: Deliver procedure steps, highlight that APU is in final third of service life so Fe trending threshold (>40 ppm) is critical.

## Example 4: AD Compliance Deadlines

**User:** "How much time do we have left on the open ADs for N123AK?"

**Flow:**

1. `getAircraftStatus("N123AK", "open_ads")` — Three open ADs with due dates.
2. `lookupAd("2025-04782")` — IDG oil contamination.
3. `lookupAd("2025-09505")` — Door 1 latch replacement.
4. `lookupAd("2024-20389")` — Fuel tank wiring.
5. Calculate: 25,500 - 24,872 = 628 FH; 25,600 - 24,872 = 728 FH; days until 2027-01-01 = ~316 days.
6. **Synthesize**: Table of AD numbers, systems, remaining time, required actions. Flag 2025-04782 as most urgent.

## Example 5: Cross-System Risk Assessment

**User:** "Give me a risk summary for N123AK."

**Flow:**

1. `getAircraftStatus("N123AK", "all")` — Full picture.
2. Calculate remaining life for key components (IDG: 1,610 FH, APU: 5,900 FH).
3. `getMaintenanceSchedule("AP100", withinFh: 1000)` — Tasks due soon.
4. **Synthesize**: Risk-ranked summary:
   - CRITICAL: Door 1 slides past calendar life
   - HIGH: AD 2025-04782 IDG (628 FH), AD 2025-09505 door latch (728 FH)
   - MEDIUM: Nose gear shimmy, fuel tank wiring AD
   - MONITOR: APU at 80% life, IDG-RIGHT at 78% life
