package com.onlinejudge.gradeservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fail-closed adapter for the frozen Course v2 authorization and membership contract. */
@Component
public class GradeCoursePermissionClient implements CoursePermissionClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String baseUrl;
    private final String serviceAuthorization;
    private final Duration timeout;
    private final int memberPageSize;

    public GradeCoursePermissionClient(ObjectMapper mapper,
            @Value("${grade.course.base-url:http://127.0.0.1:8082}") String baseUrl,
            @Value("${grade.course.service-authorization:}") String serviceAuthorization,
            @Value("${grade.course.timeout:PT1S}") Duration timeout,
            @Value("${grade.course.member-page-size:100}") int memberPageSize) {
        this.mapper = mapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.serviceAuthorization = serviceAuthorization;
        this.timeout = timeout;
        this.memberPageSize = Math.max(1, Math.min(100, memberPageSize));
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean canManageCourse(long courseId, long userId) {
        return authorize(courseId, userId, "MANAGE");
    }

    @Override
    public boolean canViewCourse(long courseId, long userId) {
        return authorize(courseId, userId, "VIEW");
    }

    @Override
    public boolean isCourseMember(long courseId, long userId) {
        return authorize(courseId, userId, "VIEW");
    }

    @Override
    public boolean canManageCourseGrade(long courseId, long userId) {
        return authorize(courseId, userId, "MANAGE_GRADE");
    }

    @Override
    public List<Long> listCourseStudentIds(long courseId) {
        return listMembers(courseId, "STUDENT");
    }

    @Override
    public List<Long> listCourseTeacherIds(long courseId) {
        return listMembers(courseId, "TEACHER");
    }

    private boolean authorize(long courseId, long userId, String action) {
        requireConfigured();
        URI uri = URI.create(baseUrl + "/internal/v2/courses/" + courseId + "/authorizations/" + userId
                + "?action=" + encode(action));
        JsonNode decision = get(uri);
        if (!requiredText(decision, "courseId").equals(String.valueOf(courseId))
                || !requiredText(decision, "userId").equals(String.valueOf(userId))
                || !requiredText(decision, "action").equals(action)
                || !decision.path("allowed").isBoolean()
                || !decision.path("memberVersion").isIntegralNumber()
                || decision.path("memberVersion").asLong() < 1) {
            throw new CourseAuthorizationUnavailableException("Course authorization response is malformed");
        }
        return decision.path("allowed").asBoolean();
    }

    private List<Long> listMembers(long courseId, String role) {
        requireConfigured();
        List<Long> members = new ArrayList<>();
        long total = Long.MAX_VALUE;
        int page = 0;
        while (members.size() < total) {
            URI uri = URI.create(baseUrl + "/internal/v2/courses/" + courseId + "/members?role=" + role
                    + "&page=" + page + "&size=" + memberPageSize);
            JsonNode response = get(uri);
            JsonNode items = response.path("items");
            if (!items.isArray() || !response.path("page").isIntegralNumber()
                    || response.path("page").asInt() != page || !response.path("size").isIntegralNumber()
                    || !response.path("total").isIntegralNumber() || response.path("total").asLong() < 0) {
                throw new CourseAuthorizationUnavailableException("Course member response is malformed");
            }
            total = response.path("total").asLong();
            for (JsonNode item : items) {
                if (!role.equals(requiredText(item, "role")) || !item.path("memberVersion").isIntegralNumber()
                        || item.path("memberVersion").asLong() < 1) {
                    throw new CourseAuthorizationUnavailableException("Course member response is malformed");
                }
                try {
                    members.add(Long.parseLong(requiredText(item, "userId")));
                } catch (NumberFormatException invalidIdentifier) {
                    throw new CourseAuthorizationUnavailableException("Course member identifier is incompatible", invalidIdentifier);
                }
            }
            if (items.isEmpty() && members.size() < total) {
                throw new CourseAuthorizationUnavailableException("Course member pagination is incomplete");
            }
            page++;
        }
        return List.copyOf(members);
    }

    private JsonNode get(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .header("X-OnlineJudge-Service-Authorization", serviceAuthorization)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return missingDecision(uri);
            }
            if (response.statusCode() != 200) {
                throw new CourseAuthorizationUnavailableException("Course dependency returned HTTP " + response.statusCode());
            }
            JsonNode body = mapper.readTree(response.body());
            if (body == null || !body.isObject()) {
                throw new CourseAuthorizationUnavailableException("Course response is malformed");
            }
            return body;
        } catch (CourseAuthorizationUnavailableException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new CourseAuthorizationUnavailableException("Course dependency is unavailable", failure);
        }
    }

    private static JsonNode missingDecision(URI uri) {
        throw new CourseAuthorizationUnavailableException("Course does not exist: " + uri.getPath());
    }

    private void requireConfigured() {
        if (baseUrl.isBlank() || serviceAuthorization.isBlank()) {
            throw new CourseAuthorizationUnavailableException("Course authorization is not configured");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new CourseAuthorizationUnavailableException("Course response is missing " + field);
        }
        return value.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
