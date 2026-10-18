package com.minhhai.amimate.dto.response;

import com.minhhai.amimate.entity.ChatLog;

import java.time.Instant;

public record ChatLogResponse(

        Long id,

        String deviceId,

        String sessionId,

        String clientMessageId,

        String userMessage,

        String botResponse,

        Instant createdAt) {

    public static ChatLogResponse from(ChatLog c) {

        return new ChatLogResponse(c.getId(), c.getDeviceId(), c.getSessionId(),

                c.getClientMessageId(), c.getUserMessage(), c.getBotResponse(), c.getCreatedAt());

    }
}