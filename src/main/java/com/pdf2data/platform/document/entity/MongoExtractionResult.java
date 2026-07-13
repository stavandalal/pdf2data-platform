package com.pdf2data.platform.document.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "dynamic_extractions")
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class MongoExtractionResult {

    @Id
    private String id;


    private Long documentId;

    private String fileName;

    private String rawJsonData;

    private Map<String, Object> parsedFields;

    private LocalDateTime processedAt;
}
