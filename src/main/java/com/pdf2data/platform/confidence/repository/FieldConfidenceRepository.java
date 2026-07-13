package com.pdf2data.platform.confidence.repository;

import com.pdf2data.platform.confidence.entity.FieldConfidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FieldConfidenceRepository extends JpaRepository<FieldConfidence, Long> {
    List<FieldConfidence> findByDocumentId(Long documentId);
}
