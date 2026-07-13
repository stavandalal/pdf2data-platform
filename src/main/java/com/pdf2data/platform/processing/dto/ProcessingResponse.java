package com.pdf2data.platform.processing.dto;

import com.pdf2data.platform.highlight.dto.HighlightRegion;
import com.pdf2data.platform.reasoning.dto.ReasoningResult;
import com.pdf2data.platform.validation.dto.ValidationIssue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingResponse {
    private Long documentId;
    private String fileName;
    private String documentType;
    private double documentTypeConfidence;
    private Map<String, Object> data;
    private Map<String, Double> fieldConfidence;
    private double overallConfidence;
    private List<ValidationIssue> validationIssues;
    private List<HighlightRegion> highlights;
    private ReasoningResult reasoning;
}
