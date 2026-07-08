package com.pdf2data.platform.document.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.auth.entity.User;
import com.pdf2data.platform.auth.repository.UserRepository;
import com.pdf2data.platform.document.entity.Document;
import com.pdf2data.platform.document.service.AiExtractionService;
import com.pdf2data.platform.document.service.DocumentStorageService;
import com.pdf2data.platform.document.service.ExtractionService;
import com.pdf2data.platform.document.service.FileStorageService;
import com.pdf2data.platform.document.service.OcrService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final ExtractionService extractionService;
    private final OcrService ocrService;
    private final AiExtractionService aiExtractionService;
    private final DocumentStorageService documentStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileUploadController(
            FileStorageService fileStorageService,
            UserRepository userRepository,
            ExtractionService extractionService,
            OcrService ocrService,
            AiExtractionService aiExtractionService,
            DocumentStorageService documentStorageService
    ) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.extractionService = extractionService;
        this.ocrService = ocrService;
        this.aiExtractionService = aiExtractionService;
        this.documentStorageService = documentStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("prompt") String prompt
    ) {

        try {

            String email = SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getName();

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() ->
                            new RuntimeException("User not found"));

            String filePath = fileStorageService.saveFile(file);

            String fileName = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();

            String extractedText;

            if (fileName.endsWith(".pdf")) {

                extractedText =
                        extractionService.extractTextFromPDF(filePath);

            } else {

                extractedText =
                        ocrService.extractTextFromImage(filePath);

            }

            System.out.println("========== OCR TEXT ==========");
            System.out.println(extractedText);
            System.out.println("==============================");

            String aiResponse =
                    aiExtractionService.extractDataWithAI(
                            extractedText,
                            prompt
                    );

            Map<String, Object> parsedFields =
                    objectMapper.readValue(
                            sanitizeJson(aiResponse),
                            new TypeReference<Map<String, Object>>() {
                            });

            Document document = Document.builder()
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .user(user)
                    .build();

            documentStorageService.saveAll(
                    document,
                    aiResponse,
                    parsedFields
            );

            Map<String, Object> response = new HashMap<>();

            response.put("documentId", document.getId());
            response.put("data", parsedFields);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());

        }

    }

    private String sanitizeJson(String json) {

        if (json == null)
            return "{}";

        return json
                .replaceAll("^```json", "")
                .replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();
    }

}