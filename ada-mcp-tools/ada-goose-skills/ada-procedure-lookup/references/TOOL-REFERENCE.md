# ADA MCP Tool Reference

## ada-aircraft-status server

### getAircraftStatus
- **tailNumber** (required): Aircraft registration, e.g. "N123AK"
- **section** (optional): "summary", "components", "open_ads", "pireps", "upcoming_tasks", or "all"
- **Returns**: Structured JSON with aircraft identity, component life status, scheduled tasks, AD compliance, and PIREPs

## ada-ad-lookup server

### lookupAd
- **adNumber** (required): AD document number, e.g. "2025-04782"
- **Returns**: Full AD metadata including title, abstract, effective date, ATA chapter, and Federal Register URL

### listAds
- **ataChapter** (optional): Filter by ATA chapter, e.g. "ATA-24" or "24"
- **Returns**: List of AD numbers, titles, ATA chapters, and effective dates

## ada-task-card server

### getTaskCard
- **taskId** (optional): Specific task ID, e.g. "AP100-24-001"
- **ataCode** (optional): ATA section code, e.g. "24-11-01" or chapter "24"
- **tailNumber** (optional): Aircraft registration for tail-specific notes
- **Returns**: Task card with title, ATA code, zone, related ADs, tail-specific notes, and full procedure text

## ada-maintenance-schedule server

### getMaintenanceSchedule
- **aircraftType** (required): "AP100" or "Airplane-100"
- **checkType** (optional): "A", "B", "C", "D", or "all"
- **ataChapter** (optional): ATA chapter number, e.g. "24"
- **withinFh** (optional): Only tasks due within this many flight hours
- **Returns**: Check definitions, scheduled tasks (filtered), and component life limits

## ada-document-search server

### searchDocuments
- **query** (required): Natural language search query
- **docType** (optional): "handbook", "ad", "mpd", "aircraft_record", "amm_task_card"
- **ataChapter** (optional): ATA chapter number to narrow scope
- **topK** (optional): Number of chunks to return (default 5, max 20)
- **Returns**: Ranked document chunks with text, title, doc type, ATA chapter, and relevance score

## Available Task Cards (AP100)

| Task ID | ATA | Description |
|---------|-----|-------------|
| AP100-24-001 | 24-11-01 | IDG Oil Level Check and Condition Inspection |
| AP100-32-004 | 32-31-01 | Landing Gear Retraction and Extension Functional Test |
| AP100-49-001 | 49-11-01 | APU Oil Level and Filter Inspection |
| AP100-52-001 | 52-11-01 | Door Latch Mechanism Inspection and Lubrication |
