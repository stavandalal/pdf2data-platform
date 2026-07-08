package com.pdf2data.platform.document.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.document.entity.Document;
import com.pdf2data.platform.document.entity.ExtractionResult;
import com.pdf2data.platform.document.entity.MongoExtractionResult;
import com.pdf2data.platform.document.repository.ExtractionResultRepository;
import com.pdf2data.platform.document.repository.MongoExtractionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DocumentProcessingService {

    private final ExtractionService extractionService;
    private final OcrService ocrService;
    private final AiExtractionService aiExtractionService;
    private final ExtractionResultRepository extractionResultRepository;
    private final MongoExtractionRepository mongoExtractionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public DocumentProcessingService(ExtractionService extractionService,
                                     OcrService ocrService,
                                     AiExtractionService aiExtractionService,
                                     ExtractionResultRepository extractionResultRepository,
                                     MongoExtractionRepository mongoExtractionRepository) {
        this.extractionService = extractionService;
        this.ocrService = ocrService;
        this.aiExtractionService = aiExtractionService;
        this.extractionResultRepository = extractionResultRepository;
        this.mongoExtractionRepository = mongoExtractionRepository;
    }


    public void submitProcessingTask(Document document, String userPrompt) {
        Map<String, Object> initialFields = new HashMap<>();
        initialFields.put("_status", "PROCESSING");

        MongoExtractionResult placeholder = MongoExtractionResult.builder()
                .documentId(document.getId())
                .fileName(document.getFileName())
                .parsedFields(initialFields)
                .processedAt(LocalDateTime.now())
                .build();
        mongoExtractionRepository.save(placeholder);

        executorService.submit(() -> {
            try {

                String rawText;

                String fileName = document.getFileName().toLowerCase();

                if (fileName.endsWith(".pdf")) {

                    rawText = extractionService.extractTextFromPDF(document.getFilePath());

                } else {

                    rawText = ocrService.extractTextFromImage(document.getFilePath());

                }


                byte[] rawBytes = rawText.getBytes(StandardCharsets.UTF_8);
                String cleanText = new String(rawBytes, StandardCharsets.UTF_8);


                String strictPrompt = userPrompt + "\n\n" +
                        "CRITICAL STRUCTURAL MANDATES:\n" +
                        "1. Return ONLY valid, structured JSON output.\n" +
                        "2. Do not explain, summarize, or include markdown wrapping backticks.\n" +
                        "3. Extract tables natively by capturing grid headers and items into array objects.\n" +
                        "4. Only map fields explicitly visible in the document. If any field is missing, set its value strictly to null.";


                String rawAiResponse = aiExtractionService.extractDataWithAI(cleanText, strictPrompt);

                if (rawAiResponse.startsWith("Error")) {
                    throw new RuntimeException("AI processing failed: " + rawAiResponse);
                }

                String jsonPayload = sanitizeJson(rawAiResponse);

                Map<String, Object> extractedFields;
                try {
                    extractedFields = objectMapper.readValue(jsonPayload, new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
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

            } catch (Exception e) {
                Map<String, Object> errorFields = new HashMap<>();
                errorFields.put("_status", "FAILED");
                errorFields.put("_errorMessage", e.getMessage());

                placeholder.setParsedFields(errorFields);
                placeholder.setRawJsonData(null);
                placeholder.setProcessedAt(LocalDateTime.now());
                mongoExtractionRepository.save(placeholder);
            }
        });
    }


    private String sanitizeJson(String json) {
        if (json == null) {
            return "{}";
        }
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            } else {
                cleaned = cleaned.substring(3);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}