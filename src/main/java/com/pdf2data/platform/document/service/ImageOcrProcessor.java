package com.pdf2data.platform.document.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ImageOcrProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImageOcrProcessor.class);

    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final int MIN_DIMENSION = 20;
    private static final int MAX_DIMENSION = 10_000;

    private final String tessDataPath;
    private final AtomicInteger processedCount = new AtomicInteger(0);

    public ImageOcrProcessor() {
        this.tessDataPath = resolveTessDataPath();

        newTesseractInstance("eng");
        log.info("ImageOcrProcessor initialized. tessdata path={}", tessDataPath);
    }


    public String extractTextFromImage(String imagePath) {
        return extractTextFromImage(imagePath, "eng");
    }

    public String extractTextFromImage(String imagePath, String language) {
        File file = new File(imagePath);
        if (!file.exists() || !file.isFile()) {
            throw new OcrException("Image not found: " + imagePath);
        }
        try {
            long size = Files.size(file.toPath());
            if (size == 0) throw new OcrException("Image file is empty: " + imagePath);
            if (size > MAX_IMAGE_BYTES) {
                throw new OcrException("Image exceeds max allowed size (" + MAX_IMAGE_BYTES + " bytes): " + imagePath);
            }
        } catch (IOException e) {
            throw new OcrException("Unable to read image file: " + imagePath, e);
        }

        BufferedImage image = readImageRobustly(file);
        return extractText(image, language);
    }

    public String extractText(BufferedImage image) {
        return extractText(image, "eng");
    }


    public String extractText(BufferedImage image, String language) {
        validateImage(image);
        long start = System.currentTimeMillis();

        BufferedImage processed;
        try {
            processed = preprocess(image);
        } catch (Exception e) {
            log.warn("Preprocessing failed, falling back to original image. reason={}", e.getMessage());
            processed = image;
        }

        Tesseract tesseract = newTesseractInstance(language);
        try {
            String text = tesseract.doOCR(processed);
            long elapsedMs = System.currentTimeMillis() - start;
            int count = processedCount.incrementAndGet();
            log.info("OCR completed in {} ms (job #{}, lang={})", elapsedMs, count, language);

            String trimmed = text == null ? "" : text.trim();
            if (trimmed.isEmpty()) {
                log.warn("OCR produced empty text; attempting retry with alternate page segmentation mode");
                trimmed = retryWithFallbackSettings(processed, language);
            }
            return trimmed;
        } catch (TesseractException e) {
            throw new OcrException("OCR engine failed: " + e.getMessage(), e);
        }
    }

    public boolean isSupportedImage(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff")
                || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    public boolean isReady() {
        return tessDataPath != null && new File(tessDataPath).exists();
    }




    private Tesseract newTesseractInstance(String language) {
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(language == null || language.isBlank() ? "eng" : language);
            tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            tesseract.setPageSegMode(3);
            tesseract.setVariable("user_defined_dpi", "300");
            tesseract.setVariable("preserve_interword_spaces", "1");

            return tesseract;
        } catch (Exception e) {
            throw new OcrException("Unable to initialize Tesseract engine", e);
        }
    }

    private String retryWithFallbackSettings(BufferedImage image, String language) {
        try {
            Tesseract tesseract = newTesseractInstance(language);
            tesseract.setPageSegMode(6);
            String text = tesseract.doOCR(image);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.warn("Fallback OCR attempt also failed: {}", e.getMessage());
            return "";
        }
    }


    private String resolveTessDataPath() {
        try {
            ClassPathResource resource = new ClassPathResource("tessdata");
            File asFile = resource.getFile();
            return asFile.getAbsolutePath();
        } catch (IOException notOnFilesystem) {

            try {
                Path tempDir = Files.createTempDirectory("tessdata-");
                copyClasspathTessData(tempDir);
                return tempDir.toAbsolutePath().toString();
            } catch (IOException e) {
                throw new RuntimeException("Unable to resolve tessdata (not on filesystem and extraction failed)", e);
            }
        }
    }

    private void copyClasspathTessData(Path targetDir) throws IOException {

        String[] candidateFiles = {"eng.traineddata", "osd.traineddata"};
        for (String fileName : candidateFiles) {
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("tessdata/" + fileName)) {
                if (in == null) continue;
                Files.copy(in, targetDir.resolve(fileName));
            }
        }
    }


    private BufferedImage readImageRobustly(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                throw new OcrException("Unsupported or corrupt image format: " + file.getName());
            }
            return image;
        } catch (IOException e) {
            throw new OcrException("Failed to read image: " + file.getName(), e);
        }
    }

    private void validateImage(BufferedImage image) {
        if (image == null) throw new OcrException("Image is null.");
        int w = image.getWidth();
        int h = image.getHeight();
        if (w < MIN_DIMENSION || h < MIN_DIMENSION) {
            throw new OcrException("Image resolution too small for reliable OCR: " + w + "x" + h);
        }
        if (w > MAX_DIMENSION || h > MAX_DIMENSION) {
            throw new OcrException("Image resolution too large: " + w + "x" + h);
        }
    }



    private BufferedImage preprocess(BufferedImage image) {
        BufferedImage output = normalizeScale(image);
        output = convertToGray(output);

        double meanBrightness = meanBrightness(output);

        if (meanBrightness < 90) {
            output = adjustBrightness(output, 1.25f, 20f);
        } else if (meanBrightness > 200) {
            output = adjustBrightness(output, 0.9f, -10f); // overexposed / washed out
        }

        output = increaseContrast(output);
        output = sharpen(output);
        output = binarizeOtsu(output); // adaptive threshold instead of fixed cutoff
        output = removeNoiseFast(output);
        return output;
    }


    private BufferedImage normalizeScale(BufferedImage image) {
        int targetDpiFactor = image.getWidth() < 1000 ? 2 : 1;
        if (targetDpiFactor == 1) return image;

        int width = image.getWidth() * targetDpiFactor;
        int height = image.getHeight() * targetDpiFactor;
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }

    private BufferedImage convertToGray(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) return image;
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        op.filter(image, gray);
        return gray;
    }

    private double meanBrightness(BufferedImage gray) {
        Raster raster = gray.getRaster();
        long sum = 0;
        int w = gray.getWidth(), h = gray.getHeight();
        int sampleStep = Math.max(1, (w * h) / 200_000); // sample for large images instead of full scan
        int samples = 0;
        int[] pixel = new int[1];
        for (int y = 0; y < h; y += sampleStep) {
            for (int x = 0; x < w; x += sampleStep) {
                raster.getPixel(x, y, pixel);
                sum += pixel[0];
                samples++;
            }
        }
        return samples == 0 ? 128 : (double) sum / samples;
    }

    private BufferedImage adjustBrightness(BufferedImage image, float scale, float offset) {
        RescaleOp op = new RescaleOp(scale, offset, null);
        return op.filter(image, null);
    }

    private BufferedImage increaseContrast(BufferedImage image) {
        RescaleOp op = new RescaleOp(1.25f, 10, null);
        return op.filter(image, null);
    }

    private BufferedImage sharpen(BufferedImage image) {
        float[] kernelData = {0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f};
        Kernel kernel = new Kernel(3, 3, kernelData);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }


    private BufferedImage binarizeOtsu(BufferedImage gray) {
        int w = gray.getWidth(), h = gray.getHeight();
        int[] histogram = new int[256];
        Raster raster = gray.getRaster();
        int[] pixel = new int[1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                raster.getPixel(x, y, pixel);
                histogram[pixel[0]]++;
            }
        }

        int total = w * h;
        long sumAll = 0;
        for (int i = 0; i < 256; i++) sumAll += (long) i * histogram[i];

        long sumBackground = 0;
        int weightBackground = 0;
        double maxVariance = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) continue;
            int weightForeground = total - weightBackground;
            if (weightForeground == 0) break;

            sumBackground += (long) t * histogram[t];
            double meanBackground = (double) sumBackground / weightBackground;
            double meanForeground = (double) (sumAll - sumBackground) / weightForeground;

            double betweenVariance = (double) weightBackground * weightForeground
                    * Math.pow(meanBackground - meanForeground, 2);

            if (betweenVariance > maxVariance) {
                maxVariance = betweenVariance;
                threshold = t;
            }
        }

        BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster outRaster = binary.getRaster();
        int[] out = new int[1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                raster.getPixel(x, y, pixel);
                out[0] = pixel[0] < threshold ? 0 : 1;
                outRaster.setPixel(x, y, out);
            }
        }
        return binary;
    }


    private BufferedImage removeNoiseFast(BufferedImage binary) {
        int w = binary.getWidth(), h = binary.getHeight();
        Raster src = binary.getRaster();
        BufferedImage cleaned = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster dst = cleaned.getRaster();

        int[] p = new int[1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    src.getPixel(x, y, p);
                    dst.setPixel(x, y, p);
                    continue;
                }
                int blackNeighbours = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        src.getPixel(x + i, y + j, p);
                        if (p[0] == 0) blackNeighbours++;
                    }
                }
                p[0] = blackNeighbours >= 5 ? 0 : 1;
                dst.setPixel(x, y, p);
            }
        }
        return cleaned;
    }



    public static class OcrException extends RuntimeException {
        public OcrException(String message) {
            super(message);
        }

        public OcrException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}