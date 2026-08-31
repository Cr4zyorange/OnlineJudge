package com.onlinejudge.assessmentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Calls the canonical CRS authorization endpoint and distinguishes denial from dependency outage. */
@Component
public class HttpCoursePermissionClient implements CoursePermissionClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String authorizationUri;
    private final String serviceAuthorization;
    private final Duration timeout;

    public HttpCoursePermissionClient(ObjectMapper mapper,
            @Value("${assessment.course.authorization-uri:}") String authorizationUri,
            @Value("${assessment.course.service-authorization:}") String serviceAuthorization,
            @Value("${assessment.course.timeout:PT0.8S}") Duration timeout) {
        this.mapper = mapper;
        this.authorizationUri = authorizationUri;
        this.serviceAuthorization = serviceAuthorization;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean canManageCourse(String courseId, String userId) {
        return canManageCourse(courseId, userId, UUID.randomUUID().toString());
    }

    @Override
    public boolean canManageCourse(String courseId, String userId, String requestId) {
        if (authorizationUri.isBlank() || serviceAuthorization.isBlank() || courseId == null || userId == null) {
            throw new CourseAuthorizationUnavailableException("CRS authorization is not configured");
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 80) {
            throw new CourseAuthorizationUnavailableException("CRS authorization request id is invalid");
        }
        try {
            String path = authorizationUri.replace("{courseId}", encode(courseId)).replace("{userId}", encode(userId));
            String separator = path.contains("?") ? "&" : "?";
            HttpRequest request = HttpRequest.newBuilder(URI.create(path + separator + "action=MANAGE"))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", requestId)
                    .header("X-OnlineJudge-Service-Authorization", serviceAuthorization)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new CourseAuthorizationUnavailableException("CRS authorization returned HTTP " + response.statusCode());
            }
            JsonNode decision = mapper.readTree(response.body());
            if (decision == null || !decision.has("allowed") || !decision.has("courseId")
                    || !decision.has("userId") || !decision.has("action") || !decision.has("memberVersion")
                    || !decision.path("allowed").isBoolean() || !decision.path("courseId").isTextual()
                    || !decision.path("userId").isTextual() || !decision.path("action").isTextual()
                    || !decision.path("memberVersion").isIntegralNumber()) {
                throw new CourseAuthorizationUnavailableException("CRS authorization response is malformed");
            }
            if (!courseId.equals(decision.path("courseId").asText())
                    || !userId.equals(decision.path("userId").asText())
                    || !"MANAGE".equals(decision.path("action").asText())
                    || decision.path("memberVersion").asLong(0) < 1) {
                return false;
            }
            return decision.path("allowed").asBoolean(false);
        } catch (CourseAuthorizationUnavailableException unavailable) {
            throw unavailable;
        } catch (Exception unavailable) {
            throw new CourseAuthorizationUnavailableException("CRS authorization is unavailable", unavailable);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
