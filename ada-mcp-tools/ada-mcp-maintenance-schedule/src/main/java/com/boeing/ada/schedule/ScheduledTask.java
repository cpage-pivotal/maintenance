package com.boeing.ada.schedule;

public record ScheduledTask(
        String ataChapter,
        String system,
        String taskId,
        String description,
        String interval,
        String checkType
) {}
