package com.pdf2data.platform.processing.repository;

import com.pdf2data.platform.processing.entity.ExtractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtractionLogRepository extends JpaRepository<ExtractionLog, Long> {
    List<ExtractionLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}
