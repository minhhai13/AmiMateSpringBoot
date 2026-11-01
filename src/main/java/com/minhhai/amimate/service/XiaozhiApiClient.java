package com.minhhai.amimate.service;

import com.minhhai.amimate.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Component
public class XiaozhiApiClient implements XiaozhiClient {
    private static final Logger log = LoggerFactory.getLogger(XiaozhiApiClient.class);
    private static final String API_ERROR =
            "Không thể đồng bộ dữ liệu từ Xiaozhi. Vui lòng thử lại sau.";
    private static final int CHAT_PAGE_SIZE = 20;
    private static final int MESSAGE_PAGE_SIZE = 50;
    private static final int MAX_PAGES = 1000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String bearerToken;

    public XiaozhiApiClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${xiaozhi.api.bearer-token:}") String bearerToken) {
        this.restClient = restClientBuilder.baseUrl("https://xiaozhi.me").build();
        this.objectMapper = objectMapper;
        this.bearerToken = bearerToken;
    }

    @Override
    public List<Chat> listRecentChats(long agentId, Predicate<Chat> alreadySynced) {
        return fetchAllPages(
                "/api/chats/list",
                CHAT_PAGE_SIZE,
                agentId,
                null,
                node -> new Chat(
                        requiredLong(node, "id"),
                        summaryTitle(node),
                        optionalInt(node, "msg_count")),
                page -> isNewestFirst(page) && page.stream().allMatch(alreadySynced));
    }

    private static boolean isNewestFirst(List<Chat> page) {
        for (int i = 1; i < page.size(); i++) {
            if (page.get(i - 1).id() <= page.get(i).id()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Message> listAllMessages(long chatId) {
        return fetchMessages(chatId, "asc", page -> false);
    }

    @Override
    public List<Message> listMessagesAfter(long chatId, long afterMessageId) {
        // Lấy từ mới nhất về cũ và dừng ngay khi chạm tin đã lưu.
        List<Message> fetched = fetchMessages(
                chatId, "desc",
                page -> page.stream().anyMatch(m -> m.id() <= afterMessageId));
        if (!isNewestFirstMessages(fetched)) {
            // Xiaozhi không áp dụng order=desc: quay về lấy hết rồi lọc.
            log.warn("Xiaozhi did not honour order=desc; falling back to a full fetch: chatId={}", chatId);
            fetched = listAllMessages(chatId);
        }
        return fetched.stream()
                .filter(m -> m.id() > afterMessageId)
                .sorted(java.util.Comparator.comparingLong(Message::id))
                .toList();
    }

    private List<Message> fetchMessages(long chatId, String order, Predicate<List<Message>> stopAfterPage) {
        return fetchAllPages(
                "/api/chats/messages",
                MESSAGE_PAGE_SIZE,
                chatId,
                order,
                node -> new Message(
                        requiredLong(node, "id"),
                        requiredText(node, "role"),
                        optionalText(node, "content"),
                        requiredInstant(node, "created_at")),
                stopAfterPage);
    }

    private static boolean isNewestFirstMessages(List<Message> messages) {
        for (int i = 1; i < messages.size(); i++) {
            if (messages.get(i - 1).id() <= messages.get(i).id()) {
                return false;
            }
        }
        return true;
    }

    private <T> List<T> fetchAllPages(
            String path,
            int pageSize,
            long id,
            String order,
            NodeMapper<T> mapper,
            Predicate<List<T>> stopAfterPage) {
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

            JsonNode root = requestPage(path, page, pageSize, id, order);
            if (root == null || !root.path("success").asBoolean(false)) {
                throw invalidResponse("missing or false success flag", path, page);
            }

            JsonNode data = root.path("data");
            JsonNode items = data.path("list");
            JsonNode pagination = data.path("pagination");
            if (!items.isArray() || !pagination.path("totalPages").isNumber()) {
                throw invalidResponse("unexpected list or pagination shape", path, page);
            }

            int reportedTotalPages = pagination.path("totalPages").asInt();
            if (reportedTotalPages < 0 || reportedTotalPages > MAX_PAGES) {
                throw invalidResponse("totalPages outside supported range", path, page);
            }
            totalPages = Math.max(totalPages, reportedTotalPages);
            List<T> pageItems = new ArrayList<>();
            for (JsonNode item : items) {
                pageItems.add(mapper.map(item));
            }
            result.addAll(pageItems);
            if (stopAfterPage.test(pageItems)) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private JsonNode requestPage(String path, int page, int pageSize, long id, String order) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path(path);
                        if (path.endsWith("/list")) {
                            builder.queryParam("agentId", id);
                        } else {
                            builder.queryParam("chatId", id)
                                    .queryParam("order", order);
                        }
                        return builder.queryParam("page", page)
                                .queryParam("pageSize", pageSize)
                                .build();
                    })
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            String status = exception instanceof RestClientResponseException responseException
                    ? Integer.toString(responseException.getStatusCode().value())
                    : "unavailable";
            if (exception instanceof RestClientResponseException responseException
                    && responseException.getStatusCode().value() == 401) {
                log.warn("Xiaozhi API rejected the bearer token (401); it may have expired.");
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Token Xiaozhi không hợp lệ hoặc đã hết hạn. Vui lòng liên hệ quản trị viên.");
            }
            log.warn(
                    "Xiaozhi API request failed: path={}, page={}, chatId={}, status={}, exceptionType={}",
                    path,
                    page,
                    path.endsWith("/list") ? "n/a" : id,
                    status,
                    exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
    }

    private static ApiException invalidResponse(String reason, String path, int page) {
        log.warn("Unexpected Xiaozhi API response: path={}, page={}, reason={}", path, page, reason);
        return new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
    }

    /** chat_summary là object {"title": "..."}; chat đang diễn ra có thể chưa có. */
    private static String summaryTitle(JsonNode node) {
        JsonNode summary = node.get("chat_summary");
        if (summary == null || summary.isNull()) {
            return null;
        }
        if (summary.isString()) {
            return summary.asText();
        }
        JsonNode title = summary.get("title");
        return title != null && title.isString() ? title.asText() : null;
    }

    private static int optionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : -1;
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            log.warn("Unexpected Xiaozhi API response field: field={}", field);
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
        return value.asLong();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asText().isBlank()) {
            log.warn("Unexpected Xiaozhi API response field: field={}", field);
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
            log.warn("Unexpected Xiaozhi API response field: field={}", field);
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
        return value.asText();
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        try {
            return Instant.parse(requiredText(node, field));
        } catch (DateTimeParseException exception) {
            log.warn("Unexpected Xiaozhi API timestamp: field={}", field);
            throw new ApiException(HttpStatus.BAD_GATEWAY, API_ERROR);
        }
    }

    @FunctionalInterface
    private interface NodeMapper<T> {
        T map(JsonNode node);
    }
}
