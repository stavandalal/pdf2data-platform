package com.pdf2data.platform.learning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores generic OCR / layout PATTERNS learned from processing history.
 *
 * IMPORTANT RULE: This table must never store a literal user value or a
 * specific correction such as "Rajesh -> Rakesh". Only generic, reusable
 * signatures are stored, e.g. a character-confusion rule "0<->O" or a
 * layout signature such as "INVOICE:invoiceNumber:TOP_RIGHT".
 */
@Entity
@Table(name = "learning_patterns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatternType patternType;


    @Column(nullable = false, length = 512)
    private String patternSignature;

    @Builder.Default
    private Integer occurrenceCount = 1;

    @Builder.Default
    private Double confidenceScore = 0.5;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum PatternType {
        OCR_CHAR_CONFUSION,
        LAYOUT_POSITION,
        FIELD_FORMAT
    }
}
