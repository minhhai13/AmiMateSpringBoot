package com.minhhai.amimate.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XiaozhiApiClientTest {
    @Test
    void fetchesEveryChatPageWithBearerAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=1&pageSize=20"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":101,"msg_count":18,"chat_summary":{"title":"Học ngữ pháp tiếng Anh và phím tắt Excel"}}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=2&pageSize=20"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":102,"chat_summary":null}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        var chats = client.listRecentChats(2496934L, chat -> false);

        assertEquals(2, chats.size());
        assertEquals(101L, chats.get(0).id());
        assertEquals("Học ngữ pháp tiếng Anh và phím tắt Excel", chats.get(0).title());
        assertEquals(18, chats.get(0).msgCount());
        assertNull(chats.get(1).title());
        assertEquals(-1, chats.get(1).msgCount());
        assertEquals(102L, chats.get(1).id());
        server.verify();
    }

    @Test
    void requestsMessagesInAscendingOrderAndMapsFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=88720701&order=asc&page=1&pageSize=50"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[
                          {"id":3583508348,"role":"user","content":"Xin chào",
                           "created_at":"2026-10-21T13:25:39.000Z"}
                        ],"pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=88720701&order=asc&page=2&pageSize=50"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[
                          {"id":3583508349,"role":"assistant","content":"Chào bạn nha!",
                           "created_at":"2026-10-21T13:25:41.000Z"}
                        ],"pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        var messages = client.listAllMessages(88720701L);

        assertEquals(2, messages.size());
        assertEquals(3583508348L, messages.get(0).id());
        assertEquals("user", messages.get(0).role());
        assertEquals("Xin chào", messages.get(0).content());
        assertEquals("2026-10-21T13:25:39Z", messages.get(0).createdAt().toString());
        assertEquals("assistant", messages.get(1).role());
        server.verify();
    }

    @Test
    void stopsAfterTheFirstPageWhenEveryChatIsAlreadySyncedAndNewestFirst() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=1&pageSize=20"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":300},{"id":200}],
                        "pagination":{"totalPages":3}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        var chats = client.listRecentChats(2496934L, chat -> true);

        assertEquals(2, chats.size());
        server.verify(); // trang 2 và 3 không được gọi
    }

    @Test
    void keepsPagingWhenTheFirstPageIsNotInDescendingOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=1&pageSize=20"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":100},{"id":200}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=2&pageSize=20"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":50}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        assertEquals(3, client.listRecentChats(2496934L, chat -> true).size());
        server.verify();
    }

    @Test
    void keepsPagingWhenAChatOnThePageIsNotSyncedYet() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=1&pageSize=20"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":300},{"id":200}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=2&pageSize=20"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":100}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        assertEquals(3, client.listRecentChats(2496934L, chat -> chat.id() != 200).size());
        server.verify();
    }

    @Test
    void fetchesMessagesNewestFirstAndStopsAtTheKnownMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=7&order=desc&page=1&pageSize=50"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[
                          {"id":105,"role":"assistant","content":"e","created_at":"2026-10-21T13:25:41.000Z"},
                          {"id":104,"role":"user","content":"d","created_at":"2026-10-21T13:25:40.000Z"},
                          {"id":103,"role":"assistant","content":"c","created_at":"2026-10-21T13:25:39.000Z"}
                        ],"pagination":{"totalPages":5}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        var messages = client.listMessagesAfter(7L, 103L);

        assertEquals(List.of(104L, 105L), messages.stream().map(m -> m.id()).toList());
        server.verify(); // không gọi các trang 2..5
    }

    @Test
    void fallsBackToAFullFetchWhenOrderDescIsNotHonoured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String ascending = """
                {"success":true,"data":{"list":[
                  {"id":101,"role":"user","content":"a","created_at":"2026-10-21T13:25:39.000Z"},
                  {"id":102,"role":"assistant","content":"b","created_at":"2026-10-21T13:25:40.000Z"},
                  {"id":103,"role":"user","content":"c","created_at":"2026-10-21T13:25:41.000Z"}
                ],"pagination":{"totalPages":1}}}
                """;
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=7&order=desc&page=1&pageSize=50"))
                .andRespond(withSuccess(ascending, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=7&order=asc&page=1&pageSize=50"))
                .andRespond(withSuccess(ascending, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token");

        var messages = client.listMessagesAfter(7L, 101L);

        assertEquals(List.of(102L, 103L), messages.stream().map(m -> m.id()).toList());
        server.verify();
    }
}
