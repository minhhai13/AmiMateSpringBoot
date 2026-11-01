package com.minhhai.amimate.service;

import com.minhhai.amimate.entity.ChatSession;
import com.minhhai.amimate.repository.ChatLogRepository;
import com.minhhai.amimate.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSummaryTest {
    private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
    private final ChatLogService service = new ChatLogService(
            mock(ChatLogRepository.class), sessions, mock(DeviceService.class));

    private static ChatSession row(String chatId, String summary, Integer count) {
        ChatSession s = new ChatSession();
        s.setDeviceId("2496934");
        s.setChatId(chatId);
        s.setSummary(summary);
        s.setRemoteMsgCount(count);
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<ChatSession> saved() {
        ArgumentCaptor<List<ChatSession>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessions).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsAndUpdatesOnlyWhatChanged() {
        ChatSession unchanged = row("1", "Giữ nguyên", 4);
        ChatSession outdated = row("2", "Cũ", 4);
        when(sessions.findByDeviceId("2496934")).thenReturn(List.of(unchanged, outdated));

        service.upsertSessions("2496934", Map.of(
                1L, new ChatLogService.SessionUpdate("Giữ nguyên", 4, null),
                2L, new ChatLogService.SessionUpdate("  Học ngữ pháp  ", null, null),
                3L, new ChatLogService.SessionUpdate("Phím tắt Excel", 7, null),
                4L, new ChatLogService.SessionUpdate("   ", null, null)));

        List<ChatSession> saved = saved();
        assertEquals(2, saved.size());
        assertEquals("Học ngữ pháp", outdated.getSummary());
        assertEquals(4, outdated.getRemoteMsgCount());
        ChatSession created = saved.stream().filter(s -> s.getChatId().equals("3")).findFirst().orElseThrow();
        assertEquals("Phím tắt Excel", created.getSummary());
        assertEquals(7, created.getRemoteMsgCount());
    }

    @Test
    void keepsExistingTitleWhenOnlyTheMessageCountIsUpdated() {
        ChatSession existing = row("1", "Tiêu đề", null);
        when(sessions.findByDeviceId("2496934")).thenReturn(List.of(existing));

        service.upsertSessions("2496934", Map.of(1L, new ChatLogService.SessionUpdate(null, 12, null)));

        assertEquals("Tiêu đề", existing.getSummary());
        assertEquals(12, existing.getRemoteMsgCount());
    }

    @Test
    void syncStateListsSyncedChatsAndFlagsUnsyncedOnes() {
        when(sessions.findByDeviceId("2496934")).thenReturn(List.of(
                row("1", "A", 4), row("2", "B", null), row("abc", "C", 3)));

        var state = service.syncState("2496934");

        assertEquals(Map.of(1L, 4), state.synced());
        assertTrue(state.hasUnsynced());
    }

    @Test
    void syncStateHasNoUnsyncedFlagWhenEverythingIsSynced() {
        when(sessions.findByDeviceId("2496934")).thenReturn(List.of(row("1", "A", 4)));

        assertFalse(service.syncState("2496934").hasUnsynced());
    }

    @Test
    void lastMessageIdOnlyMovesForward() {
        ChatSession existing = row("1", "T", 4);
        existing.setLastMessageId(50L);
        when(sessions.findByDeviceId("2496934")).thenReturn(List.of(existing));

        service.upsertSessions("2496934", Map.of(1L, new ChatLogService.SessionUpdate(null, null, 40L)));
        assertEquals(50L, existing.getLastMessageId());

        service.upsertSessions("2496934", Map.of(1L, new ChatLogService.SessionUpdate(null, null, 60L)));
        assertEquals(60L, existing.getLastMessageId());
    }
}
