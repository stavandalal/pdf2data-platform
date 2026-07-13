package com.pdf2data.platform.chat.repository;

import com.pdf2data.platform.chat.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    List<ChatHistory> findByDocumentIdAndUserIdOrderByCreatedAtAsc(Long documentId, Long userId);
}
