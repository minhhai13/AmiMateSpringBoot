package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.response.XiaozhiSyncResponse;
import com.minhhai.amimate.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class XiaozhiSyncService {
    private static final int MAX_CONTENT_LENGTH = 10_000;
    private static final int PARALLEL_CHATS = 4;

    private final XiaozhiClient xiaozhiClient;
    private final DeviceService deviceService;
    private final ChatLogService chatLogService;

    private final Duration cooldown;
    private final Set<String> syncing = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> lastSyncedAt = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_CHATS, runnable -> {
        Thread thread = new Thread(runnable, "xiaozhi-sync");
        thread.setDaemon(true);
        return thread;
    });

    public XiaozhiSyncService(
            XiaozhiClient xiaozhiClient,
            DeviceService deviceService,
            ChatLogService chatLogService,
            @Value("${xiaozhi.sync.cooldown-seconds:30}") long cooldownSeconds) {
        this.cooldown = Duration.ofSeconds(Math.max(cooldownSeconds, 0));
        this.xiaozhiClient = xiaozhiClient;
        this.deviceService = deviceService;
        this.chatLogService = chatLogService;
    }

    public XiaozhiSyncResponse sync(Long userId, String deviceId) {
        deviceService.requireOwned(userId, deviceId);
        long agentId = parseAgentId(deviceId);

        if (!syncing.add(deviceId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Robot đang được đồng bộ. Vui lòng thử lại sau.");
        }
        try {
            // Bấm đồng bộ liên tục trong thời gian ngắn thì không gọi lại Xiaozhi.
            Instant last = lastSyncedAt.get(deviceId);
            if (last != null && Duration.between(last, Instant.now()).compareTo(cooldown) < 0) {
                return new XiaozhiSyncResponse(deviceId, 0, 0, 0, 0);
            }
            XiaozhiSyncResponse response = doSync(deviceId, agentId);
            lastSyncedAt.put(deviceId, Instant.now());
            return response;
        } finally {
            syncing.remove(deviceId);
        }
    }

    private XiaozhiSyncResponse doSync(String deviceId, long agentId) {
        ChatLogService.SyncState state = chatLogService.syncState(deviceId);
        Map<Long, Integer> synced = state.synced();
        Map<Long, Long> lastMessageIds = state.lastMessageIds();
        // Danh sách chat mới nhất trước: dừng ở trang đầu tiên đã đồng bộ hết, trừ khi còn chat lỗi chưa xong.
        List<XiaozhiClient.Chat> allChats = xiaozhiClient.listRecentChats(
                agentId, chat -> !state.hasUnsynced() && synced.containsKey(chat.id()));
        long latestSynced = synced.keySet().stream().mapToLong(Long::longValue).max().orElse(-1L);

        // Mỗi lần nói chuyện là một chat mới nên chat cũ không đổi. Chỉ tải tin nhắn của:
        // chat chưa đồng bộ, và chat đã đồng bộ mới nhất nếu msg_count đã thay đổi.
        List<XiaozhiClient.Chat> toFetch = allChats.stream()
                .filter(chat -> {
                    Integer stored = synced.get(chat.id());
                    if (stored == null) {
                        return true;
                    }
                    return chat.id() == latestSynced
                            && (chat.msgCount() < 0 || chat.msgCount() != stored);
                })
                .sorted(Comparator.comparingLong(XiaozhiClient.Chat::id))
                .toList();

        // Tiêu đề có sẵn trong danh sách chat nên cập nhật cho mọi chat mà không tốn thêm request.
        Map<Long, ChatLogService.SessionUpdate> titles = new HashMap<>();
        allChats.forEach(chat -> titles.put(
                chat.id(), new ChatLogService.SessionUpdate(chat.title(), null, null)));
        chatLogService.upsertSessions(deviceId, titles);

        List<ChatResult> results = fetchAll(deviceId, toFetch, lastMessageIds);

        int messagesFetched = 0;
        int imported = 0;
        int skipped = 0;
        for (ChatResult result : results) {
            messagesFetched += result.messages();
            imported += result.imported();
            skipped += result.skipped();
        }
        return new XiaozhiSyncResponse(deviceId, toFetch.size(), messagesFetched, imported, skipped);
    }

    private List<ChatResult> fetchAll(
            String deviceId, List<XiaozhiClient.Chat> chats, Map<Long, Long> lastMessageIds) {
        if (chats.size() <= 1) {
            return chats.stream().map(chat -> syncChat(deviceId, chat, lastMessageIds.get(chat.id()))).toList();
        }
        List<CompletableFuture<ChatResult>> futures = chats.stream()
                .map(chat -> CompletableFuture.supplyAsync(
                        () -> syncChat(deviceId, chat, lastMessageIds.get(chat.id())), pool))
                .toList();
        // Chờ tất cả xong rồi mới báo lỗi: chat nào lưu được thì đã lưu và đánh dấu đã đồng bộ,
        // chat lỗi chưa có msg_count nên lần sau sẽ được thử lại.
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).exceptionally(e -> null).join();
        List<ChatResult> results = new ArrayList<>();
        for (CompletableFuture<ChatResult> future : futures) {
            try {
                results.add(future.join());
            } catch (java.util.concurrent.CompletionException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw e;
            }
        }
        return results;
    }

    private ChatResult syncChat(String deviceId, XiaozhiClient.Chat chat, Long lastMessageId) {
        // Đã có mốc thì chỉ tải các tin sau mốc; chat mới thì tải toàn bộ.
        List<XiaozhiClient.Message> messages;
        if (chat.msgCount() == 0) {
            messages = List.of();
        } else if (lastMessageId != null) {
            messages = xiaozhiClient.listMessagesAfter(chat.id(), lastMessageId);
        } else {
            messages = xiaozhiClient.listAllMessages(chat.id());
        }
        Parsed parsed = parse(chat.id(), messages);
        var result = chatLogService.importChat(deviceId, Long.toString(chat.id()), parsed.logs());
        // Chỉ đánh dấu đã đồng bộ sau khi lưu xong tin nhắn.
        chatLogService.upsertSessions(deviceId, Map.of(
                chat.id(), new ChatLogService.SessionUpdate(null, chat.msgCount(), parsed.lastMessageId())));
        return new ChatResult(messages.size(), result.imported(), result.skipped());
    }

    /**
     * Ghép các tin nhắn user/assistant liên tiếp thành từng lượt hỏi đáp. lastMessageId là mốc để
     * lần sau tải tiếp: câu hỏi cuối chưa có trả lời không tính vào mốc, nên sẽ được tải lại để ghép.
     */
    private Parsed parse(long chatId, List<XiaozhiClient.Message> messages) {
        List<ChatLogService.ImportedLog> logs = new ArrayList<>();
        XiaozhiClient.Message pendingUser = null;
        Long consumed = null;
        for (XiaozhiClient.Message message : messages) {
            if ("user".equals(message.role())) {
                if (pendingUser != null) {
                    logs.add(toLog(chatId, pendingUser, null));
                    consumed = pendingUser.id();
                }
                pendingUser = message;
            } else if ("assistant".equals(message.role())) {
                if (pendingUser == null) {
                    logs.add(toAssistantOnlyLog(chatId, message));
                } else {
                    logs.add(toLog(chatId, pendingUser, message));
                    pendingUser = null;
                }
                consumed = message.id();
            }
        }
        if (pendingUser != null) {
            logs.add(toLog(chatId, pendingUser, null));
        }
        return new Parsed(logs, consumed);
    }

    private ChatLogService.ImportedLog toLog(
            long chatId, XiaozhiClient.Message user, XiaozhiClient.Message assistant) {
        requireTimestamp(user);
        validateMessageLength(user.content());
        validateMessageLength(assistant == null ? null : assistant.content());
        return new ChatLogService.ImportedLog(
                "xz-" + chatId + "-" + user.id(),
                user.content(),
                assistant == null ? null : assistant.content(),
                user.createdAt());
    }

    private ChatLogService.ImportedLog toAssistantOnlyLog(long chatId, XiaozhiClient.Message assistant) {
        requireTimestamp(assistant);
        validateMessageLength(assistant.content());
        return new ChatLogService.ImportedLog(
                "xz-" + chatId + "-" + assistant.id(), null, assistant.content(), assistant.createdAt());
    }

    private static long parseAgentId(String deviceId) {
        try {
            return Long.parseLong(deviceId);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ID robot không phải agentId Xiaozhi hợp lệ.");
        }
    }

    private static void requireTimestamp(XiaozhiClient.Message message) {
        Instant createdAt = message.createdAt();
        if (createdAt == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Thời gian hội thoại Xiaozhi không hợp lệ.");
        }
    }

    private static void validateMessageLength(String content) {
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Nội dung hội thoại Xiaozhi vượt quá giới hạn lưu trữ.");
        }
    }

    private record Parsed(List<ChatLogService.ImportedLog> logs, Long lastMessageId) {}

    private record ChatResult(int messages, int imported, int skipped) {}
}
