package com.pdf2data.platform.document.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.auth.entity.User;
import com.pdf2data.platform.auth.repository.UserRepository;
import com.pdf2data.platform.document.entity.Document;
import com.pdf2data.platform.document.entity.ExtractionResult;
import com.pdf2data.platform.document.entity.MongoExtractionResult;
import com.pdf2data.platform.document.repository.DocumentRepository;
import com.pdf2data.platform.document.repository.ExtractionResultRepository;
import com.pdf2data.platform.document.repository.MongoExtractionRepository;
import com.pdf2data.platform.document.service.AiExtractionService;
import com.pdf2data.platform.document.service.ExtractionService;
import com.pdf2data.platform.document.service.FileStorageService;
import com.pdf2data.platform.document.service.OcrService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final MongoExtractionRepository mongoExtractionRepository;
    private final ExtractionService extractionService;
    private final OcrService ocrService;
    private final AiExtractionService aiExtractionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileUploadController(FileStorageService fileStorageService,
                                UserRepository userRepository,
                                DocumentRepository documentRepository,
                                ExtractionResultRepository extractionResultRepository,
                                MongoExtractionRepository mongoExtractionRepository,
                                ExtractionService extractionService,
                                OcrService ocrService,
                                AiExtractionService aiExtractionService) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.extractionResultRepository = extractionResultRepository;
        this.mongoExtractionRepository = mongoExtractionRepository;
        this.extractionService = extractionService;
        this.ocrService = ocrService;
        this.aiExtractionService = aiExtractionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Extract invoiceNumber, totalAmount, and invoiceDate from this text") String prompt) {

        try {
            String usernameOrEmail = SecurityContextHolder.getContext().getAuthentication().getName();

            User user = userRepository.findByUsername(usernameOrEmail)
                    .or(() -> userRepository.findByEmail(usernameOrEmail))
                    .orElseThrow(() -> new RuntimeException("User context not found: " + usernameOrEmail));

            String path = fileStorageService.saveFile(file);

            // Apache PDFBox
            String extractedText = extractionService.extractTextFromPDF(path);

            if (extractedText == null || extractedText.trim().isEmpty() || extractedText.trim().length() < 10) {
                extractedText = ocrService.extractTextFromImage(path);
            }

            byte[] rawBytes = extractedText.getBytes(StandardCharsets.UTF_8);
            String sanitizedText = new String(rawBytes, StandardCharsets.UTF_8);

            String validatedPrompt = prompt + "\n\nCRITICAL MANDATE: Only output values explicitly confirmed in the text. " +
                    "If a value is not mentioned or unknown, set its value strictly to null. Do not guess or hallucinate details.";

            String structuredData = aiExtractionService.extractDataWithAI(sanitizedText, validatedPrompt);

            if (structuredData.startsWith("Error")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("AI Pipeline Error: " + structuredData);
            }

            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .filePath(path)
                    .user(user)
                    .build();
            Document savedDoc = documentRepository.save(document);

            ExtractionResult relationalResult = ExtractionResult.builder()
                    .rawJsonData(structuredData)
                    .document(savedDoc)
                    .build();
            extractionResultRepository.save(relationalResult);

            String cleanedJson = sanitizeJson(structuredData);

            Map<String, Object> parsedFieldsMap;
            try {
                parsedFieldsMap = objectMapper.readValue(cleanedJson, new TypeReference<Map<String, Object>>() {});
            } catch (Exception parseException) {

                parsedFieldsMap = new HashMap<>();
                parsedFieldsMap.put("rawExtractedMessage", structuredData);
                parsedFieldsMap.put("parseError", parseException.getMessage());
            }

            MongoExtractionResult mongoResult = MongoExtractionResult.builder()
                    .documentId(savedDoc.getId())
                    .fileName(savedDoc.getFileName())
                    .rawJsonData(structuredData)
                    .parsedFields(parsedFieldsMap)
                    .processedAt(LocalDateTime.now())
                    .build();
            mongoExtractionRepository.save(mongoResult);

            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("message", "File processed successfully. Stored in relational and NoSQL databases.");
            responsePayload.put("documentId", savedDoc.getId());
            responsePayload.put("mongoId", mongoResult.getId());
            responsePayload.put("structuredAiResponse", parsedFieldsMap);

            return ResponseEntity.ok(responsePayload);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing document extraction: " + e.getMessage());
        }
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

    @GetMapping("/{documentId}/result")
    public ResponseEntity<?> getDocumentExtractionResult(@PathVariable Long documentId) {
        return mongoExtractionRepository.findByDocumentId(documentId)
                .map(result -> ResponseEntity.ok(result.getParsedFields()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Extraction record not found for the given document ID.")));
    }
}