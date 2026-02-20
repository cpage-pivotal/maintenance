package com.boeing.ada.taskcard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TaskCardParser {

    private static final Logger log = LoggerFactory.getLogger(TaskCardParser.class);

    private final List<TaskCard> taskCards = new ArrayList<>();

    @PostConstruct
    public void init() throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:data/AMM-*.txt");

        for (var resource : resources) {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            TaskCard card = parse(content);
            taskCards.add(card);
            log.info("Loaded task card: {} ({})", card.taskId(), card.title());
        }
        log.info("Loaded {} task cards total", taskCards.size());
    }

    public List<TaskCard> getAllTaskCards() {
        return Collections.unmodifiableList(taskCards);
    }

    public List<TaskCard> findByTaskId(String taskId) {
        return taskCards.stream()
                .filter(tc -> tc.taskId().equalsIgnoreCase(taskId))
                .toList();
    }

    public List<TaskCard> findByAtaCode(String ataCode) {
        return taskCards.stream()
                .filter(tc -> tc.ataCode().startsWith(ataCode) || matchesAtaChapter(tc.ataCode(), ataCode))
                .toList();
    }

    private boolean matchesAtaChapter(String fullAtaCode, String searchCode) {
        String chapter = fullAtaCode.split("-")[0];
        return chapter.equals(searchCode);
    }

    private TaskCard parse(String content) {
        String taskId = extractPattern(content, "Task ID:\\s*(AP100-[\\w-]+)");
        String ataCode = extractPattern(content, "ATA:\\s*([\\d-]+)");
        String revision = extractPattern(content, "Rev:\\s*(\\d+)");
        String date = extractPattern(content, "Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
        String zone = extractPattern(content, "Zone:\\s*(.+?)\\n");

        // Title is on the second line (TASK CARD: ...)
        String title = extractPattern(content, "TASK CARD:\\s*(.+?)\\n");
        if (title.isEmpty()) {
            title = extractPattern(content, "APU TASK CARD:\\s*(.+?)\\n");
        }

        String reasonForJob = extractSection(content, "1. REASON FOR THE JOB", "2.");
        List<String> relatedAds = extractAdReferences(content);
        List<String> toolsRequired = extractBulletSection(content, "TOOLS AND EQUIPMENT");
        List<String> precautions = extractBulletSection(content, "PRECAUTIONS");
        List<TaskCard.ProcedureStep> steps = extractProcedureSteps(content);
        List<String> tailNotes = extractTailSpecificNotes(content);
        String signOff = extractSection(content, "SIGN-OFF", "━");

        return new TaskCard(taskId, title, ataCode, revision, date, zone,
                reasonForJob, relatedAds, toolsRequired, precautions, steps,
                tailNotes, signOff, content);
    }

    private String extractPattern(String content, String regex) {
        Matcher m = Pattern.compile(regex).matcher(content);
        return m.find() ? m.group(1).trim() : "";
    }

    private String extractSection(String content, String startMarker, String endMarker) {
        int start = content.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = content.indexOf(endMarker, start);
        if (end < 0) end = content.length();
        return content.substring(start, end).trim();
    }

    private List<String> extractAdReferences(String content) {
        List<String> ads = new ArrayList<>();
        Pattern p = Pattern.compile("AD\\s+(\\d{4}-\\d+)\\s*—\\s*(.+?)(?:\\n|$)");
        Matcher m = p.matcher(content);
        while (m.find()) {
            ads.add("AD " + m.group(1) + " — " + m.group(2).trim());
        }
        return ads;
    }

    private List<String> extractBulletSection(String content, String sectionTitle) {
        List<String> items = new ArrayList<>();
        String section = extractSection(content, sectionTitle, "\n\n");
        if (section.isBlank()) {
            int idx = content.indexOf(sectionTitle);
            if (idx >= 0) {
                section = extractSection(content, sectionTitle, "━");
            }
        }
        for (String line : section.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("WARNING") || trimmed.startsWith("CAUTION") || trimmed.startsWith("NOTE")) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private List<TaskCard.ProcedureStep> extractProcedureSteps(String content) {
        List<TaskCard.ProcedureStep> steps = new ArrayList<>();

        // Find PROCEDURE section (numbered like 6. PROCEDURE or 4. PROCEDURE)
        Pattern procedureStart = Pattern.compile("\\d+\\.\\s+PROCEDURE");
        Matcher startMatcher = procedureStart.matcher(content);
        if (!startMatcher.find()) return steps;

        int procStart = startMatcher.start();
        // Find SIGN-OFF section as the end boundary
        int procEnd = content.indexOf("SIGN-OFF", procStart);
        if (procEnd < 0) procEnd = content.length();

        String procedureSection = content.substring(procStart, procEnd);

        // Split by sub-steps (e.g. 6.1, 6.2 or 4.1, 4.2)
        Pattern stepPattern = Pattern.compile("(\\d+\\.\\d+)\\s+(.+?)(?=\\d+\\.\\d+\\s+[A-Z]|$)", Pattern.DOTALL);
        Matcher stepMatcher = stepPattern.matcher(procedureSection);

        while (stepMatcher.find()) {
            String stepNumber = stepMatcher.group(1);
            String stepContent = stepMatcher.group(2).trim();
            String stepTitle = stepContent.split("\n")[0].trim();
            steps.add(new TaskCard.ProcedureStep(stepNumber, stepTitle, stepContent));
        }

        return steps;
    }

    private List<String> extractTailSpecificNotes(String content) {
        List<String> notes = new ArrayList<>();
        Pattern p = Pattern.compile("(?:Note for N123AK|N123AK[- ]specific|N123AK:)(.+?)(?:\\n\\n|━)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(content);
        while (m.find()) {
            notes.add(m.group(0).trim());
        }
        return notes;
    }
}
