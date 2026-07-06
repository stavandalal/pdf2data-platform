package com.pdf2data.platform.document.service;

import org.springframework.stereotype.Service;

@Service
public class OcrService {
    public String extractTextFromImage(String filePath) {
        try {

            return "[OCR_FALLBACK_TRIGGERED] Scanned document or image detected at path: " + filePath
                    + ". Native Tesseract is not yet connected.";
        } catch (Exception e) {
            throw new RuntimeException("OCR extraction sub-routine failed: " + e.getMessage());
        }
    }
}