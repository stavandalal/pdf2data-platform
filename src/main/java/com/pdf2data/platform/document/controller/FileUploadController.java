package com.pdf2data.platform.document.controller;

import com.pdf2data.platform.auth.entity.User;
import com.pdf2data.platform.auth.repository.UserRepository;
import com.pdf2data.platform.document.entity.Document;
import com.pdf2data.platform.document.repository.DocumentRepository;
import com.pdf2data.platform.document.service.AiExtractionService;
import com.pdf2data.platform.document.service.ExtractionService;
import com.pdf2data.platform.document.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ExtractionService extractionService;
    private final AiExtractionService aiExtractionService;

    public FileUploadController(FileStorageService fileStorageService,
                                UserRepository userRepository,
                                DocumentRepository documentRepository,
                                ExtractionService extractionService,
                                AiExtractionService aiExtractionService) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.aiExtractionService = aiExtractionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "Extract invoiceNumber, totalAmount, and invoiceDate from this text") String prompt) {

        String usernameOrEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new RuntimeException("User context not found: " + usernameOrEmail));

        String path = fileStorageService.saveFile(file);
        String extractedText = extractionService.extractTextFromPDF(path);


        String structuredData = aiExtractionService.extractDataWithAI(extractedText, prompt);

        Document document = Document.builder()
                .fileName(file.getOriginalFilename())
                .filePath(path)
                .user(user)
                .build();

        documentRepository.save(document);

        return ResponseEntity.ok("File processed successfully. AI Response: " + structuredData);
    }
}