package com.minhhai.amimate.service;

import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.repository.ChatLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatLogImportTest {
    private final ChatLogRepository repository = mock(ChatLogRepository.class);
    private final ChatLogService service = new ChatLogService(repository, mock(DeviceService.class));

    @Test
    void savesSourceTimestampForANewMessage() {
        Instant sourceTime = Instant.parse("2026-10-21T13:25:39Z");
        when(repository.findByDeviceIdAndClientMessageId("2496934", "xz-10-20"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ChatLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.saveImported(
                "2496934", "10", "xz-10-20", "Xin chào", "Chào bạn", sourceTime));

        ArgumentCaptor<ChatLog> captor = ArgumentCaptor.forClass(ChatLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ChatLog saved = captor.getValue();
        assertEquals("2496934", saved.getDeviceId());
        assertEquals("10", saved.getSessionId());
        assertEquals("xz-10-20", saved.getClientMessageId());
        assertEquals("Xin chào", saved.getUserMessage());
        assertEquals("Chào bạn", saved.getBotResponse());
        assertEquals(sourceTime, saved.getCreatedAt());
    }

    @Test
    void skipsAnAlreadyImportedMessage() {
        ChatLog existing = new ChatLog();
        existing.setBotResponse("Chào bạn");
        when(repository.findByDeviceIdAndClientMessageId("2496934", "xz-10-20"))
                .thenReturn(Optional.of(existing));

        assertFalse(service.saveImported(
                "2496934", "10", "xz-10-20", "Xin chào", "Chào bạn", Instant.now()));

        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void fillsInBotResponseWhenPreviouslyOnlyTheQuestionWasStored() {
        ChatLog existing = new ChatLog();
        when(repository.findByDeviceIdAndClientMessageId("2496934", "xz-10-20"))
                .thenReturn(Optional.of(existing));

        assertTrue(service.saveImported(
                "2496934", "10", "xz-10-20", "Xin chào", "Chào bạn", Instant.now()));

        assertEquals("Chào bạn", existing.getBotResponse());
        verify(repository).save(existing);
    }
}
