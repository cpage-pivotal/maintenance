package com.boeing.ada.schedule;

public record CheckDefinition(
        String checkType,
        String interval,
        String elapsedTime,
        String typicalDuration
) {}
