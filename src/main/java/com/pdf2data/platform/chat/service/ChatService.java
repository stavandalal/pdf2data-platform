package com.pdf2data.platform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2data.platform.chat.dto.ChatResponse;
import com.pdf2data.platform.chat.entity.ChatHistory;
import com.pdf2data.platform.chat.repository.ChatHistoryRepository;
import com.pdf2data.platform.document.entity.MongoExtractionResult;
import com.pdf2data.platform.document.repository.MongoExtractionRepository;
import com.pdf2data.platform.document.service.AiExtractionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final int MAX_HISTORY_MESSAGES = 10;

    private final ChatHistoryRepository chatHistoryRepository;
    private final MongoExtractionRepository mongoExtractionRepository;
    private final AiExtractionService aiExtractionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(ChatHistoryRepository chatHistoryRepository,
                        MongoExtractionRepository mongoExtractionRepository,
                        AiExtractionService aiExtractionService) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.mongoExtractionRepository = mongoExtractionRepository;
        this.aiExtractionService = aiExtractionService;
    }

    public ChatResponse ask(Long documentId, Long userId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new RuntimeException("Chat message must not be empty.");
        }

        MongoExtractionResult extraction = mongoExtractionRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("No extraction data found for document id=" + documentId));

        chatHistoryRepository.save(ChatHistory.builder()
                .documentId(documentId)
                .userId(userId)
                .role(ChatHistory.Role.USER)
                .message(userMessage)
                .build());

        String context = buildContext(extraction);
        String history = buildHistoryTranscript(documentId, userId);

        String prompt = "You are an assistant answering questions ONLY about the following extracted document data. "
                + "If the answer is not present in the data, say you don't have that information.\n\n"
                + "DOCUMENT DATA (JSON):\n" + context + "\n\n"
                + (history.isBlank() ? "" : "PRIOR CONVERSATION:\n" + history + "\n\n")
                + "USER QUESTION: " + userMessage;

        String reply = aiExtractionService.generateText(prompt);
        if (reply == null || reply.startsWith("Error")) {
            reply = "Sorry, I could not process that question right now. Please try again.";
        }

        chatHistoryRepository.save(ChatHistory.builder()
                .documentId(documentId)
                .userId(userId)
                .role(ChatHistory.Role.ASSISTANT)
                .message(reply)
                .build());

        return ChatResponse.builder().documentId(documentId).reply(reply).build();
    }

    public List<ChatHistory> getHistory(Long documentId, Long userId) {
        return chatHistoryRepository.findByDocumentIdAndUserIdOrderByCreatedAtAsc(documentId, userId);
    }

    private String buildContext(MongoExtractionResult extraction) {
        try {
            return objectMapper.writeValueAsString(extraction.getParsedFields());
        } catch (Exception e) {
            return extraction.getRawJsonData() != null ? extraction.getRawJsonData() : "{}";
        }
    }

    private String buildHistoryTranscript(Long documentId, Long userId) {
        List<ChatHistory> history = chatHistoryRepository.findByDocumentIdAndUserIdOrderByCreatedAtAsc(documentId, userId);
        int from = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        return history.subList(from, history.size()).stream()
                .map(h -> h.getRole().name() + ": " + h.getMessage())
                .collect(Collectors.joining("\n"));
    }
}
