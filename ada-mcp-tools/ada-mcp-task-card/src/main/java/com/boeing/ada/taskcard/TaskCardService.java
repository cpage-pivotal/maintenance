package com.boeing.ada.taskcard;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskCardService {

    private final TaskCardParser parser;

    public TaskCardService(TaskCardParser parser) {
        this.parser = parser;
    }

    @McpTool(description = "Retrieve a maintenance task card (AMM procedure) by ATA chapter code or task ID. "
            + "Returns the full step-by-step procedure with precautions, tools required, referenced ADs, "
            + "and any tail-specific notes. Provide either taskId or ataCode.")
    public Object getTaskCard(
            @McpToolParam(description = "Specific task ID, e.g. 'AP100-24-001'") String taskId,
            @McpToolParam(description = "ATA section code, e.g. '24-11-01' or ATA chapter '24'. Returns all matching task cards.") String ataCode,
            @McpToolParam(description = "Aircraft registration. If provided, tail-specific notes are highlighted.") String tailNumber) {

        List<TaskCard> results;

        if (taskId != null && !taskId.isBlank()) {
            results = parser.findByTaskId(taskId);
        } else if (ataCode != null && !ataCode.isBlank()) {
            results = parser.findByAtaCode(ataCode);
        } else {
            results = parser.getAllTaskCards();
        }

        if (results.isEmpty()) {
            return Map.of(
                    "error", "No task cards found",
                    "searched_taskId", taskId != null ? taskId : "",
                    "searched_ataCode", ataCode != null ? ataCode : "",
                    "available_task_ids", parser.getAllTaskCards().stream().map(TaskCard::taskId).toList()
            );
        }

        // Return full text for direct readability by the agent
        return results.stream()
                .map(tc -> Map.of(
                        "taskId", tc.taskId(),
                        "title", tc.title(),
                        "ataCode", tc.ataCode(),
                        "revision", tc.revision(),
                        "zone", tc.zone(),
                        "relatedAds", tc.relatedAds(),
                        "tailSpecificNotes", tc.tailSpecificNotes(),
                        "fullText", tc.fullText()
                ))
                .toList();
    }
}
