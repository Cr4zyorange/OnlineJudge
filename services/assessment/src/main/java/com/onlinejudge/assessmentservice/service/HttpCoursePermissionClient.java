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

/** Calls the canonical Course v2 authorization endpoint and fails closed. */
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
        if (authorizationUri.isBlank() || serviceAuthorization.isBlank() || courseId == null || userId == null) return false;
        try {
            String path = authorizationUri.replace("{courseId}", encode(courseId)).replace("{userId}", encode(userId));
            String separator = path.contains("?") ? "&" : "?";
            HttpRequest request = HttpRequest.newBuilder(URI.create(path + separator + "action=MANAGE"))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .header("X-OnlineJudge-Service-Authorization", serviceAuthorization)
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            JsonNode decision = mapper.readTree(response.body());
            return decision.path("allowed").asBoolean(false)
                    && courseId.equals(decision.path("courseId").asText())
                    && userId.equals(decision.path("userId").asText())
                    && "MANAGE".equals(decision.path("action").asText())
                    && decision.path("memberVersion").asLong(0) >= 1;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
