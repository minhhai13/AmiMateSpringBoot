package com.minhhai.amimate.service;

import com.minhhai.amimate.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class XiaozhiSyncServiceTest {
    private static final Instant T = Instant.parse("2026-10-21T13:25:39Z");

    private final XiaozhiClient client = mock(XiaozhiClient.class);
    private final DeviceService devices = mock(DeviceService.class);
    private final ChatLogService logs = mock(ChatLogService.class);
    private final XiaozhiSyncService service = new XiaozhiSyncService(client, devices, logs, 0);

    @org.junit.jupiter.api.BeforeEach
    void emptyState() {
        when(logs.syncState(anyString()))
                .thenReturn(new ChatLogService.SyncState(Map.of(), Map.of(), false));
    }

    private static XiaozhiClient.Chat chat(long id, int msgCount) {
        return new XiaozhiClient.Chat(id, "Tiêu đề " + id, msgCount);
    }

    private void importReturns(int imported, int skipped) {
        when(logs.importChat(anyString(), anyString(), anyList()))
                .thenReturn(new ChatLogService.ImportResult(imported, skipped));
    }

    @Test
    void pairsUserAndAssistantMessagesAndPreservesRemoteTimestamp() {
        Instant assistantTime = Instant.parse("2026-10-21T13:25:41Z");
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(88720701L, 5)));
        when(client.listAllMessages(88720701L)).thenReturn(List.of(
                new XiaozhiClient.Message(1L, "user", "Xin chào", T),
                new XiaozhiClient.Message(2L, "assistant", "Chào bạn nha!", assistantTime),
                new XiaozhiClient.Message(3L, "tool", "ignored", assistantTime),
                new XiaozhiClient.Message(4L, "user", "Tạm biệt", assistantTime)));
        importReturns(2, 0);

        var response = service.sync(7L, "2496934");

        assertEquals(1, response.chatsFetched());
        assertEquals(4, response.messagesFetched());
        assertEquals(2, response.logsImported());
        verify(devices).requireOwned(7L, "2496934");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatLogService.ImportedLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(logs).importChat(eq("2496934"), eq("88720701"), captor.capture());
        assertEquals(List.of(
                new ChatLogService.ImportedLog("xz-88720701-1", "Xin chào", "Chào bạn nha!", T),
                new ChatLogService.ImportedLog("xz-88720701-4", "Tạm biệt", null, assistantTime)),
                captor.getValue());
        // msg_count chỉ được ghi sau khi lưu tin nhắn
        verify(logs).upsertSessions("2496934", Map.of(
                88720701L, new ChatLogService.SessionUpdate(null, 5, 2L)));
    }

    @Test
    void refusesToCallXiaozhiForADeviceNotOwnedByTheUser() {
        doThrow(new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Not found"))
                .when(devices).requireOwned(7L, "111");

        assertThrows(ApiException.class, () -> service.sync(7L, "111"));

        verifyNoInteractions(client, logs);
    }

    @Test
    void fetchesNewChatsAndOnlyTheLatestSyncedChatWhenItsMessageCountChanged() {
        when(logs.syncState("2496934")).thenReturn(new ChatLogService.SyncState(Map.of(1L, 4, 2L, 6, 3L, 8), Map.of(), false));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(
                chat(4L, 2), chat(3L, 10), chat(2L, 6), chat(1L, 99)));
        when(client.listAllMessages(anyLong())).thenReturn(List.of(
                new XiaozhiClient.Message(50L, "user", "Hi", T)));
        importReturns(1, 0);

        var response = service.sync(7L, "2496934");

        assertEquals(2, response.chatsFetched());
        verify(client).listAllMessages(3L);
        verify(client).listAllMessages(4L);
        verify(client, never()).listAllMessages(2L);
        verify(client, never()).listAllMessages(1L); // chat cũ không đổi dù msg_count lệch
    }

    @Test
    void skipsTheLatestSyncedChatWhenMessageCountIsUnchanged() {
        when(logs.syncState("2496934")).thenReturn(new ChatLogService.SyncState(Map.of(3L, 8), Map.of(), false));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(3L, 8)));

        var response = service.sync(7L, "2496934");

        assertEquals(0, response.chatsFetched());
        verify(client, never()).listAllMessages(anyLong());
        verify(logs, never()).importChat(anyString(), anyString(), anyList());
    }

    @Test
    void emptyChatsAreMarkedSyncedWithoutFetchingMessages() {
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(9L, 0)));
        importReturns(0, 0);

        service.sync(7L, "2496934");

        verify(client, never()).listAllMessages(anyLong());
        verify(logs).upsertSessions("2496934", Map.of(
                9L, new ChatLogService.SessionUpdate(null, 0, null)));
    }

    @Test
    void updatesTitlesForEveryChatEvenWhenMessagesAreNotFetched() {
        when(logs.syncState("2496934")).thenReturn(new ChatLogService.SyncState(Map.of(1L, 4, 2L, 6), Map.of(), false));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(2L, 6), chat(1L, 4)));

        service.sync(7L, "2496934");

        verify(logs).upsertSessions("2496934", Map.of(
                2L, new ChatLogService.SessionUpdate("Tiêu đề 2", null, null),
                1L, new ChatLogService.SessionUpdate("Tiêu đề 1", null, null)));
    }

    @Test
    void doesNotMarkAChatSyncedWhenFetchingItsMessagesFails() {
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(5L, 3)));
        when(client.listAllMessages(5L))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "x"));

        assertThrows(ApiException.class, () -> service.sync(7L, "2496934"));

        verify(logs, never()).upsertSessions(eq("2496934"),
                eq(Map.of(5L, new ChatLogService.SessionUpdate(null, 3, null))));
    }

    @Test
    void rejectsAConcurrentSyncOfTheSameDevice() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(client.listRecentChats(eq(2496934L), any())).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return List.of();
        });

        var first = java.util.concurrent.CompletableFuture.runAsync(() -> service.sync(7L, "2496934"));
        started.await();
        var ex = assertThrows(ApiException.class, () -> service.sync(7L, "2496934"));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatus());
        release.countDown();
        first.join();
    }

    @SuppressWarnings("unchecked")
    private java.util.function.Predicate<XiaozhiClient.Chat> capturedPredicate() {
        ArgumentCaptor<java.util.function.Predicate<XiaozhiClient.Chat>> captor =
                ArgumentCaptor.forClass(java.util.function.Predicate.class);
        verify(client).listRecentChats(eq(2496934L), captor.capture());
        return captor.getValue();
    }

    @Test
    void stopsListingOnlyAtChatsThatAreAlreadySynced() {
        when(logs.syncState("2496934"))
                .thenReturn(new ChatLogService.SyncState(Map.of(1L, 4, 2L, 6), Map.of(), false));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of());

        service.sync(7L, "2496934");

        var predicate = capturedPredicate();
        assertTrue(predicate.test(chat(1L, 4)));
        assertTrue(predicate.test(chat(2L, 6)));
        assertFalse(predicate.test(chat(3L, 1)));
    }

    @Test
    void neverStopsEarlyWhileSomeKnownChatIsStillUnsynced() {
        when(logs.syncState("2496934"))
                .thenReturn(new ChatLogService.SyncState(Map.of(1L, 4), Map.of(), true));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of());

        service.sync(7L, "2496934");

        assertFalse(capturedPredicate().test(chat(1L, 4)));
    }

    @Test
    void fetchesOnlyMessagesAfterTheWatermarkForAnAlreadySyncedChat() {
        when(logs.syncState("2496934")).thenReturn(new ChatLogService.SyncState(
                Map.of(3L, 8), Map.of(3L, 100L), false));
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(3L, 10)));
        when(client.listMessagesAfter(3L, 100L)).thenReturn(List.of(
                new XiaozhiClient.Message(101L, "user", "Tiếp", T),
                new XiaozhiClient.Message(102L, "assistant", "Ok", T)));
        importReturns(1, 0);

        service.sync(7L, "2496934");

        verify(client).listMessagesAfter(3L, 100L);
        verify(client, never()).listAllMessages(anyLong());
        verify(logs).upsertSessions("2496934", Map.of(
                3L, new ChatLogService.SessionUpdate(null, 10, 102L)));
    }

    @Test
    void trailingQuestionWithoutAnswerIsNotPartOfTheWatermark() {
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of(chat(5L, 3)));
        when(client.listAllMessages(5L)).thenReturn(List.of(
                new XiaozhiClient.Message(1L, "user", "A", T),
                new XiaozhiClient.Message(2L, "assistant", "B", T),
                new XiaozhiClient.Message(3L, "user", "C", T)));
        importReturns(2, 0);

        service.sync(7L, "2496934");

        verify(logs).upsertSessions("2496934", Map.of(
                5L, new ChatLogService.SessionUpdate(null, 3, 2L)));
    }

    @Test
    void skipsXiaozhiWhenTheSameRobotWasSyncedWithinTheCooldown() {
        var cooled = new XiaozhiSyncService(client, devices, logs, 60);
        when(client.listRecentChats(eq(2496934L), any())).thenReturn(List.of());

        cooled.sync(7L, "2496934");
        var second = cooled.sync(7L, "2496934");

        assertEquals(0, second.chatsFetched());
        verify(client, times(1)).listRecentChats(eq(2496934L), any());
    }

    @Test
    void doesNotStartTheCooldownWhenASyncFails() {
        var cooled = new XiaozhiSyncService(client, devices, logs, 60);
        when(client.listRecentChats(eq(2496934L), any()))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "x"))
                .thenReturn(List.of());

        assertThrows(ApiException.class, () -> cooled.sync(7L, "2496934"));
        cooled.sync(7L, "2496934");

        verify(client, times(2)).listRecentChats(eq(2496934L), any());
    }
}
