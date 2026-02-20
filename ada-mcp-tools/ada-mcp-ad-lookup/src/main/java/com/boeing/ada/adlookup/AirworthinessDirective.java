package com.boeing.ada.adlookup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AirworthinessDirective(
        @JsonProperty("document_number") String documentNumber,
        String title,
        @JsonProperty("abstract") String adAbstract,
        @JsonProperty("publication_date") String publicationDate,
        @JsonProperty("effective_on") String effectiveOn,
        @JsonProperty("ata_chapter") String ataChapter,
        String system,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("pdf_url") String pdfUrl,
        @JsonProperty("local_path") String localPath
) {}
