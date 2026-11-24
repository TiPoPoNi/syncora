package com.prs.repository;

import com.prs.entity.AssistantChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantChatMessageRepository extends JpaRepository<AssistantChatMessage, Long> {
    List<AssistantChatMessage> findByProjectIdAndUsernameOrderByCreatedAtAsc(Long projectId, String username);
    void deleteByProjectIdAndUsername(Long projectId, String username);
}
