package com.boeing.ada.schedule;

public record ComponentLifeLimit(
        String component,
        String partNumber,
        String lifeLimit,
        String ataChapter
) {}
