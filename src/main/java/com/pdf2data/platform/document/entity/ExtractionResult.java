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

    private String invoiceNumber;
    private String totalAmount;
    private String invoiceDate;


    @OneToOne
    @JoinColumn(name = "document_id")
    private Document document;
}