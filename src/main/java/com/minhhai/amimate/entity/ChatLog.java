package com.minhhai.amimate.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "chat_log",
        uniqueConstraints = @UniqueConstraint(name = "UQ_chat_log_device_client_msg",
                columnNames = {"device_id", "client_message_id"}),
        indexes = @Index(name = "IX_chat_log_device_created",
                columnList = "device_id, created_at, id"))
@Getter
@Setter
@NoArgsConstructor
public class ChatLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false, length = 50)
    private String deviceId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "client_message_id", nullable = false, updatable = false, length = 64)
    private String clientMessageId;

    @Column(name = "user_message", length = 10000)
    private String userMessage;

    @Column(name = "bot_response", length = 10000)
    private String botResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}