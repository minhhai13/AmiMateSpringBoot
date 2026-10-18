package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.response.ChatLogResponse;
import com.minhhai.amimate.dto.request.CreateChatLogRequest;
import com.minhhai.amimate.dto.response.PageResponse;
import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.repository.ChatLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ChatLogRepository chatLogs;
    private final DeviceService deviceService;

    public ChatLogService(ChatLogRepository chatLogs, DeviceService deviceService) {
        this.chatLogs = chatLogs;
        this.deviceService = deviceService;
    }

    public record SaveResult(ChatLog log, boolean created) {}

    public SaveResult save(String deviceId, CreateChatLogRequest req) {
        var existing = chatLogs.findByDeviceIdAndClientMessageId(deviceId, req.clientMessageId());
        if (existing.isPresent()) {
            return new SaveResult(existing.get(), false);
        }

        ChatLog log = new ChatLog();
        log.setDeviceId(deviceId);
        log.setSessionId(req.sessionId());
        log.setClientMessageId(req.clientMessageId());
        log.setUserMessage(req.userMessage());
        log.setBotResponse(req.botResponse());

        try {
            return new SaveResult(chatLogs.save(log), true);
        } catch (DataIntegrityViolationException e) {
            return chatLogs.findByDeviceIdAndClientMessageId(deviceId, req.clientMessageId())
                    .map(l -> new SaveResult(l, false))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatLogResponse> list(Long userId, String deviceId, int page, int size) {
        deviceService.requireOwned(userId, deviceId);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        var pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<ChatLog> result = chatLogs.findByDeviceId(deviceId, pageable);

        return new PageResponse<>(
                result.getContent().stream().map(ChatLogResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }
}