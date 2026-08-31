package com.onlinejudge.courseservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseLearningProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded client for the v2 Course -> LRN recent-task summary contract
 * ({@code GET /internal/v2/learning/tasks/recent}).  One request with one
 * timeout budget; any non-200, malformed, or failed response surfaces as
 * {@link LearningTasksUnavailableException} so the caller never turns a
 * downstream outage into an empty "no tasks" state.
 */
@Component
public class LearningTaskSummaryClient {
    public static final int MAX_RECENT_TASKS = 5;
    private static final String RECENT_TASKS_PATH = "/internal/v2/learning/tasks/recent";

    private final ObjectMapper objectMapper;
    private final CourseLearningProperties properties;
    private final HttpClient http;

    public LearningTaskSummaryClient(ObjectMapper objectMapper, CourseLearningProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    public List<RecentTask> recentTasks(long courseId, long userId, String requestId) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new LearningTasksUnavailableException("learning base url is not configured");
        }
        try {
            URI uri = URI.create(baseUrl + RECENT_TASKS_PATH
                    + "?courseId=" + courseId + "&userId=" + userId + "&limit=" + MAX_RECENT_TASKS);
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(properties.getTimeout())
                    .header("X-Request-Id", requestId)
                    .header("Accept", "application/json")
                    .GET();
            if (properties.getServiceToken() != null && !properties.getServiceToken().isBlank()) {
                builder.header("X-OnlineJudge-Service-Authorization", "Bearer " + properties.getServiceToken());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LearningTasksUnavailableException("learning task summary returned status " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                throw new LearningTasksUnavailableException("learning task summary response is malformed");
            }
            List<RecentTask> tasks = new ArrayList<>();
            for (JsonNode item : items) {
                tasks.add(new RecentTask(
                        item.path("taskId").asLong(),
                        item.path("taskType").asText(),
                        item.path("title").asText(),
                        item.path("courseId").asLong(),
                        item.path("courseName").asText(),
                        textOrNull(item, "deadline"),
                        item.path("progress").asInt(),
                        item.path("status").asText(),
                        textOrNull(item, "actionUrl")
                ));
            }
            return List.copyOf(tasks);
        } catch (LearningTasksUnavailableException unavailable) {
            throw unavailable;
        } catch (Exception failure) {
            throw new LearningTasksUnavailableException("learning task summary is unavailable", failure);
        }
    }

    private static String textOrNull(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record RecentTask(long taskId, String taskType, String title, long courseId, String courseName,
                             String deadline, int progress, String status, String actionUrl) {
    }

    public static final class LearningTasksUnavailableException extends RuntimeException {
        public LearningTasksUnavailableException(String message) {
            super(message);
        }

        public LearningTasksUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
