package com.minhhai.amimate.repository;

import com.minhhai.amimate.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByDeviceId(String deviceId);
}
