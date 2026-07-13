package com.pdf2data.platform.validation.service;

import com.pdf2data.platform.validation.dto.ValidationIssue;
import com.pdf2data.platform.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


@Service
public class ValidationService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[\\d\\s-]{7,15}$");

    public ValidationResult validate(Map<String, Object> fields) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (fields == null || fields.isEmpty()) {
            issues.add(ValidationIssue.builder()
                    .field("_document")
                    .issue("No fields were extracted from the document.")
                    .severity(ValidationIssue.Severity.ERROR)
                    .build());
            return ValidationResult.builder().valid(false).issues(issues).build();
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String field = entry.getKey();
            if (field.startsWith("_")) {
                continue;
            }
            Object value = entry.getValue();

            if (value == null || (value instanceof String && ((String) value).isBlank())) {
                issues.add(ValidationIssue.builder()
                        .field(field)
                        .issue("Value is missing or empty.")
                        .severity(ValidationIssue.Severity.WARNING)
                        .build());
                continue;
            }

            String stringValue = String.valueOf(value).trim();
            String lowerField = field.toLowerCase();

            if (lowerField.contains("email") && !EMAIL_PATTERN.matcher(stringValue).matches()) {
                issues.add(issue(field, "Does not look like a valid email address.", ValidationIssue.Severity.ERROR));
            }

            if ((lowerField.contains("date") || lowerField.endsWith("dob"))
                    && !DATE_PATTERN.matcher(stringValue).matches()) {
                issues.add(issue(field, "Does not look like a valid date format.", ValidationIssue.Severity.WARNING));
            }

            if ((lowerField.contains("phone") || lowerField.contains("mobile") || lowerField.contains("contact"))
                    && !PHONE_PATTERN.matcher(stringValue).matches()) {
                issues.add(issue(field, "Does not look like a valid phone number.", ValidationIssue.Severity.WARNING));
            }

            if ((lowerField.contains("amount") || lowerField.contains("total") || lowerField.contains("price"))
                    && !stringValue.matches("^[\\d,.]+$")) {
                issues.add(issue(field, "Expected a numeric amount but got non-numeric text.", ValidationIssue.Severity.WARNING));
            }
        }

        boolean hasError = issues.stream().anyMatch(i -> i.getSeverity() == ValidationIssue.Severity.ERROR);
        return ValidationResult.builder().valid(!hasError).issues(issues).build();
    }

    private ValidationIssue issue(String field, String message, ValidationIssue.Severity severity) {
        return ValidationIssue.builder().field(field).issue(message).severity(severity).build();
    }
}
