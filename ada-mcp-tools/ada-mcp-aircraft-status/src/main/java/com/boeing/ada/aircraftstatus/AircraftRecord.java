package com.boeing.ada.aircraftstatus;

import java.util.List;

public record AircraftRecord(
        AircraftIdentity identity,
        List<ComponentStatus> components,
        List<ScheduledTaskStatus> upcomingTasks,
        List<AdComplianceEntry> openAds,
        List<Pirep> pireps
) {

    public record AircraftIdentity(
            String registration,
            String model,
            String serialNumber,
            int lineNumber,
            int yearOfManufacture,
            String totalFlightHours,
            String totalFlightCycles,
            String lastACheck,
            String nextACheckDue,
            String lastCCheck,
            String nextCCheckDue,
            String engine1,
            String engine2,
            String apu
    ) {}

    public record ComponentStatus(
            String component,
            String partNumber,
            String serialNumber,
            String installed,
            String tsi,
            String limit,
            String remaining,
            String status
    ) {}

    public record ScheduledTaskStatus(
            String taskId,
            String description,
            String dueFh,
            String remaining,
            boolean overdue
    ) {}

    public record AdComplianceEntry(
            String adNumber,
            String title,
            String status,
            String due
    ) {}

    public record Pirep(
            String date,
            String enteredBy,
            String ataChapter,
            String note
    ) {}
}
