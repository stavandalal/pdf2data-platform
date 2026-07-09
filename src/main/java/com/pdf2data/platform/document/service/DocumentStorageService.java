package com.pdf2data.platform.document.service;

import com.pdf2data.platform.document.entity.*;
import com.pdf2data.platform.document.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class DocumentStorageService {

    private final DocumentRepository documentRepository;
    private final ExtractionResultRepository relationalRepo;
    private final MongoExtractionRepository mongoRepo;

    public DocumentStorageService(DocumentRepository docRepo,
                                  ExtractionResultRepository relRepo,
                                  MongoExtractionRepository mongoRepo) {
        this.documentRepository = docRepo;
        this.relationalRepo = relRepo;
        this.mongoRepo = mongoRepo;
    }

    public void saveAll(Document doc, String rawJson, Map<String, Object> parsedFields) {

        Long savedDocId = null;
        boolean mysqlSaved = false;
        boolean mongoSaved = false;

        // ---------- MySQL ----------
        try {

            Document savedDoc = documentRepository.save(doc);
            savedDocId = savedDoc.getId();

            relationalRepo.save(
                    ExtractionResult.builder()
                            .rawJsonData(rawJson)
                            .document(savedDoc)
                            .build()
            );

            mysqlSaved = true;

        } catch (Exception e) {

            System.err.println("========== MYSQL ERROR ==========");
            e.printStackTrace();
            System.err.println("================================");

        }

        // ---------- MongoDB ----------
        try {

            System.out.println("Saving to Mongo...");
            System.out.println("Document ID = " + savedDocId);

            mongoRepo.save(
                    MongoExtractionResult.builder()
                            .documentId(savedDocId)
                            .fileName(doc.getFileName())
                            .rawJsonData(rawJson)
                            .parsedFields(parsedFields)
                            .processedAt(LocalDateTime.now())
                            .build()
            );

            mongoSaved = true;

        } catch (Exception e) {

            System.err.println("========== MONGODB ERROR ==========");
            e.printStackTrace();
            System.err.println("==================================");

        }

        if (!mysqlSaved && !mongoSaved) {
            throw new RuntimeException("CRITICAL: Both databases are offline.");
        }
    }
}