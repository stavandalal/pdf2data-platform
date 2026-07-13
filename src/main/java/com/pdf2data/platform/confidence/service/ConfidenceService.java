package com.pdf2data.platform.confidence.service;

import com.pdf2data.platform.confidence.dto.ConfidenceResult;
import com.pdf2data.platform.confidence.entity.FieldConfidence;
import com.pdf2data.platform.confidence.repository.FieldConfidenceRepository;
import com.pdf2data.platform.highlight.dto.HighlightRegion;
import com.pdf2data.platform.validation.dto.ValidationIssue;
import com.pdf2data.platform.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class ConfidenceService {

    private final FieldConfidenceRepository fieldConfidenceRepository;

    public ConfidenceService(FieldConfidenceRepository fieldConfidenceRepository) {
        this.fieldConfidenceRepository = fieldConfidenceRepository;
    }

    public ConfidenceResult computeConfidence(Long documentId,
                                               Map<String, Object> fields,
                                               ValidationResult validationResult,
                                               List<HighlightRegion> highlights) {

        Map<String, Double> fieldConfidence = new HashMap<>();

        if (fields == null || fields.isEmpty()) {
            return ConfidenceResult.builder().fieldConfidence(fieldConfidence).overallConfidence(0.0).build();
        }

        Map<String, Boolean> foundInText = new HashMap<>();
        if (highlights != null) {
            for (HighlightRegion region : highlights) {
                foundInText.put(region.getField(), region.isFound());
            }
        }

        Map<String, ValidationIssue.Severity> worstIssuePerField = new HashMap<>();
        if (validationResult != null && validationResult.getIssues() != null) {
            for (ValidationIssue issue : validationResult.getIssues()) {
                ValidationIssue.Severity current = worstIssuePerField.get(issue.getField());
                if (current == null || issue.getSeverity().ordinal() > current.ordinal()) {
                    worstIssuePerField.put(issue.getField(), issue.getSeverity());
                }
            }
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String field = entry.getKey();
            if (field.startsWith("_")) {
                continue;
            }
            Object value = entry.getValue();

            double score;
            String method;

            if (value == null || String.valueOf(value).isBlank()) {
                score = 0.05;
                method = "MISSING_VALUE";
            } else {
                score = 0.6;

                Boolean found = foundInText.get(field);
                if (Boolean.TRUE.equals(found)) {
                    score += 0.3;
                    method = "TEXT_MATCH";
                } else if (Boolean.FALSE.equals(found)) {
                    score -= 0.15;
                    method = "TEXT_MISMATCH";
                } else {
                    method = "DEFAULT";
                }

                ValidationIssue.Severity severity = worstIssuePerField.get(field);
                if (severity == ValidationIssue.Severity.ERROR) {
                    score -= 0.35;
                    method = "VALIDATION_PENALTY";
                } else if (severity == ValidationIssue.Severity.WARNING) {
                    score -= 0.15;
                    method = "VALIDATION_PENALTY";
                }
            }

            score = Math.max(0.0, Math.min(1.0, score));
            fieldConfidence.put(field, round(score));

            fieldConfidenceRepository.save(FieldConfidence.builder()
                    .documentId(documentId)
                    .fieldName(field)
                    .confidenceScore(score)
                    .extractionMethod(method)
                    .build());
        }

        double overall = fieldConfidence.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return ConfidenceResult.builder()
                .fieldConfidence(fieldConfidence)
                .overallConfidence(round(overall))
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
