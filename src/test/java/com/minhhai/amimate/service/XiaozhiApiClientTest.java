package com.minhhai.amimate.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        {"success":true,"data":{"list":[{"id":101}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/list?agentId=2496934&page=2&pageSize=20"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[{"id":102}],
                        "pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token", 2496934L);

        var chats = client.listAllChats();

        assertEquals(2, chats.size());
        assertEquals(101L, chats.get(0).id());
        assertEquals(102L, chats.get(1).id());
        server.verify();
    }

    @Test
    void requestsMessagesInAscendingOrderAndMapsFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=88720701&includeTools=1&order=asc&page=1&pageSize=50"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[
                          {"id":3583508348,"role":"user","content":"Xin chào",
                           "created_at":"2026-10-21T13:25:39.000Z"}
                        ],"pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        "https://xiaozhi.me/api/chats/messages?chatId=88720701&includeTools=1&order=asc&page=2&pageSize=50"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"list":[
                          {"id":3583508349,"role":"assistant","content":"Chào bạn nha!",
                           "created_at":"2026-10-21T13:25:41.000Z"}
                        ],"pagination":{"totalPages":2}}}
                        """, MediaType.APPLICATION_JSON));

        var client = new XiaozhiApiClient(builder, JsonMapper.builder().build(), "test-token", 2496934L);

        var messages = client.listAllMessages(88720701L);

        assertEquals(2, messages.size());
        assertEquals(3583508348L, messages.get(0).id());
        assertEquals("user", messages.get(0).role());
        assertEquals("Xin chào", messages.get(0).content());
        assertEquals("2026-10-21T13:25:39Z", messages.get(0).createdAt().toString());
        assertEquals("assistant", messages.get(1).role());
        server.verify();
    }
}
