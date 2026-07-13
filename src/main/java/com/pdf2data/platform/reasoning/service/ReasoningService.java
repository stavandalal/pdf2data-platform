package com.pdf2data.platform.reasoning.service;

import com.pdf2data.platform.classification.dto.ClassificationResult;
import com.pdf2data.platform.confidence.dto.ConfidenceResult;
import com.pdf2data.platform.reasoning.dto.ReasoningResult;
import com.pdf2data.platform.validation.dto.ValidationIssue;
import com.pdf2data.platform.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class ReasoningService {

    public ReasoningResult buildReasoning(ClassificationResult classification,
                                           ValidationResult validationResult,
                                           ConfidenceResult confidenceResult) {

        List<String> details = new ArrayList<>();

        if (classification != null) {
            details.add(String.format(
                    "Classified as %s with %.0f%% confidence (method: %s).",
                    classification.getDocumentType(),
                    classification.getConfidence() * 100,
                    classification.getMethod()));
        }

        if (confidenceResult != null) {
            details.add(String.format(
                    "Overall extraction confidence is %.0f%% across %d field(s).",
                    confidenceResult.getOverallConfidence() * 100,
                    confidenceResult.getFieldConfidence() == null ? 0 : confidenceResult.getFieldConfidence().size()));

            List<String> lowConfidenceFields = new ArrayList<>();
            if (confidenceResult.getFieldConfidence() != null) {
                for (Map.Entry<String, Double> entry : confidenceResult.getFieldConfidence().entrySet()) {
                    if (entry.getValue() < 0.5) {
                        lowConfidenceFields.add(entry.getKey());
                    }
                }
            }
            if (!lowConfidenceFields.isEmpty()) {
                details.add("Low-confidence fields that may need manual review: " + String.join(", ", lowConfidenceFields) + ".");
            }
        }

        if (validationResult != null && validationResult.getIssues() != null && !validationResult.getIssues().isEmpty()) {
            for (ValidationIssue issue : validationResult.getIssues()) {
                details.add(String.format("[%s] %s: %s", issue.getSeverity(), issue.getField(), issue.getIssue()));
            }
        } else {
            details.add("No validation issues were found.");
        }

        String summary = buildSummary(classification, validationResult, confidenceResult);

        return ReasoningResult.builder().summary(summary).details(details).build();
    }

    private String buildSummary(ClassificationResult classification,
                                 ValidationResult validationResult,
                                 ConfidenceResult confidenceResult) {

        String type = classification != null ? classification.getDocumentType() : "UNKNOWN";
        double overall = confidenceResult != null ? confidenceResult.getOverallConfidence() : 0.0;
        boolean valid = validationResult == null || validationResult.isValid();

        if (overall >= 0.8 && valid) {
            return "High-confidence extraction of a " + type + " document; results look reliable.";
        } else if (valid) {
            return "Moderate-confidence extraction of a " + type + " document; a quick manual review is recommended.";
        } else {
            return "Extraction of a " + type + " document completed, but validation flagged issues that need attention.";
        }
    }
}
