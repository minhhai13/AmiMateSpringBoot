package com.minhhai.amimate.dto.request;
import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;

public record CreateChatLogRequest(

        @NotBlank @Size(max = 64) String clientMessageId,

        @NotBlank @Size(max = 100) String sessionId,

        @Size(max = 10000) String userMessage,

        @Size(max = 10000) String botResponse) {

}



