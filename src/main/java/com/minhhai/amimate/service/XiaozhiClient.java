package com.minhhai.amimate.service;

import java.time.Instant;
import java.util.List;

public interface XiaozhiClient {
    record Chat(long id) {}

    record Message(long id, String role, String content, Instant createdAt) {}

    List<Chat> listAllChats();

    List<Message> listAllMessages(long chatId);
}
