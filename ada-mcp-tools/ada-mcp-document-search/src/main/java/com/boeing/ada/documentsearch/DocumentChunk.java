package com.boeing.ada.documentsearch;

public record DocumentChunk(
        String text,
        String docTitle,
        String docType,
        String ataChapter,
        String source,
        double score
) {}
