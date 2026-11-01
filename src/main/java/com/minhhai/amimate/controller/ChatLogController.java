package com.minhhai.amimate.controller;

import com.minhhai.amimate.dto.response.ChatLogResponse;
import com.minhhai.amimate.dto.response.ChatSessionResponse;
import com.minhhai.amimate.dto.response.PageResponse;
import com.minhhai.amimate.dto.response.XiaozhiSyncResponse;
import com.minhhai.amimate.service.ChatLogService;
import com.minhhai.amimate.service.XiaozhiSyncService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/logs")
public class ChatLogController {
    private final ChatLogService chatLogService;
    private final XiaozhiSyncService xiaozhiSyncService;

    public ChatLogController(
            ChatLogService chatLogService,
            XiaozhiSyncService xiaozhiSyncService) {
        this.chatLogService = chatLogService;
        this.xiaozhiSyncService = xiaozhiSyncService;
    }

    @GetMapping
    public PageResponse<ChatLogResponse> getLogs(@AuthenticationPrincipal Long userId,
                                                 @RequestParam String deviceId,
                                                 @RequestParam(required = false) String sessionId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return chatLogService.list(userId, deviceId, sessionId, page, size);
    }

    @GetMapping("/sessions")
    public PageResponse<ChatSessionResponse> getSessions(
            @AuthenticationPrincipal Long userId,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return chatLogService.listSessions(userId, deviceId, page, size);
    }

    @PostMapping("/sync-xiaozhi")
    public XiaozhiSyncResponse syncXiaozhi(
            @AuthenticationPrincipal Long userId,
            @RequestParam String deviceId) {
        return xiaozhiSyncService.sync(userId, deviceId);
    }
}