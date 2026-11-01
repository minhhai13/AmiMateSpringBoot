package com.minhhai.amimate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_session",
        uniqueConstraints = @UniqueConstraint(name = "UQ_chat_session_device_chat",
                columnNames = {"device_id", "chat_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {
    public static final int MAX_SUMMARY_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false, length = 50)
    private String deviceId;

    @Column(name = "chat_id", nullable = false, updatable = false, length = 100)
    private String chatId;

    @Column(name = "summary", length = MAX_SUMMARY_LENGTH)
    private String summary;

    /** msg_count của Xiaozhi tại lần sync gần nhất thành công; null = chưa đồng bộ tin nhắn. */
    @Column(name = "remote_msg_count")
    private Integer remoteMsgCount;

    /** id tin nhắn Xiaozhi cuối cùng đã được ghép vào log; lần sau chỉ tải các tin sau mốc này. */
    @Column(name = "last_message_id")
    private Long lastMessageId;
}
