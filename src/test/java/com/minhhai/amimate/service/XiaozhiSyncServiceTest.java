package com.minhhai.amimate.service;

import com.minhhai.amimate.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class XiaozhiSyncServiceTest {
    @Test
    void importsUserAssistantPairsAndPreservesRemoteTimestamp() {
        XiaozhiClient client = mock(XiaozhiClient.class);
        DeviceService devices = mock(DeviceService.class);
        ChatLogService logs = mock(ChatLogService.class);
        Instant userTime = Instant.parse("2026-10-21T13:25:39Z");
        Instant assistantTime = Instant.parse("2026-10-21T13:25:41Z");
        when(client.listAllChats()).thenReturn(List.of(new XiaozhiClient.Chat(88720701L)));
        when(client.listAllMessages(88720701L)).thenReturn(List.of(
                new XiaozhiClient.Message(3583508348L, "user", "Xin chào", userTime),
                new XiaozhiClient.Message(3583508349L, "assistant", "Chào bạn nha!", assistantTime),
                new XiaozhiClient.Message(3583508350L, "tool", "ignored", assistantTime),
                new XiaozhiClient.Message(3583508351L, "user", "Tạm biệt", assistantTime)));
        when(logs.saveImported(
                anyString(), anyString(), anyString(), nullable(String.class),
                nullable(String.class), any(Instant.class))).thenReturn(true);

        var response = new XiaozhiSyncService(client, devices, logs)
                .sync(7L, "AMI-ABC123");

        assertEquals(1, response.chatsFetched());
        assertEquals(4, response.messagesFetched());
        assertEquals(2, response.logsImported());
        assertEquals(0, response.logsSkipped());
        verify(devices).requireOwned(7L, "AMI-ABC123");
        verify(logs).saveImported(
                "AMI-ABC123", "88720701", "xz-88720701-3583508348",
                "Xin chào", "Chào bạn nha!", userTime);
        verify(logs).saveImported(
                "AMI-ABC123", "88720701", "xz-88720701-3583508351",
                "Tạm biệt", null, assistantTime);
        verifyNoMoreInteractions(logs);
    }

    @Test
    void refusesToCallXiaozhiForADeviceNotOwnedByTheUser() {
        XiaozhiClient client = mock(XiaozhiClient.class);
        DeviceService devices = mock(DeviceService.class);
        ChatLogService logs = mock(ChatLogService.class);
        doThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Not found"))
                .when(devices).requireOwned(7L, "AMI-OTHER");

        var service = new XiaozhiSyncService(client, devices, logs);
        assertThrows(ApiException.class, () -> service.sync(7L, "AMI-OTHER"));

        verifyNoInteractions(client, logs);
    }

    @Test
    void countsPreviouslyImportedLogsAsSkipped() {
        XiaozhiClient client = mock(XiaozhiClient.class);
        DeviceService devices = mock(DeviceService.class);
        ChatLogService logs = mock(ChatLogService.class);
        Instant createdAt = Instant.parse("2026-10-21T13:25:39Z");
        when(client.listAllChats()).thenReturn(List.of(new XiaozhiClient.Chat(10L)));
        when(client.listAllMessages(10L)).thenReturn(List.of(
                new XiaozhiClient.Message(20L, "user", "Hello", createdAt)));
        when(logs.saveImported(
                anyString(), anyString(), anyString(), nullable(String.class),
                nullable(String.class), any(Instant.class))).thenReturn(false);

        var response = new XiaozhiSyncService(client, devices, logs)
                .sync(7L, "AMI-ABC123");

        assertEquals(0, response.logsImported());
        assertEquals(1, response.logsSkipped());
    }
}
