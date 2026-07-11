package com.pdf2data.platform.document.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.document.entity.Document;
import com.pdf2data.platform.document.entity.ExtractionResult;
import com.pdf2data.platform.document.entity.MongoExtractionResult;
import com.pdf2data.platform.document.repository.ExtractionResultRepository;
import com.pdf2data.platform.document.repository.MongoExtractionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final ExtractionService extractionService;
    private final ImageOcrProcessor imageOcrProcessor;
    private final AiExtractionService aiExtractionService;
    private final ExtractionResultRepository extractionResultRepository;
    private final MongoExtractionRepository mongoExtractionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService;

    private static final long TASK_TIMEOUT_MINUTES = 5;

    public DocumentProcessingService(ExtractionService extractionService,
                                     ImageOcrProcessor imageOcrProcessor,
                                     AiExtractionService aiExtractionService,
                                     ExtractionResultRepository extractionResultRepository,
                                     MongoExtractionRepository mongoExtractionRepository,
                                     @Value("${document.processing.pool-size:4}") int poolSize) {
        this.extractionService = extractionService;
        this.imageOcrProcessor = imageOcrProcessor;
        this.aiExtractionService = aiExtractionService;
        this.extractionResultRepository = extractionResultRepository;
        this.mongoExtractionRepository = mongoExtractionRepository;
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    public void submitProcessingTask(Document document, String userPrompt) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(document.getFileName(), "document.fileName must not be null");
        Objects.requireNonNull(userPrompt, "userPrompt must not be null");

        Map<String, Object> initialFields = new HashMap<>();
        initialFields.put("_status", "PROCESSING");

        MongoExtractionResult placeholder = MongoExtractionResult.builder()
                .documentId(document.getId())
                .fileName(document.getFileName())
                .parsedFields(initialFields)
                .processedAt(LocalDateTime.now())
                .build();
        mongoExtractionRepository.save(placeholder);

        executorService.submit(() -> runWithTimeout(document, userPrompt, placeholder));
    }

    private void runWithTimeout(Document document, String userPrompt, MongoExtractionResult placeholder) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<Void> future = worker.submit(() -> {
            process(document, userPrompt, placeholder);
            return null;
        });

        try {
            future.get(TASK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Processing timed out after {} minutes for document id={}",
                    TASK_TIMEOUT_MINUTES, document.getId());
            saveFailure(placeholder, "Processing timed out after " + TASK_TIMEOUT_MINUTES + " minutes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Processing interrupted for document id={}", document.getId(), e);
            saveFailure(placeholder, "Processing was interrupted");
        } catch (ExecutionException e) {
            log.error("Processing failed for document id={}", document.getId(), e.getCause());
            saveFailure(placeholder, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } finally {
            worker.shutdownNow();
        }
    }

    private void process(Document document, String userPrompt, MongoExtractionResult placeholder) {
        try {
            String fileName = document.getFileName().toLowerCase();
            String rawText;

            if (fileName.endsWith(".pdf")) {
                rawText = extractionService.extractTextFromPDF(document.getFilePath());
            } else {
                rawText = imageOcrProcessor.extractTextFromImage(document.getFilePath());
            }

            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("No text could be extracted from document: " + document.getFileName());
            }

            String strictPrompt = userPrompt + "\n\n" +
                    "CRITICAL STRUCTURAL MANDATES:\n" +
                    "1. Return ONLY valid, structured JSON output.\n" +
                    "2. Do not explain, summarize, or include markdown wrapping backticks.\n" +
                    "3. Extract tables natively by capturing grid headers and items into array objects.\n" +
                    "4. Only map fields explicitly visible in the document. If any field is missing, set its value strictly to null.";

            String rawAiResponse = aiExtractionService.extractDataWithAI(rawText, strictPrompt);

            if (rawAiResponse == null || rawAiResponse.startsWith("Error")) {
                throw new RuntimeException("AI processing failed: " + rawAiResponse);
            }

            String jsonPayload = sanitizeJson(rawAiResponse);

            Map<String, Object> extractedFields;
            try {
                extractedFields = objectMapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("AI response for document id={} was not valid JSON, storing raw text instead. reason={}",
                        document.getId(), e.getMessage());
                extractedFields = new HashMap<>();
                extractedFields.put("rawOutputText", rawAiResponse);
            }

            ExtractionResult relationalData = ExtractionResult.builder()
                    .rawJsonData(rawAiResponse)
                    .document(document)
                    .build();
            extractionResultRepository.save(relationalData);

            extractedFields.put("_status", "COMPLETED");
            placeholder.setParsedFields(extractedFields);
            placeholder.setRawJsonData(rawAiResponse);
            placeholder.setProcessedAt(LocalDateTime.now());
            mongoExtractionRepository.save(placeholder);

            log.info("Processing completed for document id={}", document.getId());
        } catch (Exception e) {
            log.error("Processing failed for document id={}", document.getId(), e);
            saveFailure(placeholder, e.getMessage());
        }
    }

    private void saveFailure(MongoExtractionResult placeholder, String errorMessage) {
        Map<String, Object> errorFields = new HashMap<>();
        errorFields.put("_status", "FAILED");
        errorFields.put("_errorMessage", errorMessage);

        placeholder.setParsedFields(errorFields);
        placeholder.setRawJsonData(null);
        placeholder.setProcessedAt(LocalDateTime.now());
        mongoExtractionRepository.save(placeholder);
    }


    private String sanitizeJson(String json) {
        if (json == null) {
            return "{}";
        }
        String cleaned = json.trim();

        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            cleaned = firstNewline != -1 ? cleaned.substring(firstNewline + 1) : cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();


        int start = indexOfFirst(cleaned, '{', '[');
        int end = indexOfLast(cleaned, '}', ']');
        if (start != -1 && end != -1 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        return cleaned.trim();
    }

    private int indexOfFirst(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia == -1) return ib;
        if (ib == -1) return ia;
        return Math.min(ia, ib);
    }

    private int indexOfLast(String s, char a, char b) {
        int ia = s.lastIndexOf(a);
        int ib = s.lastIndexOf(b);
        return Math.max(ia, ib);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DocumentProcessingService executor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}