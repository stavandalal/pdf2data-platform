package com.pdf2data.platform.document.repository;

import com.pdf2data.platform.document.entity.ExtractionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, Long> {

    ExtractionResult findByDocumentId(Long documentId);
}