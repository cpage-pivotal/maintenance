package com.boeing.ada.aircraftstatus;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AircraftStatusService {

    private final AircraftRecordParser parser;

    public AircraftStatusService(AircraftRecordParser parser) {
        this.parser = parser;
    }

    @McpTool(description = "Retrieve the current operational status of a specific aircraft. "
            + "Returns structured data: flight hours, flight cycles, component time-since-installation, "
            + "open PIREPs, open Airworthiness Directives, and upcoming scheduled tasks. "
            + "Use this as the starting point for any tail-number-specific investigation.")
    public Object getAircraftStatus(
            @McpToolParam(description = "Aircraft registration, e.g. 'N123AK'", required = true) String tailNumber,
            @McpToolParam(description = "Return only a specific section: summary, components, open_ads, pireps, upcoming_tasks, or all") String section) {

        var record = parser.getRecord(tailNumber)
                .orElseThrow(() -> new IllegalArgumentException("No aircraft record found for tail number: " + tailNumber));

        String sec = (section != null && !section.isBlank()) ? section.toLowerCase() : "all";

        return switch (sec) {
            case "summary" -> record.identity();
            case "components" -> record.components();
            case "open_ads" -> record.openAds();
            case "pireps" -> record.pireps();
            case "upcoming_tasks" -> record.upcomingTasks();
            case "all" -> record;
            default -> Map.of("error", "Unknown section: " + section,
                    "valid_sections", "summary, components, open_ads, pireps, upcoming_tasks, all");
        };
    }
}
