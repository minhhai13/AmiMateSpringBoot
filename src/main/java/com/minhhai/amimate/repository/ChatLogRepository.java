package com.minhhai.amimate.repository;
import com.minhhai.amimate.entity.ChatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ChatLogRepository extends JpaRepository<ChatLog, Long> {
    Page<ChatLog> findByDeviceId(String deviceId, Pageable pageable);
    Optional<ChatLog> findByDeviceIdAndClientMessageId(String deviceId, String clientMessageId);
}