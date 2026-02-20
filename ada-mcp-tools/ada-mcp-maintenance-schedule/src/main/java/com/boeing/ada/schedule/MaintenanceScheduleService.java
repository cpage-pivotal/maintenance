package com.boeing.ada.schedule;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MaintenanceScheduleService {

    private final MpdParser parser;

    public MaintenanceScheduleService(MpdParser parser) {
        this.parser = parser;
    }

    @McpTool(description = "Query the Airplane-100 maintenance planning document for scheduled tasks, "
            + "check intervals, and component life limits. Can filter by check type (A/B/C/D), "
            + "ATA chapter, or return component hard-time life limits.")
    public Object getMaintenanceSchedule(
            @McpToolParam(description = "Aircraft type designator, e.g. 'AP100'", required = true) String aircraftType,
            @McpToolParam(description = "Filter by maintenance check level: A, B, C, D, or all") String checkType,
            @McpToolParam(description = "Filter tasks by ATA chapter number, e.g. '24', '32'") String ataChapter,
            @McpToolParam(description = "Only return tasks due within this many flight hours from current position") Integer withinFh) {

        if (!aircraftType.equalsIgnoreCase("AP100") && !aircraftType.toLowerCase().contains("airplane-100")) {
            return Map.of("error", "Unknown aircraft type: " + aircraftType,
                    "supported", "AP100 (Airplane-100)");
        }

        List<ScheduledTask> tasks = parser.getTasks().stream()
                .filter(t -> {
                    if (checkType != null && !checkType.isBlank() && !"all".equalsIgnoreCase(checkType)) {
                        return t.checkType().equalsIgnoreCase(checkType);
                    }
                    return true;
                })
                .filter(t -> {
                    if (ataChapter != null && !ataChapter.isBlank()) {
                        return t.ataChapter().equals(ataChapter);
                    }
                    return true;
                })
                .toList();

        return Map.of(
                "aircraftType", "Airplane-100",
                "checkDefinitions", parser.getCheckDefinitions(),
                "tasks", tasks,
                "taskCount", tasks.size(),
                "componentLifeLimits", parser.getLifeLimits()
        );
    }
}
