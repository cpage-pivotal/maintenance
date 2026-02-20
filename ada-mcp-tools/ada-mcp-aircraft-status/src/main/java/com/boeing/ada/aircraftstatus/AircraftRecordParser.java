package com.boeing.ada.aircraftstatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AircraftRecordParser {

    private static final Logger log = LoggerFactory.getLogger(AircraftRecordParser.class);

    private final Map<String, AircraftRecord> records = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        var resource = new ClassPathResource("data/ATR-N123AK_Aircraft_Technical_Record.txt");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        AircraftRecord record = parse(content);
        records.put(record.identity().registration(), record);
        log.info("Loaded aircraft record for {}", record.identity().registration());
    }

    public Optional<AircraftRecord> getRecord(String tailNumber) {
        return Optional.ofNullable(records.get(tailNumber));
    }

    private AircraftRecord parse(String content) {
        Map<Integer, String> sections = splitSections(content);

        var identity = parseIdentity(sections.getOrDefault(1, ""));
        var components = parseComponents(sections.getOrDefault(2, ""));
        var tasks = parseTasks(sections.getOrDefault(3, ""));
        var ads = parseAds(sections.getOrDefault(4, ""));
        var pireps = parsePireps(sections.getOrDefault(5, ""));

        return new AircraftRecord(identity, components, tasks, ads, pireps);
    }

    private Map<Integer, String> splitSections(String content) {
        Map<Integer, String> sections = new HashMap<>();
        Pattern sectionPattern = Pattern.compile("SECTION (\\d+) —");
        Matcher matcher = sectionPattern.matcher(content);

        List<int[]> sectionBounds = new ArrayList<>();
        while (matcher.find()) {
            sectionBounds.add(new int[]{Integer.parseInt(matcher.group(1)), matcher.start()});
        }

        for (int i = 0; i < sectionBounds.size(); i++) {
            int sectionNum = sectionBounds.get(i)[0];
            int start = sectionBounds.get(i)[1];
            int end = (i + 1 < sectionBounds.size()) ? sectionBounds.get(i + 1)[1] : content.length();
            sections.put(sectionNum, content.substring(start, end));
        }
        return sections;
    }

    private AircraftRecord.AircraftIdentity parseIdentity(String section) {
        return new AircraftRecord.AircraftIdentity(
                extractField(section, "Registration"),
                extractField(section, "Model"),
                extractField(section, "Manufacturer S/N"),
                parseIntSafe(extractField(section, "Line Number")),
                parseIntSafe(extractField(section, "Year of Manufacture")),
                extractField(section, "Total Flight Hours"),
                extractField(section, "Total Flight Cycles"),
                extractField(section, "Date of Last A-Check"),
                extractField(section, "Next A-Check Due"),
                extractField(section, "Last C-Check"),
                extractField(section, "Next C-Check Due"),
                extractLineContaining(section, "Engine 1"),
                extractLineContaining(section, "Engine 2"),
                extractLineContaining(section, "APU")
        );
    }

    private List<AircraftRecord.ComponentStatus> parseComponents(String section) {
        List<AircraftRecord.ComponentStatus> components = new ArrayList<>();
        String[] lines = section.split("\n");

        for (String line : lines) {
            if (line.startsWith("---") || line.isBlank() || line.contains("COMPONENT")
                    || line.contains("SECTION") || line.contains("━") || line.contains("TSI =")
                    || line.contains("As of")) {
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Fixed-width columns: component(36) P/N(16) S/N(14) installed(12) TSI(10) limit(9) remaining(12) status
            if (trimmed.length() > 80 && !trimmed.startsWith("COMPONENT")) {
                String[] parts = splitFixedWidth(trimmed);
                if (parts.length >= 8) {
                    components.add(new AircraftRecord.ComponentStatus(
                            parts[0].trim(), parts[1].trim(), parts[2].trim(),
                            parts[3].trim(), parts[4].trim(), parts[5].trim(),
                            parts[6].trim(), parts[7].trim()
                    ));
                }
            }
        }
        return components;
    }

    private String[] splitFixedWidth(String line) {
        // Parse the fixed-width table by splitting on 2+ spaces
        return line.split("\\s{2,}");
    }

    private List<AircraftRecord.ScheduledTaskStatus> parseTasks(String section) {
        List<AircraftRecord.ScheduledTaskStatus> tasks = new ArrayList<>();
        String[] lines = section.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("AP100-")) {
                String[] parts = trimmed.split("\\s{2,}");
                if (parts.length >= 5) {
                    tasks.add(new AircraftRecord.ScheduledTaskStatus(
                            parts[0].trim(),
                            parts[1].trim(),
                            parts[2].trim(),
                            parts[3].trim(),
                            "Yes".equalsIgnoreCase(parts[4].trim())
                    ));
                }
            }
        }
        return tasks;
    }

    private List<AircraftRecord.AdComplianceEntry> parseAds(String section) {
        List<AircraftRecord.AdComplianceEntry> ads = new ArrayList<>();
        String[] lines = section.split("\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d{4}-\\d+\\s+.*")) {
                String[] parts = trimmed.split("\\s{2,}");
                if (parts.length >= 4) {
                    ads.add(new AircraftRecord.AdComplianceEntry(
                            parts[0].trim(), parts[1].trim(),
                            parts[2].trim(), parts[3].trim()
                    ));
                }
            }
        }
        return ads;
    }

    private List<AircraftRecord.Pirep> parsePireps(String section) {
        List<AircraftRecord.Pirep> pireps = new ArrayList<>();
        String[] lines = section.split("\n");
        StringBuilder currentNote = null;
        String currentDate = null, currentEnteredBy = null, currentAta = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("DATE") || trimmed.startsWith("---") || trimmed.contains("━")
                    || trimmed.contains("SECTION") || trimmed.contains("END OF DOCUMENT") || trimmed.isEmpty()) {
                continue;
            }

            // New PIREP entry starts with a date
            if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2}\\s+.*")) {
                if (currentNote != null) {
                    pireps.add(new AircraftRecord.Pirep(currentDate, currentEnteredBy, currentAta, currentNote.toString().trim()));
                }
                String[] parts = trimmed.split("\\s{2,}");
                currentDate = parts.length > 0 ? parts[0].trim() : "";
                currentEnteredBy = parts.length > 1 ? parts[1].trim() : "";
                currentAta = parts.length > 2 ? parts[2].trim() : "";
                currentNote = new StringBuilder(parts.length > 3 ? parts[3].trim() : "");
            } else if (currentNote != null) {
                // Continuation line
                currentNote.append(" ").append(trimmed);
            }
        }
        if (currentNote != null) {
            pireps.add(new AircraftRecord.Pirep(currentDate, currentEnteredBy, currentAta, currentNote.toString().trim()));
        }

        return pireps;
    }

    private String extractField(String section, String fieldName) {
        Pattern pattern = Pattern.compile(Pattern.quote(fieldName) + ":\\s*(.+)");
        Matcher matcher = pattern.matcher(section);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractLineContaining(String section, String keyword) {
        for (String line : section.split("\n")) {
            if (line.contains(keyword)) {
                return line.trim();
            }
        }
        return "";
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
