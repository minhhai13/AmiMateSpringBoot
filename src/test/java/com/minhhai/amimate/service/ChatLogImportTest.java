package com.minhhai.amimate.service;

import com.minhhai.amimate.entity.ChatLog;
import com.minhhai.amimate.repository.ChatLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ChatLogImportTest {
    @Test
    void savesSourceTimestampAndSkipsAnAlreadyImportedMessage() {
        ChatLogRepository repository = mock(ChatLogRepository.class);
        DeviceService deviceService = mock(DeviceService.class);
        ChatLogService service = new ChatLogService(repository, deviceService);
        Instant sourceTime = Instant.parse("2026-10-21T13:25:39Z");
        when(repository.findByDeviceIdAndClientMessageId("AMI-ABC123", "xz-10-20"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ChatLog()));
        when(repository.saveAndFlush(any(ChatLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.saveImported(
                "AMI-ABC123", "10", "xz-10-20", "Xin chào", "Chào bạn", sourceTime));
        assertFalse(service.saveImported(
                "AMI-ABC123", "10", "xz-10-20", "Xin chào", "Chào bạn", sourceTime));

        ArgumentCaptor<ChatLog> captor = ArgumentCaptor.forClass(ChatLog.class);
        verify(repository).saveAndFlush(captor.capture());
        ChatLog saved = captor.getValue();
        assertEquals("AMI-ABC123", saved.getDeviceId());
        assertEquals("10", saved.getSessionId());
        assertEquals("xz-10-20", saved.getClientMessageId());
        assertEquals("Xin chào", saved.getUserMessage());
        assertEquals("Chào bạn", saved.getBotResponse());
        assertEquals(sourceTime, saved.getCreatedAt());
        verify(repository, times(2))
                .findByDeviceIdAndClientMessageId("AMI-ABC123", "xz-10-20");
    }
}
