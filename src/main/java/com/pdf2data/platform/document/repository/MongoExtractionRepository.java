package com.pdf2data.platform.document.repository;

import com.pdf2data.platform.document.entity.MongoExtractionResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MongoExtractionRepository extends MongoRepository<MongoExtractionResult, String> {
    Optional<MongoExtractionResult> findByDocumentId(Long documentId);
}
