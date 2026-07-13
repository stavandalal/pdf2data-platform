package com.pdf2data.platform.processing.controller;

import com.pdf2data.platform.auth.entity.User;
import com.pdf2data.platform.auth.repository.UserRepository;
import com.pdf2data.platform.processing.dto.ProcessingResponse;
import com.pdf2data.platform.processing.service.ProcessingOrchestratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Enterprise document-intelligence entry point - the single upload endpoint
 * for the platform. Runs the full pipeline and returns documentType,
 * confidence, reasoning and highlight data as described in the
 * architecture blueprint.
 */
@RestController
@RequestMapping("/api/processing")
@CrossOrigin(origins = "*")
public class ProcessingController {

    private final ProcessingOrchestratorService processingOrchestratorService;
    private final UserRepository userRepository;

    public ProcessingController(ProcessingOrchestratorService processingOrchestratorService,
                                 UserRepository userRepository) {
        this.processingOrchestratorService = processingOrchestratorService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam("prompt") String prompt) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ProcessingResponse response = processingOrchestratorService.process(file, prompt, user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
