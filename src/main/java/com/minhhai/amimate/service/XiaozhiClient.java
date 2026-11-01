package com.minhhai.amimate.service;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

public interface XiaozhiClient {
    /** msgCount = -1 nếu Xiaozhi không trả msg_count. */
    record Chat(long id, String title, int msgCount) {}

    record Message(long id, String role, String content, Instant createdAt) {}

    /**
     * Lấy danh sách chat (mới nhất trước). Dừng sớm sau trang đầu tiên mà mọi chat đều
     * {@code alreadySynced}, miễn là trang đó đúng thứ tự id giảm dần; ngược lại lấy hết.
     */
    List<Chat> listRecentChats(long agentId, Predicate<Chat> alreadySynced);

    List<Message> listAllMessages(long chatId);

    /** Chỉ các tin nhắn có id lớn hơn afterMessageId, theo thứ tự tăng dần. */
    List<Message> listMessagesAfter(long chatId, long afterMessageId);
}
