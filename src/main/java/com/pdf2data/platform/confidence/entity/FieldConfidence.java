package com.pdf2data.platform.confidence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "field_confidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldConfidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private String fieldName;

    @Column(nullable = false)
    private Double confidenceScore;


    private String extractionMethod;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
