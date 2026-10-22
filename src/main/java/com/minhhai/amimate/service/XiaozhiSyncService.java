package com.minhhai.amimate.service;

import com.minhhai.amimate.dto.response.XiaozhiSyncResponse;
import com.minhhai.amimate.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class XiaozhiSyncService {
    private final XiaozhiClient xiaozhiClient;
    private final DeviceService deviceService;
    private final ChatLogService chatLogService;

    public XiaozhiSyncService(
            XiaozhiClient xiaozhiClient,
            DeviceService deviceService,
            ChatLogService chatLogService) {
        this.xiaozhiClient = xiaozhiClient;
        this.deviceService = deviceService;
        this.chatLogService = chatLogService;
    }

    public XiaozhiSyncResponse sync(Long userId, String deviceId) {
        deviceService.requireOwned(userId, deviceId);

        List<XiaozhiClient.Chat> chats = xiaozhiClient.listAllChats();
        int messagesFetched = 0;
        int imported = 0;
        int skipped = 0;

        for (XiaozhiClient.Chat chat : chats) {
            List<XiaozhiClient.Message> messages =
                    xiaozhiClient.listAllMessages(chat.id());
            messagesFetched += messages.size();

            PendingMessage pendingUser = null;
            for (XiaozhiClient.Message message : messages) {
                if ("user".equals(message.role())) {
                    if (pendingUser != null) {
                        if (save(deviceId, chat.id(), pendingUser, null)) {
                            imported++;
                        } else {
                            skipped++;
                        }
                    }
                    pendingUser = new PendingMessage(message.id(), message.content(), message.createdAt());
                } else if ("assistant".equals(message.role())) {
                    if (pendingUser == null) {
                        if (saveAssistantOnly(deviceId, chat.id(), message)) {
                            imported++;
                        } else {
                            skipped++;
                        }
                    } else {
                        if (save(deviceId, chat.id(), pendingUser, message)) {
                            imported++;
                        } else {
                            skipped++;
                        }
                        pendingUser = null;
                    }
                }
            }

            if (pendingUser != null) {
                if (save(deviceId, chat.id(), pendingUser, null)) {
                    imported++;
                } else {
                    skipped++;
                }
            }
        }

        return new XiaozhiSyncResponse(
                deviceId, chats.size(), messagesFetched, imported, skipped);
    }

    private boolean save(
            String deviceId,
            long chatId,
            PendingMessage user,
            XiaozhiClient.Message assistant) {
        Instant createdAt = user.createdAt();
        if (createdAt == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Thời gian hội thoại Xiaozhi không hợp lệ.");
        }
        validateMessageLength(user.content());
        validateMessageLength(assistant == null ? null : assistant.content());
        return chatLogService.saveImported(
                deviceId,
                Long.toString(chatId),
                "xz-" + chatId + "-" + user.id(),
                user.content(),
                assistant == null ? null : assistant.content(),
                createdAt);
    }

    private boolean saveAssistantOnly(
            String deviceId,
            long chatId,
            XiaozhiClient.Message assistant) {
        if (assistant.createdAt() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Thời gian hội thoại Xiaozhi không hợp lệ.");
        }
        validateMessageLength(assistant.content());
        return chatLogService.saveImported(
                deviceId,
                Long.toString(chatId),
                "xz-" + chatId + "-" + assistant.id(),
                null,
                assistant.content(),
                assistant.createdAt());
    }

    private void validateMessageLength(String content) {
        if (content != null && content.length() > 10_000) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Nội dung hội thoại Xiaozhi vượt quá giới hạn lưu trữ.");
        }
    }

    private record PendingMessage(long id, String content, Instant createdAt) {}
}
