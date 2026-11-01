package com.minhhai.amimate.service;

import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.repository.ChatLogRepository;
import com.minhhai.amimate.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatLogImportTest {
    private static final Instant T = Instant.parse("2026-10-21T13:25:39Z");

    private final ChatLogRepository repository = mock(ChatLogRepository.class);
    private final ChatLogService service = new ChatLogService(
            repository, mock(ChatSessionRepository.class), mock(DeviceService.class));

    private static ChatLog stored(String clientMessageId, String botResponse) {
        ChatLog log = new ChatLog();
        log.setClientMessageId(clientMessageId);
        log.setBotResponse(botResponse);
        return log;
    }

    @SuppressWarnings("unchecked")
    private List<ChatLog> saved() {
        ArgumentCaptor<List<ChatLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void savesNewMessagesWithSourceTimestampInOneBatch() {
        when(repository.findByDeviceIdAndSessionId("2496934", "10")).thenReturn(List.of());

        var result = service.importChat("2496934", "10", List.of(
                new ChatLogService.ImportedLog("xz-10-20", "Xin chào", "Chào bạn", T),
                new ChatLogService.ImportedLog("xz-10-21", "Tạm biệt", null, T)));

        assertEquals(2, result.imported());
        assertEquals(0, result.skipped());
        List<ChatLog> saved = saved();
        assertEquals(2, saved.size());
        assertEquals("2496934", saved.get(0).getDeviceId());
        assertEquals("10", saved.get(0).getSessionId());
        assertEquals("Chào bạn", saved.get(0).getBotResponse());
        assertEquals(T, saved.get(0).getCreatedAt());
        verify(repository, times(1)).findByDeviceIdAndSessionId("2496934", "10");
    }

    @Test
    void skipsMessagesThatAreAlreadyStored() {
        when(repository.findByDeviceIdAndSessionId("2496934", "10"))
                .thenReturn(List.of(stored("xz-10-20", "Chào bạn")));

        var result = service.importChat("2496934", "10", List.of(
                new ChatLogService.ImportedLog("xz-10-20", "Xin chào", "Chào bạn", T)));

        assertEquals(0, result.imported());
        assertEquals(1, result.skipped());
        assertTrue(saved().isEmpty());
    }

    @Test
    void fillsInBotResponseWhenPreviouslyOnlyTheQuestionWasStored() {
        ChatLog existing = stored("xz-10-20", null);
        when(repository.findByDeviceIdAndSessionId("2496934", "10")).thenReturn(List.of(existing));

        var result = service.importChat("2496934", "10", List.of(
                new ChatLogService.ImportedLog("xz-10-20", "Xin chào", "Chào bạn", T)));

        assertEquals(1, result.imported());
        assertEquals("Chào bạn", existing.getBotResponse());
        assertEquals(List.of(existing), saved());
    }
}
