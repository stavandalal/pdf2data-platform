package com.pdf2data.platform.document.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        try {
            File tessFolder = new ClassPathResource("tessdata").getFile();
            tesseract = new Tesseract();
            tesseract.setDatapath(tessFolder.getAbsolutePath());
            tesseract.setLanguage("eng");


            tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            tesseract.setPageSegMode(3);
            tesseract.setVariable("user_defined_dpi", "300");
            tesseract.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.,!?-:;()@#$%&/ ");
            tesseract.setVariable("preserve_interword_spaces", "1");
            tesseract.setVariable("textord_heavy_nr", "1");
            tesseract.setVariable("tessedit_do_invert", "0");
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize Tesseract", e);
        }
    }

    public String extractTextFromImage(String imagePath) {
        try {
            File file = new File(imagePath);
            if (!file.exists()) throw new RuntimeException("Image not found : " + imagePath);
            BufferedImage image = ImageIO.read(file);
            if (image == null) throw new RuntimeException("Unsupported Image Format");
            return extractText(image);
        } catch (Exception e) {
            throw new RuntimeException("OCR Failed : " + e.getMessage(), e);
        }
    }

    public String extractText(BufferedImage image) {
        try {
            long start = System.currentTimeMillis();
            BufferedImage processed = preprocess(image);
            String text = tesseract.doOCR(processed);
            long end = System.currentTimeMillis();
            System.out.println("OCR Completed In " + (end - start) + " ms");
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            throw new RuntimeException("OCR Error", e);
        }
    }

    private BufferedImage preprocess(BufferedImage image) {
        validateImage(image);
        BufferedImage output = resize(image);
        output = convertToGray(output);
        output = autoBrightness(output);
        output = increaseContrast(output);
        output = sharpen(output);
        output = convertToBinary(output);
        output = removeNoise(output);
        return output;
    }


    private BufferedImage resize(BufferedImage image) {
        int width = image.getWidth() * 2;
        int height = image.getHeight() * 2;
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private BufferedImage convertToGray(BufferedImage image) {
        ColorConvertOp colorConvertOp = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        colorConvertOp.filter(image, gray);
        return gray;
    }

    private BufferedImage increaseContrast(BufferedImage image) {
        RescaleOp rescaleOp = new RescaleOp(1.35f, 15, null);
        return rescaleOp.filter(image, null);
    }

    private BufferedImage sharpen(BufferedImage image) {
        float[] sharpenKernel = { 0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f };
        Kernel kernel = new Kernel(3, 3, sharpenKernel);
        ConvolveOp convolveOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return convolveOp.filter(image, null);
    }

    private BufferedImage convertToBinary(BufferedImage image) {
        BufferedImage binary = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = binary.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return binary;
    }

    public BufferedImage removeNoise(BufferedImage image) {
        BufferedImage cleaned = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int blackNeighbours = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        if ((image.getRGB(x + i, y + j) & 0xFFFFFF) == 0) blackNeighbours++;
                    }
                }
                if (blackNeighbours >= 5) cleaned.setRGB(x, y, Color.BLACK.getRGB());
                else cleaned.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        return cleaned;
    }

    private BufferedImage autoBrightness(BufferedImage image) {
        RescaleOp op = new RescaleOp(1.15f, 10f, null);
        return op.filter(image, null);
    }

    private void validateImage(BufferedImage image) {
        if (image == null) throw new RuntimeException("Image is null.");
        if (image.getWidth() < 30 || image.getHeight() < 30) throw new RuntimeException("Image resolution is too small.");
    }

    public boolean isReady() { return tesseract != null; }

    public boolean isSupportedImage(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff") || lower.endsWith(".webp");
    }
}