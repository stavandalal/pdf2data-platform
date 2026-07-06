package com.pdf2data.platform.document.entity;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Table(name = "extraction_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String rawJsonData;

    @OneToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
}