package com.minhhai.amimate.dto.response;

import java.time.Instant;

public record ChatSessionResponse(
        String chatId,
        Instant lastMessageAt,
        long turnCount) {
}
