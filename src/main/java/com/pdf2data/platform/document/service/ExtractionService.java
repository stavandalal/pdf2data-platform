package com.pdf2data.platform.document.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);


    private static final int MIN_CHARS_PER_PAGE = 20;
    private static final int OCR_RENDER_DPI = 300;

    private final ImageOcrProcessor imageOcrProcessor;

    public ExtractionService(ImageOcrProcessor imageOcrProcessor) {
        this.imageOcrProcessor = imageOcrProcessor;
    }

    public String extractTextFromPDF(String filePath) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new RuntimeException("PDF not found: " + filePath);
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new RuntimeException("PDF has no pages: " + filePath);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = null;
            StringBuilder finalText = new StringBuilder();

            for (int page = 0; page < pageCount; page++) {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);

                String pageText = safeStripPage(stripper, document, page);

                if (pageText != null && countNonWhitespace(pageText) > MIN_CHARS_PER_PAGE) {
                    finalText.append(pageText.trim());
                } else {
                    log.debug("Page {} of {} has no usable text layer, falling back to OCR", page + 1, pdfFile.getName());
                    if (renderer == null) {
                        renderer = new PDFRenderer(document);
                    }
                    String ocrText = ocrPage(renderer, page, pdfFile.getName());
                    finalText.append(ocrText);
                }
                finalText.append(System.lineSeparator());
            }

            String result = finalText.toString().trim();
            if (result.isEmpty()) {
                log.warn("No text extracted (typed or OCR) from PDF: {}", pdfFile.getName());
            }
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PDF extraction failed: " + e.getMessage(), e);
        }
    }


    public List<String> extractTextPerPageFromPDF(String filePath) {
        File pdfFile = new File(filePath);
        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new RuntimeException("PDF not found: " + filePath);
        }

        List<String> pages = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new RuntimeException("PDF has no pages: " + filePath);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = null;

            for (int page = 0; page < pageCount; page++) {
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);

                String pageText = safeStripPage(stripper, document, page);

                if (pageText != null && countNonWhitespace(pageText) > MIN_CHARS_PER_PAGE) {
                    pages.add(pageText.trim());
                } else {
                    if (renderer == null) {
                        renderer = new PDFRenderer(document);
                    }
                    pages.add(ocrPage(renderer, page, pdfFile.getName()));
                }
            }
            return pages;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("PDF page extraction failed: " + e.getMessage(), e);
        }
    }

    private String safeStripPage(PDFTextStripper stripper, PDDocument document, int pageIndex) {
        try {
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("Failed to extract native text from page {}: {}", pageIndex + 1, e.getMessage());
            return null;
        }
    }


    private String ocrPage(PDFRenderer renderer, int pageIndex, String sourceFileName) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_RENDER_DPI, ImageType.RGB);
            return imageOcrProcessor.extractText(image);
        } catch (Exception e) {
            log.error("OCR failed for page {} of {}: {}", pageIndex + 1, sourceFileName, e.getMessage(), e);
            return "";
        }
    }

    private int countNonWhitespace(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) count++;
        }
        return count;
    }
}