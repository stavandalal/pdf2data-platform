package com.pdf2data.platform.learning.controller;

import com.pdf2data.platform.learning.dto.LearningSuggestion;
import com.pdf2data.platform.learning.service.LearningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learning")
@CrossOrigin(origins = "*")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/suggestions/{documentType}")
    public ResponseEntity<List<LearningSuggestion>> suggestions(@PathVariable String documentType) {
        return ResponseEntity.ok(learningService.suggestCorrections(documentType.toUpperCase()));
    }
}
