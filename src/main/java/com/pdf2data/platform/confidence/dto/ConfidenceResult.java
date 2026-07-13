package com.pdf2data.platform.confidence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfidenceResult {
    private Map<String, Double> fieldConfidence;
    private double overallConfidence;
}
