package com.pdf2data.platform.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class AiExtractionService {

    @Value("${gemini.api.key}")
    private String apiKey;

    public String extractDataWithAI(String text, String userPrompt) {


        String url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + apiKey;
        RestTemplate restTemplate = new RestTemplate();


        Map<String, Object> body = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        List<Map<String, String>> parts = new ArrayList<>();

        Map<String, String> part = new HashMap<>();

        part.put("text", userPrompt + "\n\nData to extract:\n" + text);
        parts.add(part);

        content.put("parts", parts);
        contents.add(content);

        body.put("contents", contents);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            return "Error calling Gemini API: " + e.getMessage();
        }
    }
}