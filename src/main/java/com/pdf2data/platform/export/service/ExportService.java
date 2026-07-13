package com.pdf2data.platform.export.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.document.entity.MongoExtractionResult;
import com.pdf2data.platform.document.repository.MongoExtractionRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
@Service
public class ExportService {

    private final MongoExtractionRepository mongoExtractionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public ExportService(MongoExtractionRepository mongoExtractionRepository) {
        this.mongoExtractionRepository = mongoExtractionRepository;
    }
    public Resource exportAsJson(Long documentId) throws Exception {

        MongoExtractionResult result = mongoExtractionRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        String json = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(result.getParsedFields());

        return new ByteArrayResource(
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
    public Resource exportAsCsv(Long documentId) throws Exception {

        MongoExtractionResult result = mongoExtractionRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CSVPrinter csvPrinter = new CSVPrinter(
                new OutputStreamWriter(outputStream),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Field", "Value")
                        .build()
        );

        for (Map.Entry<String, Object> entry : result.getParsedFields().entrySet()) {

            csvPrinter.printRecord(
                    entry.getKey(),
                    entry.getValue()
            );

        }

        csvPrinter.flush();
        csvPrinter.close();

        return new ByteArrayResource(outputStream.toByteArray());
    }
    public Resource exportAsExcel(Long documentId) throws Exception {

        MongoExtractionResult result = mongoExtractionRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Workbook workbook = new XSSFWorkbook();

        Sheet sheet = workbook.createSheet("Extracted Data");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Field");
        header.createCell(1).setCellValue("Value");

        int rowNum = 1;

        for (Map.Entry<String, Object> entry : result.getParsedFields().entrySet()) {

            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(entry.getKey());

            row.createCell(1).setCellValue(
                    String.valueOf(entry.getValue())
            );

        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        workbook.write(outputStream);

        workbook.close();

        return new ByteArrayResource(outputStream.toByteArray());
    }
    public Resource exportAsSql(Long documentId) throws Exception {

        MongoExtractionResult result = mongoExtractionRepository
                .findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        StringBuilder sql = new StringBuilder();

        sql.append("-- PDF2Data SQL Export\n\n");

        for (Map.Entry<String, Object> entry : result.getParsedFields().entrySet()) {

            String key = entry.getKey();

            String value = String.valueOf(entry.getValue())
                    .replace("'", "''");

            sql.append("INSERT INTO extracted_data (`field`, `value`) VALUES ('")
                    .append(key)
                    .append("', '")
                    .append(value)
                    .append("');\n");
        }

        return new ByteArrayResource(
                sql.toString().getBytes(StandardCharsets.UTF_8)
        );
    }
}