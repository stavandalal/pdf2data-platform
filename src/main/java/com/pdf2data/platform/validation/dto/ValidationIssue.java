package com.pdf2data.platform.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationIssue {
    private String field;
    private String issue;
    private Severity severity;
    public enum Severity {
        INFO, WARNING, ERROR
    }
}
