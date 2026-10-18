package com.minhhai.amimate.controller;

import com.minhhai.amimate.dto.response.ChatLogResponse;
import com.minhhai.amimate.dto.request.CreateChatLogRequest;
import com.minhhai.amimate.dto.response.PageResponse;
import com.minhhai.amimate.service.ChatLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logs")
public class ChatLogController {
    private final ChatLogService chatLogService;

    public ChatLogController(ChatLogService chatLogService) {
        this.chatLogService = chatLogService;
    }

    @PostMapping
    public ResponseEntity<ChatLogResponse> createLog(@AuthenticationPrincipal String deviceId,
                                                     @Valid @RequestBody CreateChatLogRequest req) {
        var result = chatLogService.save(deviceId, req);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ChatLogResponse.from(result.log()));
    }

    @GetMapping
    public PageResponse<ChatLogResponse> getLogs(@AuthenticationPrincipal Long userId,
                                                 @RequestParam String deviceId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return chatLogService.list(userId, deviceId, page, size);
    }
}