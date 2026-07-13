package com.pdf2data.platform.classification.service;

import com.pdf2data.platform.classification.dto.ClassificationResult;
import com.pdf2data.platform.document.service.AiExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;


@Service
public class DocumentClassifierService {

    private static final Logger log = LoggerFactory.getLogger(DocumentClassifierService.class);

    private static final String[] SUPPORTED_TYPES = {
            "INVOICE", "RECEIPT", "RESUME", "MARKSHEET_OR_CERTIFICATE",
            "ID_CARD", "CONTRACT", "BANK_STATEMENT", "MEDICAL_REPORT", "OTHER"
    };

    private static final Map<String, String[]> KEYWORD_RULES = new LinkedHashMap<>();

    static {
        KEYWORD_RULES.put("INVOICE", new String[]{"invoice", "invoice no", "bill to", "gstin", "amount due"});
        KEYWORD_RULES.put("RECEIPT", new String[]{"receipt", "paid", "cash memo", "thank you for your purchase"});
        KEYWORD_RULES.put("RESUME", new String[]{"resume", "curriculum vitae", "work experience", "education", "skills"});
        KEYWORD_RULES.put("MARKSHEET_OR_CERTIFICATE", new String[]{"marksheet", "grade", "certificate", "examination", "cgpa", "percentage"});
        KEYWORD_RULES.put("ID_CARD", new String[]{"aadhaar", "pan card", "date of birth", "identity card", "passport"});
        KEYWORD_RULES.put("BANK_STATEMENT", new String[]{"account statement", "ifsc", "opening balance", "closing balance"});
        KEYWORD_RULES.put("MEDICAL_REPORT", new String[]{"diagnosis", "patient", "prescription", "doctor"});
        KEYWORD_RULES.put("CONTRACT", new String[]{"agreement", "party of the first part", "hereinafter", "terms and conditions"});
    }

    private final AiExtractionService aiExtractionService;

    public DocumentClassifierService(AiExtractionService aiExtractionService) {
        this.aiExtractionService = aiExtractionService;
    }

    public ClassificationResult classify(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ClassificationResult.builder().documentType("OTHER").confidence(0.3).method("EMPTY_TEXT").build();
        }

        ClassificationResult aiResult = tryAiClassification(rawText);
        if (aiResult != null) {
            return aiResult;
        }

        return keywordFallback(rawText);
    }

    private ClassificationResult tryAiClassification(String rawText) {
        try {
            String snippet = rawText.length() > 4000 ? rawText.substring(0, 4000) : rawText;
            String prompt = "Classify this document into exactly ONE of these labels: "
                    + String.join(", ", SUPPORTED_TYPES)
                    + ". Reply with ONLY the label, nothing else. Document text:\n" + snippet;

            String response = aiExtractionService.generateText(prompt);
            if (response == null || response.startsWith("Error")) {
                return null;
            }

            String cleaned = response.trim().toUpperCase().replaceAll("[^A-Z_]", "");
            for (String type : SUPPORTED_TYPES) {
                if (cleaned.contains(type)) {
                    return ClassificationResult.builder().documentType(type).confidence(0.85).method("AI").build();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("AI classification failed, falling back to keyword rules. reason={}", e.getMessage());
            return null;
        }
    }

    private ClassificationResult keywordFallback(String rawText) {
        String lower = rawText.toLowerCase();

        String bestType = "OTHER";
        int bestScore = 0;

        for (Map.Entry<String, String[]> entry : KEYWORD_RULES.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = entry.getKey();
            }
        }

        double confidence = bestScore == 0 ? 0.3 : Math.min(0.4 + (bestScore * 0.1), 0.75);
        return ClassificationResult.builder().documentType(bestType).confidence(confidence).method("KEYWORD_FALLBACK").build();
    }
}
