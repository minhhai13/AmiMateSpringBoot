package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.response.ChatLogResponse;
import com.minhhai.amimate.dto.response.ChatSessionResponse;
import com.minhhai.amimate.dto.response.PageResponse;
import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.repository.ChatLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ChatLogRepository chatLogs;
    private final DeviceService deviceService;

    public ChatLogService(ChatLogRepository chatLogs, DeviceService deviceService) {
        this.chatLogs = chatLogs;
        this.deviceService = deviceService;
    }

    public boolean saveImported(
            String deviceId,
            String sessionId,
            String clientMessageId,
            String userMessage,
            String botResponse,
            Instant createdAt) {
        var existing = chatLogs.findByDeviceIdAndClientMessageId(deviceId, clientMessageId);
        if (existing.isPresent()) {
            return completeBotResponse(existing.get(), botResponse);
        }

        ChatLog log = new ChatLog();
        log.setDeviceId(deviceId);
        log.setSessionId(sessionId);
        log.setClientMessageId(clientMessageId);
        log.setUserMessage(userMessage);
        log.setBotResponse(botResponse);
        log.setCreatedAt(createdAt);

        try {
            chatLogs.saveAndFlush(log);
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (chatLogs.findByDeviceIdAndClientMessageId(deviceId, clientMessageId).isPresent()) {
                return false;
            }
            throw exception;
        }
    }

    /** Lần sync trước chỉ có câu hỏi; nếu giờ đã có câu trả lời thì bổ sung. */
    private boolean completeBotResponse(ChatLog existing, String botResponse) {
        if (existing.getBotResponse() == null && botResponse != null) {
            existing.setBotResponse(botResponse);
            chatLogs.save(existing);
            return true;
        }
        return false;
    }

    /** Các chatId (Xiaozhi) đã có log của robot này. */
    @Transactional(readOnly = true)
    public Set<Long> knownChatIds(String deviceId) {
        return chatLogs.findSessionIdsByDeviceId(deviceId).stream()
                .map(id -> {
                    try {
                        return Long.parseLong(id);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatLogResponse> list(Long userId, String deviceId, int page, int size) {
        return list(userId, deviceId, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatLogResponse> list(
            Long userId,
            String deviceId,
            String sessionId,
            int page,
            int size) {
        deviceService.requireOwned(userId, deviceId);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        var pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<ChatLog> result = sessionId == null
                ? chatLogs.findByDeviceId(deviceId, pageable)
                : chatLogs.findByDeviceIdAndSessionId(deviceId, sessionId, pageable);

        return new PageResponse<>(
                result.getContent().stream().map(ChatLogResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatSessionResponse> listSessions(
            Long userId,
            String deviceId,
            int page,
            int size) {
        deviceService.requireOwned(userId, deviceId);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var pageable = PageRequest.of(safePage, safeSize);
        Page<ChatSessionResponse> result = chatLogs.findSessionsByDeviceId(deviceId, pageable);

        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext());
    }
}