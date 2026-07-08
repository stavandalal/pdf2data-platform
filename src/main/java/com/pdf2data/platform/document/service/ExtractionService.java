package com.pdf2data.platform.document.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

@Service
public class ExtractionService {

    private final OcrService ocrService;

    public ExtractionService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public String extractTextFromPDF(String filePath) {

        File pdfFile = new File(filePath);

        if (!pdfFile.exists()) {
            throw new RuntimeException("PDF not found : " + filePath);
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            PDFTextStripper stripper = new PDFTextStripper();

            String extractedText = stripper.getText(document);


            if (extractedText != null && extractedText.trim().length() > 20) {
                return extractedText.trim();
            }



            PDFRenderer renderer = new PDFRenderer(document);

            StringBuilder finalText = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); page++) {

                BufferedImage image =
                        renderer.renderImageWithDPI(page, 300, ImageType.RGB);

                File tempImage =
                        File.createTempFile("ocr_page_" + page, ".png");

                ImageIO.write(image, "png", tempImage);

                String pageText =
                        ocrService.extractTextFromImage(tempImage.getAbsolutePath());

                finalText.append(pageText);
                finalText.append(System.lineSeparator());

                tempImage.delete();
            }

            return finalText.toString().trim();

        } catch (Exception e) {
            throw new RuntimeException("PDF Extraction Failed : " + e.getMessage(), e);
        }

    }

}