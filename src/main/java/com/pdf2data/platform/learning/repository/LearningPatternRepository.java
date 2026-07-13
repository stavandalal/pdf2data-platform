package com.pdf2data.platform.learning.repository;

import com.pdf2data.platform.learning.entity.LearningPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LearningPatternRepository extends JpaRepository<LearningPattern, Long> {

    Optional<LearningPattern> findByDocumentTypeAndPatternTypeAndPatternSignature(
            String documentType, LearningPattern.PatternType patternType, String patternSignature);

    List<LearningPattern> findByDocumentTypeAndPatternType(String documentType, LearningPattern.PatternType patternType);

    List<LearningPattern> findByPatternType(LearningPattern.PatternType patternType);

    List<LearningPattern> findAllByOrderByOccurrenceCountDesc();
}
