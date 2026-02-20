package com.boeing.ada.taskcard;

import java.util.List;

public record TaskCard(
        String taskId,
        String title,
        String ataCode,
        String revision,
        String date,
        String zone,
        String reasonForJob,
        List<String> relatedAds,
        List<String> toolsRequired,
        List<String> precautions,
        List<ProcedureStep> steps,
        List<String> tailSpecificNotes,
        String signOff,
        String fullText
) {

    public record ProcedureStep(
            String stepNumber,
            String title,
            String content
    ) {}
}
