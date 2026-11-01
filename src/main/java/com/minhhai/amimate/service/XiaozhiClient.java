package com.minhhai.amimate.service;

import java.time.Instant;
import java.util.List;

public interface XiaozhiClient {
    /** msgCount = -1 nếu Xiaozhi không trả msg_count. */
    record Chat(long id, String title, int msgCount) {}

    record Message(long id, String role, String content, Instant createdAt) {}

    List<Chat> listAllChats(long agentId);

    List<Message> listAllMessages(long chatId);
}
