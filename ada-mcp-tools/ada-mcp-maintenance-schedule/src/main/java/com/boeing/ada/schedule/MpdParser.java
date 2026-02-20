package com.boeing.ada.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class MpdParser {

    private static final Logger log = LoggerFactory.getLogger(MpdParser.class);

    private final List<ScheduledTask> tasks = new ArrayList<>();
    private final List<ComponentLifeLimit> lifeLimits = new ArrayList<>();
    private final List<CheckDefinition> checkDefinitions = new ArrayList<>();

    @PostConstruct
    public void init() throws IOException {
        var resource = new ClassPathResource("data/AP100-MPD-001_Maintenance_Planning_Document.txt");
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        parseAll(content);
        log.info("Loaded MPD: {} tasks, {} life limits, {} check definitions",
                tasks.size(), lifeLimits.size(), checkDefinitions.size());
    }

    public List<ScheduledTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public List<ComponentLifeLimit> getLifeLimits() {
        return Collections.unmodifiableList(lifeLimits);
    }

    public List<CheckDefinition> getCheckDefinitions() {
        return Collections.unmodifiableList(checkDefinitions);
    }

    private void parseAll(String content) {
        String[] lines = content.split("\n");
        int currentSection = 0;

        for (String line : lines) {
            if (line.contains("SECTION 2")) currentSection = 2;
            else if (line.contains("SECTION 3")) currentSection = 3;
            else if (line.contains("SECTION 4")) currentSection = 4;
            else if (line.contains("SECTION 5")) currentSection = 5;

            switch (currentSection) {
                case 2 -> parseCheckDefinitionLine(line);
                case 3 -> parseTaskLine(line);
                case 4 -> parseLifeLimitLine(line);
            }
        }
    }

    private void parseCheckDefinitionLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("A-Check") || trimmed.startsWith("B-Check")
                || trimmed.startsWith("C-Check") || trimmed.startsWith("D-Check")) {
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 4) {
                String checkType = parts[0].replace("-Check", "").trim();
                checkDefinitions.add(new CheckDefinition(checkType, parts[1].trim(), parts[2].trim(), parts[3].trim()));
            }
        }
    }

    private void parseTaskLine(String line) {
        String trimmed = line.trim();
        // Task lines start with a 2-digit ATA chapter number
        if (trimmed.matches("^\\d{2}\\s{2,}.*")) {
            String[] parts = trimmed.split("\\s{2,}");
            if (parts.length >= 6) {
                tasks.add(new ScheduledTask(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        parts[3].trim(),
                        parts[4].trim(),
                        parts[5].trim()
                ));
            }
        }
    }

    private void parseLifeLimitLine(String line) {
        String trimmed = line.trim();
        // Skip headers and separators
        if (trimmed.startsWith("COMPONENT") || trimmed.startsWith("---") || trimmed.isBlank()
                || trimmed.contains("━") || trimmed.contains("SECTION") || trimmed.startsWith("FC")) {
            return;
        }
        String[] parts = trimmed.split("\\s{2,}");
        if (parts.length >= 4) {
            lifeLimits.add(new ComponentLifeLimit(
                    parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim()
            ));
        }
    }
}
