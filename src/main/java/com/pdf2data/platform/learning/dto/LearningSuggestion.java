package com.pdf2data.platform.learning.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningSuggestion {
    private String patternSignature;
    private String patternType;
    private String suggestion;
    private double confidence;
}
