package com.boeing.ada.documentsearch;

import org.springframework.ai.document.Document;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentSearchService {

    private final VectorStore vectorStore;

    public DocumentSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @McpTool(description = "Search the maintenance document corpus using natural language. "
            + "Returns the most relevant document chunks ranked by semantic similarity. "
            + "Use this for general questions about procedures, systems, regulations, "
            + "or any topic where you need background information from FAA handbooks, "
            + "Airworthiness Directives, or maintenance manuals.")
    public List<DocumentChunk> searchDocuments(
            @McpToolParam(description = "Natural language search query", required = true) String query,
            @McpToolParam(description = "Filter by document type: handbook, ad, mpd, aircraft_record, amm_task_card") String docType,
            @McpToolParam(description = "ATA chapter number to narrow scope, e.g. '24', '32'") String ataChapter,
            @McpToolParam(description = "Number of chunks to return (default 5)") Integer topK) {

        int k = (topK != null && topK > 0) ? Math.min(topK, 20) : 5;

        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(k);

        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        Filter.Expression filter = buildFilter(fb, docType, ataChapter);
        if (filter != null) {
            requestBuilder.filterExpression(filter);
        }

        List<Document> results = vectorStore.similaritySearch(requestBuilder.build());

        return results.stream()
                .map(doc -> new DocumentChunk(
                        doc.getText(),
                        getMetadata(doc, "title"),
                        getMetadata(doc, "doc_type"),
                        getMetadata(doc, "ata_chapter"),
                        getMetadata(doc, "source"),
                        doc.getScore() != null ? doc.getScore() : 0.0
                ))
                .toList();
    }

    private Filter.Expression buildFilter(FilterExpressionBuilder fb, String docType, String ataChapter) {
        FilterExpressionBuilder.Op docTypeOp = (docType != null && !docType.isBlank())
                ? fb.eq("doc_type", docType)
                : null;
        FilterExpressionBuilder.Op ataOp = (ataChapter != null && !ataChapter.isBlank())
                ? fb.eq("ata_chapter", ataChapter)
                : null;

        if (docTypeOp != null && ataOp != null) {
            return fb.and(docTypeOp, ataOp).build();
        }
        if (docTypeOp != null) return docTypeOp.build();
        if (ataOp != null) return ataOp.build();
        return null;
    }

    private String getMetadata(Document doc, String key) {
        Object value = doc.getMetadata().get(key);
        return value != null ? value.toString() : null;
    }
}
