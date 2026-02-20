package com.boeing.ada.adlookup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdLookupService {

    private static final Logger log = LoggerFactory.getLogger(AdLookupService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AirworthinessDirective> adIndex = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        var resource = new ClassPathResource("data/ad_index.json");
        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode ads = root.get("ads");
            if (ads != null && ads.isArray()) {
                for (JsonNode adNode : ads) {
                    var ad = objectMapper.treeToValue(adNode, AirworthinessDirective.class);
                    adIndex.put(ad.documentNumber(), ad);
                }
            }
        }
        log.info("Loaded {} Airworthiness Directives from ad_index.json", adIndex.size());
    }

    @McpTool(description = "Retrieve the full metadata and abstract of a specific Airworthiness Directive "
            + "by its AD number. Use when you already know the AD number from an aircraft record or "
            + "task card reference. Returns title, abstract, effective date, ATA chapter, and Federal Register URL.")
    public Object lookupAd(
            @McpToolParam(description = "The AD document number, e.g. '2025-04782'", required = true) String adNumber) {

        var ad = adIndex.get(adNumber);
        if (ad == null) {
            return Map.of(
                    "error", "AD not found: " + adNumber,
                    "available_count", adIndex.size(),
                    "hint", "Use search_documents to find ADs by topic if you don't have an exact number."
            );
        }
        return ad;
    }

    @McpTool(description = "List all available Airworthiness Directives, optionally filtered by ATA chapter. "
            + "Returns AD numbers and titles for browsing.")
    public List<Map<String, String>> listAds(
            @McpToolParam(description = "ATA chapter to filter by, e.g. 'ATA-24' or '24'") String ataChapter) {

        return adIndex.values().stream()
                .filter(ad -> {
                    if (ataChapter == null || ataChapter.isBlank()) return true;
                    String normalized = ataChapter.startsWith("ATA-") ? ataChapter : "ATA-" + ataChapter;
                    return normalized.equals(ad.ataChapter());
                })
                .map(ad -> Map.of(
                        "ad_number", ad.documentNumber(),
                        "title", ad.title(),
                        "ata_chapter", ad.ataChapter(),
                        "effective_on", ad.effectiveOn() != null ? ad.effectiveOn() : ""
                ))
                .toList();
    }
}
