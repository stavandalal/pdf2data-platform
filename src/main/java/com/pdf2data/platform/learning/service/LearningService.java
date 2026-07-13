package com.pdf2data.platform.learning.service;

import com.pdf2data.platform.highlight.dto.HighlightRegion;
import com.pdf2data.platform.learning.dto.LearningSuggestion;
import com.pdf2data.platform.learning.entity.LearningPattern;
import com.pdf2data.platform.learning.repository.LearningPatternRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningService {

    private static final Map<String, String> CHAR_CONFUSIONS = new LinkedHashMap<>();

    static {
        CHAR_CONFUSIONS.put("0", "O");
        CHAR_CONFUSIONS.put("1", "l");
        CHAR_CONFUSIONS.put("1", "I");
        CHAR_CONFUSIONS.put("5", "S");
        CHAR_CONFUSIONS.put("8", "B");
        CHAR_CONFUSIONS.put("rn", "m");
        CHAR_CONFUSIONS.put("cl", "d");
        CHAR_CONFUSIONS.put("vv", "w");
    }

    private final LearningPatternRepository learningPatternRepository;

    public LearningService(LearningPatternRepository learningPatternRepository) {
        this.learningPatternRepository = learningPatternRepository;
    }

    public void learnFromExtraction(String documentType, String rawText, List<HighlightRegion> highlights) {
        if (rawText == null || highlights == null) {
            return;
        }
        String haystack = rawText.toLowerCase();

        for (HighlightRegion region : highlights) {
            if (region.isFound()) {

                recordLayoutPattern(documentType, region);
                continue;
            }

            String value = region.getMatchedText();
            if (value == null || value.isBlank()) {
                continue;
            }

            for (Map.Entry<String, String> confusion : CHAR_CONFUSIONS.entrySet()) {
                String variant = value.toLowerCase()
                        .replace(confusion.getKey(), confusion.getValue().toLowerCase());
                if (!variant.equals(value.toLowerCase()) && haystack.contains(variant)) {
                    recordPattern(documentType, LearningPattern.PatternType.OCR_CHAR_CONFUSION,
                            confusion.getKey() + "<->" + confusion.getValue());
                    break;
                }
            }
        }
    }

    private void recordLayoutPattern(String documentType, HighlightRegion region) {
        if (region.getStartOffset() < 0) {
            return;
        }
        String bucket = region.getPage() <= 1 ? "PAGE_1" : "PAGE_" + region.getPage() + "_PLUS";
        String signature = region.getField() + ":" + bucket;
        recordPattern(documentType, LearningPattern.PatternType.LAYOUT_POSITION, signature);
    }

    private void recordPattern(String documentType, LearningPattern.PatternType type, String signature) {
        learningPatternRepository
                .findByDocumentTypeAndPatternTypeAndPatternSignature(documentType, type, signature)
                .ifPresentOrElse(existing -> {
                    existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
                    existing.setConfidenceScore(Math.min(0.99, 0.5 + existing.getOccurrenceCount() * 0.05));
                    learningPatternRepository.save(existing);
                }, () -> learningPatternRepository.save(LearningPattern.builder()
                        .documentType(documentType)
                        .patternType(type)
                        .patternSignature(signature)
                        .occurrenceCount(1)
                        .confidenceScore(0.5)
                        .build()));
    }


    public List<LearningSuggestion> suggestCorrections(String documentType) {
        List<LearningSuggestion> suggestions = new ArrayList<>();
        List<LearningPattern> patterns = learningPatternRepository
                .findByDocumentTypeAndPatternType(documentType, LearningPattern.PatternType.OCR_CHAR_CONFUSION);

        for (LearningPattern pattern : patterns) {
            suggestions.add(LearningSuggestion.builder()
                    .patternSignature(pattern.getPatternSignature())
                    .patternType(pattern.getPatternType().name())
                    .suggestion("Documents of type '" + documentType + "' have historically shown OCR confusion for '"
                            + pattern.getPatternSignature() + "'. Consider a manual re-check on similar characters.")
                    .confidence(pattern.getConfidenceScore())
                    .build());
        }
        return suggestions;
    }
}
