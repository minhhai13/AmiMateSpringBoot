package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.response.ChatLogResponse;
import com.minhhai.amimate.dto.response.ChatSessionResponse;
import com.minhhai.amimate.dto.response.PageResponse;
import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.entity.ChatSession;
import com.minhhai.amimate.repository.ChatLogRepository;
import com.minhhai.amimate.repository.ChatSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ChatLogRepository chatLogs;
    private final ChatSessionRepository chatSessions;
    private final DeviceService deviceService;

    public ChatLogService(
            ChatLogRepository chatLogs,
            ChatSessionRepository chatSessions,
            DeviceService deviceService) {
        this.chatLogs = chatLogs;
        this.chatSessions = chatSessions;
        this.deviceService = deviceService;
    }

    public record ImportedLog(
            String clientMessageId, String userMessage, String botResponse, Instant createdAt) {}

    public record ImportResult(int imported, int skipped) {}

    public record SessionUpdate(String title, Integer remoteMsgCount) {}

    /**
     * Ghi cả một chat trong một transaction: 1 SELECT + 1 saveAll thay vì mỗi tin nhắn một cặp truy vấn.
     * Bản ghi đã có chỉ được bổ sung câu trả lời nếu lần trước mới chỉ có câu hỏi.
     */
    @Transactional
    public ImportResult importChat(String deviceId, String chatId, List<ImportedLog> logs) {
        Map<String, ChatLog> existing = new HashMap<>();
        chatLogs.findByDeviceIdAndSessionId(deviceId, chatId)
                .forEach(l -> existing.put(l.getClientMessageId(), l));

        var toSave = new ArrayList<ChatLog>();
        int imported = 0;
        int skipped = 0;
        for (ImportedLog item : logs) {
            ChatLog current = existing.get(item.clientMessageId());
            if (current == null) {
                ChatLog log = new ChatLog();
                log.setDeviceId(deviceId);
                log.setSessionId(chatId);
                log.setClientMessageId(item.clientMessageId());
                log.setUserMessage(item.userMessage());
                log.setBotResponse(item.botResponse());
                log.setCreatedAt(item.createdAt());
                existing.put(item.clientMessageId(), log);
                toSave.add(log);
                imported++;
            } else if (current.getBotResponse() == null && item.botResponse() != null) {
                current.setBotResponse(item.botResponse());
                if (!toSave.contains(current)) {
                    toSave.add(current);
                }
                imported++;
            } else {
                skipped++;
            }
        }
        chatLogs.saveAll(toSave);
        return new ImportResult(imported, skipped);
    }

    /**
     * Tạo hoặc cập nhật thông tin chat (tiêu đề, msg_count). Giá trị null/trống nghĩa là giữ nguyên;
     * không tạo dòng mới nếu không có gì để ghi.
     */
    @Transactional
    public void upsertSessions(String deviceId, Map<Long, SessionUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        Map<String, ChatSession> existing = new HashMap<>();
        chatSessions.findByDeviceId(deviceId).forEach(s -> existing.put(s.getChatId(), s));

        var changed = new ArrayList<ChatSession>();
        updates.forEach((chatId, update) -> {
            String title = normalizeTitle(update.title());
            Integer count = update.remoteMsgCount();
            if (title == null && count == null) {
                return;
            }
            ChatSession session = existing.get(Long.toString(chatId));
            boolean dirty = false;
            if (session == null) {
                session = new ChatSession();
                session.setDeviceId(deviceId);
                session.setChatId(Long.toString(chatId));
                dirty = true;
            }
            if (title != null && !title.equals(session.getSummary())) {
                session.setSummary(title);
                dirty = true;
            }
            if (count != null && !count.equals(session.getRemoteMsgCount())) {
                session.setRemoteMsgCount(count);
                dirty = true;
            }
            if (dirty) {
                changed.add(session);
            }
        });
        chatSessions.saveAll(changed);
    }

    private static String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String title = raw.strip();
        return title.length() > ChatSession.MAX_SUMMARY_LENGTH
                ? title.substring(0, ChatSession.MAX_SUMMARY_LENGTH)
                : title;
    }

    /** chatId (Xiaozhi) -> msg_count đã đồng bộ; chat chưa đồng bộ tin nhắn thì không có trong map. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> syncedMsgCounts(String deviceId) {
        Map<Long, Integer> result = new HashMap<>();
        for (ChatSession s : chatSessions.findByDeviceId(deviceId)) {
            if (s.getRemoteMsgCount() == null) {
                continue;
            }
            try {
                result.put(Long.parseLong(s.getChatId()), s.getRemoteMsgCount());
            } catch (NumberFormatException ignored) {
                // chatId không phải số: không thuộc Xiaozhi, bỏ qua
            }
        }
        return result;
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