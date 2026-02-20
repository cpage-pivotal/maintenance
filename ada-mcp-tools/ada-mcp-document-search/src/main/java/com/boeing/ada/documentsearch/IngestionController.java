package com.boeing.ada.documentsearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/admin")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int minChunkSize;
    private final int maxChunksPerDoc;
    private final ExecutorService ingestionExecutor = Executors.newSingleThreadExecutor();

    public IngestionController(
            VectorStore vectorStore,
            @Value("${ada.ingestion.chunk-size:750}") int chunkSize,
            @Value("${ada.ingestion.min-chunk-size:350}") int minChunkSize,
            @Value("${ada.ingestion.max-chunks-per-document:500}") int maxChunksPerDoc) {
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.minChunkSize = minChunkSize;
        this.maxChunksPerDoc = maxChunksPerDoc;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestionResult> ingest(@RequestBody IngestionRequest request) {
        log.info("Starting ingestion from directory: {}", request.directoryPath());

        Path directory = Path.of(request.directoryPath());
        if (!Files.isDirectory(directory)) {
            return ResponseEntity.badRequest()
                    .body(new IngestionResult(0, 0, "Directory not found: " + request.directoryPath()));
        }

        int totalDocuments = 0;
        int totalChunks = 0;

        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> pdfFiles = files
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .toList();

            for (Path pdf : pdfFiles) {
                try {
                    int chunks = ingestFile(new FileSystemResource(pdf), inferMetadata(pdf, request));
                    totalChunks += chunks;
                    totalDocuments++;
                    log.info("Ingested {} ({} chunks)", pdf.getFileName(), chunks);
                } catch (Exception e) {
                    log.error("Failed to ingest {}", pdf, e);
                }
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new IngestionResult(totalDocuments, totalChunks, "Error walking directory: " + e.getMessage()));
        }

        // Also ingest .txt files (synthetic data)
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> txtFiles = files
                    .filter(p -> p.toString().toLowerCase().endsWith(".txt"))
                    .toList();

            for (Path txt : txtFiles) {
                try {
                    int chunks = ingestTextFile(txt, inferMetadata(txt, request));
                    totalChunks += chunks;
                    totalDocuments++;
                    log.info("Ingested {} ({} chunks)", txt.getFileName(), chunks);
                } catch (Exception e) {
                    log.error("Failed to ingest {}", txt, e);
                }
            }
        } catch (IOException e) {
            log.error("Error processing text files", e);
        }

        log.info("Ingestion complete: {} documents, {} chunks", totalDocuments, totalChunks);
        return ResponseEntity.ok(new IngestionResult(totalDocuments, totalChunks, null));
    }

    @PostMapping("/ingest/upload")
    public ResponseEntity<Map<String, String>> ingestUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "docType", defaultValue = "handbook") String docType,
            @RequestParam(value = "ataChapter", required = false) String ataChapter,
            @RequestParam(value = "source", defaultValue = "real") String source) throws IOException {

        String originalFilename = file.getOriginalFilename();
        Path temp = Files.createTempFile("ada-ingest-", "-" + originalFilename);
        file.transferTo(temp);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_type", docType);
        metadata.put("source", source);
        metadata.put("title", originalFilename);
        if (ataChapter != null) metadata.put("ata_chapter", ataChapter);

        log.info("Accepted upload: {} ({} bytes), queuing for ingestion", originalFilename, Files.size(temp));

        ingestionExecutor.submit(() -> {
            try {
                int chunks = ingestFile(new FileSystemResource(temp), metadata);
                log.info("Ingested {} ({} chunks)", originalFilename, chunks);
            } catch (Exception e) {
                log.error("Failed to ingest {}", originalFilename, e);
            } finally {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            }
        });

        return ResponseEntity.accepted()
                .body(Map.of("status", "accepted", "file", originalFilename != null ? originalFilename : "unknown"));
    }

    private int ingestFile(Resource resource, Map<String, Object> metadata) {
        var reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.read();

        documents.forEach(doc -> doc.getMetadata().putAll(metadata));

        var splitter = new TokenTextSplitter(chunkSize, minChunkSize, minChunkSize, maxChunksPerDoc, true);
        List<Document> chunks = splitter.apply(documents);

        chunks.forEach(chunk -> chunk.getMetadata().putAll(metadata));

        vectorStore.add(chunks);
        return chunks.size();
    }

    private int ingestTextFile(Path path, Map<String, Object> metadata) throws IOException {
        String content = Files.readString(path);
        var document = new Document(content, metadata);

        var splitter = new TokenTextSplitter(chunkSize, minChunkSize, minChunkSize, maxChunksPerDoc, true);
        List<Document> chunks = splitter.apply(List.of(document));
        chunks.forEach(chunk -> chunk.getMetadata().putAll(metadata));

        vectorStore.add(chunks);
        return chunks.size();
    }

    private Map<String, Object> inferMetadata(Path filePath, IngestionRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        String fileName = filePath.getFileName().toString().toLowerCase();
        String parentDir = filePath.getParent() != null ? filePath.getParent().getFileName().toString() : "";

        if (fileName.contains("faa-h-8083") || fileName.contains("ac_43")) {
            metadata.put("doc_type", "handbook");
            metadata.put("source", "real");
        } else if (parentDir.startsWith("ATA-") || parentDir.startsWith("ata-")) {
            metadata.put("doc_type", "ad");
            metadata.put("source", "real");
            metadata.put("ata_chapter", parentDir.replaceAll("ATA-(\\d+).*", "$1"));
        } else if (fileName.contains("mpd")) {
            metadata.put("doc_type", "mpd");
            metadata.put("source", "synthetic");
        } else if (fileName.contains("atr-")) {
            metadata.put("doc_type", "aircraft_record");
            metadata.put("source", "synthetic");
            metadata.put("tail_number", "N123AK");
        } else if (fileName.contains("amm-")) {
            metadata.put("doc_type", "amm_task_card");
            metadata.put("source", "synthetic");
        }

        metadata.put("title", filePath.getFileName().toString());

        if (request.defaultAtaChapter() != null && !metadata.containsKey("ata_chapter")) {
            metadata.put("ata_chapter", request.defaultAtaChapter());
        }

        return metadata;
    }

    public record IngestionRequest(
            String directoryPath,
            String defaultAtaChapter
    ) {}

    public record IngestionResult(
            int documentsProcessed,
            int chunksCreated,
            String error
    ) {}
}
