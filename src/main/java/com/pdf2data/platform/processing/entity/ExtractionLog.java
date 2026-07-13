package com.pdf2data.platform.processing.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "extraction_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Long durationMs;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Stage {
        UPLOAD, EXTRACTION, CLASSIFICATION, AI_EXTRACTION,
        VALIDATION, CONFIDENCE, HIGHLIGHT, LEARNING, REASONING, SAVE
    }

    public enum Status {
        STARTED, SUCCESS, FAILED
    }
}
