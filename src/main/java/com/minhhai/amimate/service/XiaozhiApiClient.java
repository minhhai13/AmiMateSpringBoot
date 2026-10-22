package com.minhhai.amimate.service;

import com.minhhai.amimate.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class XiaozhiApiClient implements XiaozhiClient {
    private static final String API_ERROR =
            "Không thể đồng bộ dữ liệu từ Xiaozhi. Vui lòng thử lại sau.";
    private static final int CHAT_PAGE_SIZE = 20;
    private static final int MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_PAGES = 1000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String bearerToken;
    private final long agentId;

    public XiaozhiApiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${xiaozhi.api.bearer-token:}") String bearerToken,
            @Value("${xiaozhi.api.agent-id:2496934}") long agentId) {
        this.restClient = restClientBuilder.baseUrl("https://xiaozhi.me").build();
        this.objectMapper = objectMapper;
        this.bearerToken = bearerToken;
        this.agentId = agentId;
    }

    @Override
    public List<Chat> listAllChats() {
        return fetchAllPages(
                "/api/chats/list",
                CHAT_PAGE_SIZE,
                0,
                node -> new Chat(requiredLong(node, "id")));
    }

    @Override
    public List<Message> listAllMessages(long chatId) {
        return fetchAllPages(
                "/api/chats/messages",
                MESSAGE_PAGE_SIZE,
                chatId,
                node -> new Message(
                        requiredLong(node, "id"),
                        requiredText(node, "role"),
                        optionalText(node, "content"),
                        requiredInstant(node, "created_at")));
    }

    private <T> List<T> fetchAllPages(
            String path,
            int pageSize,
            long chatId,
            NodeMapper<T> mapper) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình token Xiaozhi trên máy chủ.");
        }

        List<T> result = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            if (page > MAX_PAGES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
            }

            JsonNode root = requestPage(path, page, pageSize, chatId);
            if (root == null || !root.path("success").asBoolean(false)) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
            }

            JsonNode data = root.path("data");
            JsonNode items = data.path("list");
            JsonNode pagination = data.path("pagination");
            if (!items.isArray() || !pagination.path("totalPages").isNumber()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
            }

            int reportedTotalPages = pagination.path("totalPages").asInt();
            if (reportedTotalPages < 0 || reportedTotalPages > MAX_PAGES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
            }
            totalPages = Math.max(totalPages, reportedTotalPages);
            for (JsonNode item : items) {
                result.add(mapper.map(item));
            }
        }
        return List.copyOf(result);
    }

    private JsonNode requestPage(String path, int page, int pageSize, long chatId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path);
                        if (path.endsWith("/list")) {
                            builder.queryParam("agentId", agentId);
                        } else {
                            builder.queryParam("chatId", chatId)
                                    .queryParam("includeTools", 1)
                                    .queryParam("order", "asc");
                        }
                        return builder.queryParam("page", page)
                                .queryParam("pageSize", pageSize)
                                .build();
                    })
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
        return value.asLong();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
        return value.asText();
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field));
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
    }

    @FunctionalInterface
    private interface NodeMapper<T> {
        T map(JsonNode node);
    }
}
