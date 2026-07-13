package com.pdf2data.platform.export.controller;

import com.pdf2data.platform.export.service.ExportService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = "*")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/json/{documentId}")
    public ResponseEntity<Resource> exportJson(@PathVariable Long documentId) throws Exception {

        Resource resource = exportService.exportAsJson(documentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=document_" + documentId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    @GetMapping("/csv/{documentId}")
    public ResponseEntity<Resource> exportCsv(
            @PathVariable Long documentId
    ) throws Exception {

        Resource resource = exportService.exportAsCsv(documentId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=document_" + documentId + ".csv"
                )
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    @GetMapping("/excel/{documentId}")
    public ResponseEntity<Resource> exportExcel(
            @PathVariable Long documentId
    ) throws Exception {

        Resource resource = exportService.exportAsExcel(documentId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=document_" + documentId + ".xlsx"
                )
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @GetMapping("/sql/{documentId}")
    public ResponseEntity<Resource> exportSql(
            @PathVariable Long documentId
    ) throws Exception {

        Resource resource = exportService.exportAsSql(documentId);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=document_" + documentId + ".sql"
                )
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }
}